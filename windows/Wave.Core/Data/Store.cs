using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Wave.Data;

/**
 * Where Wave keeps your things: %APPDATA%\Wave on Windows, the same folder the earlier Wave for
 * Windows used, so your sign-in, likes and queue carry over.
 */
public static class Paths
{
    private static string? root;

    public static string Data
    {
        get
        {
            if (root is not null) return root;
            var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            if (string.IsNullOrEmpty(appData)) appData = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".config");
            root = Path.Combine(appData, "Wave");
            Directory.CreateDirectory(root);
            return root;
        }
        set
        {
            root = value;
            Directory.CreateDirectory(value);
        }
    }

    public static string File(string name) => Path.Combine(Data, name);

    /** Re-creatable files (cover images). */
    public static string Cache
    {
        get
        {
            var local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            var dir = Path.Combine(string.IsNullOrEmpty(local) ? Data : local, "Wave", "Cache");
            Directory.CreateDirectory(dir);
            return dir;
        }
    }
}

/** Small JSON files, written atomically (a crash mid-write never leaves half a file). */
public static class JsonFile
{
    public static readonly JsonSerializerOptions Options = new()
    {
        WriteIndented = false,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    public static T? Read<T>(string name)
    {
        try
        {
            var path = Paths.File(name);
            return System.IO.File.Exists(path) ? JsonSerializer.Deserialize<T>(System.IO.File.ReadAllText(path), Options) : default;
        }
        catch (Exception)
        {
            return default;
        }
    }

    public static void Write<T>(string name, T value)
    {
        var path = Paths.File(name);
        var temp = path + ".tmp";
        System.IO.File.WriteAllText(temp, JsonSerializer.Serialize(value, Options));
        System.IO.File.Move(temp, path, overwrite: true);
    }

    public static void Delete(string name)
    {
        try { System.IO.File.Delete(Paths.File(name)); } catch (Exception) { }
    }
}

/**
 * The sign-in cookie is encrypted with a random key kept in Wave's own folder (session.key, the
 * same key and format the earlier Wave for Windows used), so it's never stored as plain text.
 */
public static class SecretBox
{
    private static byte[] Key()
    {
        var path = Paths.File("session.key");
        if (System.IO.File.Exists(path))
        {
            var existing = System.IO.File.ReadAllBytes(path);
            if (existing.Length == 32) return existing;
        }
        var key = RandomNumberGenerator.GetBytes(32);
        System.IO.File.WriteAllBytes(path, key);
        return key;
    }

    public static string Encrypt(string plain)
    {
        var iv = RandomNumberGenerator.GetBytes(12);
        var data = Encoding.UTF8.GetBytes(plain);
        var sealedBytes = new byte[data.Length];
        var tag = new byte[16];
        using (var aes = new AesGcm(Key(), 16)) aes.Encrypt(iv, data, sealedBytes, tag);
        // iv + ciphertext + tag: what Java's AES/GCM writes, so both read each other's.
        return Convert.ToBase64String([.. iv, .. sealedBytes, .. tag]);
    }

    public static string? Decrypt(string encoded)
    {
        try
        {
            var all = Convert.FromBase64String(encoded);
            if (all.Length < 12 + 16) return null;
            var iv = all.AsSpan(0, 12);
            var cipher = all.AsSpan(12, all.Length - 12 - 16);
            var tag = all.AsSpan(all.Length - 16);
            var plain = new byte[cipher.Length];
            using var aes = new AesGcm(Key(), 16);
            aes.Decrypt(iv, cipher, tag, plain);
            return Encoding.UTF8.GetString(plain);
        }
        catch (Exception)
        {
            return null;
        }
    }
}

/**
 * Reads the earlier Wave's saved preferences (AndroidX DataStore files, a small protobuf map),
 * once, to carry settings and sign-in over.
 */
public static class OldPreferences
{
    public static Dictionary<string, object> Read(string name)
    {
        var map = new Dictionary<string, object>();
        try
        {
            var path = Path.Combine(Paths.Data, "datastore", name + ".preferences_pb");
            if (!System.IO.File.Exists(path)) return map;
            var bytes = System.IO.File.ReadAllBytes(path);
            var at = 0;
            while (at < bytes.Length)
            {
                var (field, wire) = Tag(bytes, ref at);
                if (field == 1 && wire == 2)
                {
                    var entry = Slice(bytes, ref at);
                    var (key, value) = Entry(entry);
                    if (key is not null && value is not null) map[key] = value;
                }
                else Skip(bytes, ref at, wire);
            }
        }
        catch (Exception)
        {
            // A damaged file just means starting fresh.
        }
        return map;
    }

    private static (string? key, object? value) Entry(byte[] b)
    {
        string? key = null;
        object? value = null;
        var at = 0;
        while (at < b.Length)
        {
            var (field, wire) = Tag(b, ref at);
            if (field == 1 && wire == 2) key = Encoding.UTF8.GetString(Slice(b, ref at));
            else if (field == 2 && wire == 2) value = Value(Slice(b, ref at));
            else Skip(b, ref at, wire);
        }
        return (key, value);
    }

    private static object? Value(byte[] b)
    {
        var at = 0;
        while (at < b.Length)
        {
            var (field, wire) = Tag(b, ref at);
            switch (field)
            {
                case 1: return Varint(b, ref at) != 0;
                case 2: var f = BitConverter.ToSingle(b, at); at += 4; return f;
                case 3: return (int)Varint(b, ref at);
                case 4: return (long)Varint(b, ref at);
                case 5: return Encoding.UTF8.GetString(Slice(b, ref at));
                case 7: var d = BitConverter.ToDouble(b, at); at += 8; return d;
                default: Skip(b, ref at, wire); break;
            }
        }
        return null;
    }

    private static (int field, int wire) Tag(byte[] b, ref int at)
    {
        var tag = Varint(b, ref at);
        return ((int)(tag >> 3), (int)(tag & 7));
    }

    private static ulong Varint(byte[] b, ref int at)
    {
        ulong result = 0;
        for (var shift = 0; shift < 64; shift += 7)
        {
            var x = b[at++];
            result |= (ulong)(x & 0x7F) << shift;
            if ((x & 0x80) == 0) break;
        }
        return result;
    }

    private static byte[] Slice(byte[] b, ref int at)
    {
        var length = (int)Varint(b, ref at);
        var slice = b.AsSpan(at, length).ToArray();
        at += length;
        return slice;
    }

    private static void Skip(byte[] b, ref int at, int wire)
    {
        switch (wire)
        {
            case 0: Varint(b, ref at); break;
            case 1: at += 8; break;
            case 2: Slice(b, ref at); break;
            case 5: at += 4; break;
            default: at = b.Length; break;
        }
    }
}

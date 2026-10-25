package app.wave.music.data

import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Computers: the sign-in cookie is encrypted with a random key in Wave's own data folder,
 * readable only by your user account, so the saved session is never plain text.
 */
actual object SecretBox {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val key: SecretKeySpec by lazy {
        val file = Stores.file("session.key")
        val bytes = file.takeIf { it.length() == 32L }?.readBytes() ?: ByteArray(32).also {
            SecureRandom().nextBytes(it)
            file.parentFile?.mkdirs()
            file.writeBytes(it)
            restrict(file)
        }
        SecretKeySpec(bytes, "AES")
    }

    private fun restrict(file: File) {
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
    }

    actual fun encrypt(plain: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(plain.toByteArray()))
    }

    actual fun decrypt(encoded: String): String? = runCatching {
        val sealed = Base64.getDecoder().decode(encoded)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12)) }
        String(cipher.doFinal(sealed, 12, sealed.size - 12))
    }.getOrNull()
}

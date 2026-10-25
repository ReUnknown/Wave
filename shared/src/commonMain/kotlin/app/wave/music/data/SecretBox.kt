package app.wave.music.data

/** Encrypts small secrets (the YouTube sign-in cookie) with a key kept by the platform. */
expect object SecretBox {
    fun encrypt(plain: String): String
    fun decrypt(encoded: String): String?
}

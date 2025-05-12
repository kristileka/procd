package tech.procd.customer.account

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import kotlin.experimental.and

private object TokenGenerator {
    private const val RANDOM_BYTES_LENGTH = 64

    private val messageDigest = MessageDigest.getInstance("SHA-1")
    private val secureRandom = SecureRandom()

    fun generate(): String {
        val randomBytes = ByteArray(RANDOM_BYTES_LENGTH)

        secureRandom.nextBytes(randomBytes)

        return randomBytes.sha1()
    }

    private fun ByteArray.sha1() = messageDigest.digest(this).toHex()
    private fun ByteArray.toHex() = joinToString(separator = "") { byte ->
        "%02x".format(byte and 0xFF.toByte())
    }
}

package tech.procd.customer.account

import org.mindrot.jbcrypt.BCrypt

interface PasswordEncoder {
    fun encode(password: String): String
    fun check(plain: String, hashed: String): Boolean
}

object BCryptPasswordEncoder : PasswordEncoder {
    private const val ROUNDS = 10

    override fun encode(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt(ROUNDS))
    override fun check(plain: String, hashed: String): Boolean = BCrypt.checkpw(plain, hashed)
}

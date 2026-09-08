package com.mdmesh.agent.policy

import java.security.MessageDigest

object ProtectionPin {
    private const val SALT = "mdmesh-uninstall-v1"

    fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$SALT:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isValidFormat(pin: String): Boolean = pin.length in 4..8 && pin.all { it.isDigit() }

    fun matches(pin: String, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank()) return false
        return hash(pin) == storedHash
    }
}

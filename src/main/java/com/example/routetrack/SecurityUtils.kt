package com.example.routetrack

import android.util.Base64
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SecurityUtils {
    /**
     * Hashes the password using PBKDF2 with HMAC-SHA256.
     * This is significantly more secure than plain SHA-256.
     */
    fun hashPassword(password: String): String {
        val salt = "RouteTrack_Salt_2024".toByteArray() // In production, use a unique salt per user
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
}

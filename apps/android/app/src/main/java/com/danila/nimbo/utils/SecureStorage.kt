package com.danila.nimbo.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureStorage {
    private const val PREFS_NAME = "secure_storage"
    private const val KEY_ALIAS = "remnawave_key"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    // ВРЕМЕННО: Токен хранится в зашифрованном виде (Base64)
    // В продакшене используйте Android Keystore
    private const val ENCRYPTED_TOKEN = "" // Вставьте зашифрованный токен здесь

    // ВРЕМЕННО: API URL
    private const val API_BASE_URL = "" // Вставьте URL вашей панели Remnawave

    fun getRemnawaveToken(): String? {
        return if (ENCRYPTED_TOKEN.isNotEmpty()) {
            // Здесь должна быть дешифровка
            ENCRYPTED_TOKEN
        } else null
    }

    fun getRemnawaveApiUrl(): String? {
        return if (API_BASE_URL.isNotEmpty()) {
            API_BASE_URL
        } else null
    }

    // Для первоначальной настройки
    fun saveToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encrypted = encrypt(token, context)
        prefs.edit().putString(KEY_ALIAS, encrypted).apply()
    }

    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(KEY_ALIAS, null) ?: return null
        return decrypt(encrypted, context)
    }

    private fun encrypt(data: String, context: Context): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = getOrCreateKey(context)
        val iv = ByteArray(GCM_IV_LENGTH).apply { SecureRandom().nextBytes(this) }
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val encrypted = cipher.doFinal(data.toByteArray())
        return Base64.encodeToString(iv + encrypted, Base64.DEFAULT)
    }

    private fun decrypt(data: String, context: Context): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = getOrCreateKey(context)
        val decoded = Base64.decode(data, Base64.DEFAULT)
        val iv = decoded.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = decoded.copyOfRange(GCM_IV_LENGTH, decoded.size)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return String(cipher.doFinal(encrypted))
    }

    private fun getOrCreateKey(context: Context): SecretKey {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encodedKey = prefs.getString("aes_key", null)

        return if (encodedKey != null) {
            val keyBytes = Base64.decode(encodedKey, Base64.DEFAULT)
            javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        } else {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            val key = keyGen.generateKey()
            prefs.edit().putString("aes_key", Base64.encodeToString(key.encoded, Base64.DEFAULT)).apply()
            key
        }
    }
}

package com.danila.nimbo.sync

import com.google.gson.JsonParser
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import com.google.gson.JsonObject

internal class CloudFailure(val reason: String) : IllegalStateException(reason)

/** Portable encrypted envelope. No plaintext fallback and no credentials in server logs. */
internal object CloudCipher {
    const val MAX_BYTES = 16 * 1024 * 1024
    private const val FORMAT = "nimbo-cloud-encrypted-v1"
    private val aad = FORMAT.toByteArray(Charsets.UTF_8)
    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        require(password.size >= 8)
        val spec = PBEKeySpec(password, salt, 210000, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") }
        finally { spec.clearPassword() }
    }
    fun encrypt(source: String, password: CharArray): String {
        require(source.toByteArray().size <= MAX_BYTES / 2)
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)); cipher.updateAAD(aad)
        fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
        return JsonObject().apply {
            addProperty("format", FORMAT); addProperty("salt", encode(salt)); addProperty("iv", encode(iv))
            addProperty("data", encode(cipher.doFinal(source.toByteArray(Charsets.UTF_8))))
        }.toString()
    }
    fun decrypt(source: String, password: CharArray): String {
        require(source.toByteArray().size <= MAX_BYTES)
        val document = JsonParser.parseString(source).asJsonObject
        require(document["format"]?.asString == FORMAT)
        val salt = Base64.getDecoder().decode(document["salt"].asString)
        val iv = Base64.getDecoder().decode(document["iv"].asString)
        require(salt.size == 16 && iv.size == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)); cipher.updateAAD(aad)
        return cipher.doFinal(Base64.getDecoder().decode(document["data"].asString)).toString(Charsets.UTF_8)
    }
}

internal data class CloudSettings(val url: String, val user: String, val password: String, val encryptionPassword: String)
internal data class CloudDownload(val source: String, val etag: String)

/** File-level WebDAV. Redirects are disabled; conditional PUT prevents lost updates. */
internal class CloudWebDav(private val client: OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false).followSslRedirects(false).callTimeout(30, TimeUnit.SECONDS).build()) {
    private fun request(settings: CloudSettings): Request.Builder {
        val url = settings.url.trim().toHttpUrl()
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null)
        return Request.Builder().url(url).header("Authorization", Credentials.basic(settings.user, settings.password, Charsets.UTF_8))
            .header("Cache-Control", "no-store")
    }
    fun download(settings: CloudSettings): CloudDownload {
        client.newCall(request(settings).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw CloudFailure(if (response.code == 404) "NOT_FOUND" else "DOWNLOAD_FAILED")
            val etag = response.header("ETag")?.takeIf { it.startsWith('"') && it.endsWith('"') }
                ?: throw CloudFailure("ETAG_REQUIRED")
            val body = response.body ?: throw CloudFailure("DOWNLOAD_FAILED")
            if (body.contentLength() > CloudCipher.MAX_BYTES) throw CloudFailure("TOO_LARGE")
            val bytes = body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    if (output.size() + count > CloudCipher.MAX_BYTES) throw CloudFailure("TOO_LARGE")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val plaintext = try { CloudCipher.decrypt(bytes.toString(Charsets.UTF_8), settings.encryptionPassword.toCharArray()) }
            catch (_: Exception) { throw CloudFailure("DECRYPT_FAILED") }
            return CloudDownload(plaintext, etag)
        }
    }
    fun upload(settings: CloudSettings, plaintext: String, knownEtag: String?): String? {
        val body = CloudCipher.encrypt(plaintext, settings.encryptionPassword.toCharArray()).toRequestBody("application/json".toMediaType())
        val builder = request(settings).put(body)
        if (knownEtag == null) builder.header("If-None-Match", "*") else builder.header("If-Match", knownEtag)
        client.newCall(builder.build()).execute().use { response ->
            if (response.code in setOf(409, 412)) throw CloudFailure("CONFLICT")
            if (!response.isSuccessful) throw CloudFailure("UPLOAD_FAILED")
            return response.header("ETag")?.takeIf { it.startsWith('"') && it.endsWith('"') }
        }
    }
}

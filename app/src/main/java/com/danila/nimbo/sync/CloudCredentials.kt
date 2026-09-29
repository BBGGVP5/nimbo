package com.danila.nimbo.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.Gson
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Separate from exported app preferences and Android backup. */
internal class CloudCredentials(private val context: Context) {
    private val file get() = context.noBackupFilesDir.resolve("cloud-credentials.bin")
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("nimbo-cloud-credentials-v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("nimbo-cloud-credentials-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): CloudSettings? = runCatching {
        if (!file.isFile) return null
        val bytes = file.readBytes(); require(bytes.size in 28..65536)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        Gson().fromJson(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8), CloudSettings::class.java)
    }.getOrNull()
    @Synchronized fun save(settings: CloudSettings) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(Gson().toJson(settings).toByteArray())
        val destination = android.util.AtomicFile(file)
        val output = destination.startWrite()
        try { output.write(bytes); destination.finishWrite(output) } catch (e: Exception) { destination.failWrite(output); throw e }
    }
    private val revision get() = context.noBackupFilesDir.resolve("cloud-revision.json")
    fun readRevision(url: String): String? = runCatching {
        val value = com.google.gson.JsonParser.parseString(revision.readText()).asJsonObject
        if (value["source"]?.asString != sourceId(url)) return null
        value["etag"]?.asString
    }.getOrNull()
    fun saveRevision(url: String, etag: String?) {
        if (etag == null) { revision.delete(); return }
        val value = com.google.gson.JsonObject().apply { addProperty("source", sourceId(url)); addProperty("etag", etag) }
        val destination = android.util.AtomicFile(revision); val output = destination.startWrite()
        try { output.write(value.toString().toByteArray()); destination.finishWrite(output) }
        catch (e: Exception) { destination.failWrite(output); throw e }
    }
    private fun sourceId(url: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
    fun clear() { file.delete(); revision.delete() }
}

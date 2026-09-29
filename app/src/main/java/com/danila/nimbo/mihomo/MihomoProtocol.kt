package com.danila.nimbo.mihomo

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.util.UUID

/** Only safe codes are surfaced: upstream YAML errors may contain credentials. */
class MihomoException(val code: String) : IllegalStateException("Mihomo: $code")

data class MihomoReply(val data: JsonObject, val generation: Long)

object MihomoProtocol {
    const val MAX_SOURCE_BYTES = 4 * 1024 * 1024
    private val codePattern = Regex("[A-Z][A-Z0-9_]{0,79}")
    private val counterPattern = Regex("0|[1-9][0-9]{0,18}")
    private val extraFields = setOf("options", "group", "name", "url", "timeoutMs", "expectedStatus", "targetRequestId")

    fun request(operation: String, yaml: String? = null, generation: Long? = null,
                fields: JsonObject? = null, requestId: String = UUID.randomUUID().toString()): JsonObject {
        require(requestId.isNotBlank())
        yaml?.let(::checkSource)
        require(generation == null || generation >= 0)
        return JsonObject().apply {
            addProperty("apiVersion", 1)
            addProperty("requestId", requestId)
            addProperty("operation", operation)
            yaml?.let { addProperty("yaml", it) }
            generation?.let { addProperty("generation", it) }
            fields?.entrySet()?.forEach { (key, value) ->
                require(key in extraFields) { "Invalid Mihomo request field" }
                add(key, value.deepCopy())
            }
        }
    }

    fun decode(raw: String, requestId: String?): MihomoReply {
        try {
            if (raw.length > 16 * 1024 * 1024) throw MihomoException("INVALID_RESPONSE")
            val obj = JsonParser.parseString(raw).asJsonObject
            require(obj["apiVersion"]?.toString() == "1")
            if (requestId != null) require(obj["requestId"]?.asString == requestId)
            val generation = obj["generation"]?.toString() ?: if (requestId == null) "0" else error("Missing generation")
            require(counterPattern.matches(generation))
            val success = obj["success"]?.toString()
            require(success == "true" || success == "false")
            if (success != "true") {
                val code = obj.getAsJsonObject("error")?.get("code")?.asString.orEmpty()
                throw MihomoException(code.takeIf(codePattern::matches) ?: "NATIVE_ERROR")
            }
            return MihomoReply(obj.getAsJsonObject("data") ?: JsonObject(), generation.toLong())
        } catch (error: MihomoException) {
            throw error
        } catch (_: Exception) {
            throw MihomoException("INVALID_RESPONSE")
        }
    }

    fun checkSource(yaml: String) {
        if (yaml.isBlank() || yaml.toByteArray(Charsets.UTF_8).size > MAX_SOURCE_BYTES ||
            !Charsets.UTF_8.newEncoder().canEncode(yaml)) throw MihomoException("INVALID_YAML_SIZE_OR_ENCODING")
    }

    fun sourceHash(yaml: String): String = MessageDigest.getInstance("SHA-256")
        .digest(yaml.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

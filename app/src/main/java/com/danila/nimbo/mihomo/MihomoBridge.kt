package com.danila.nimbo.mihomo

import com.google.gson.JsonObject
import libXray.DialerController
import java.lang.reflect.InvocationTargetException
import java.util.UUID

/** All Mihomo exports live in the SAME gomobile runtime as LibXray. */
object MihomoBridge {
    @Volatile var latestGeneration: Long = 0
        private set

    private fun invokeNative(name: String, types: Array<Class<*>>, vararg arguments: Any?): String = try {
        Class.forName("libXray.LibXray").getMethod(name, *types).invoke(null, *arguments) as String
    } catch (_: InvocationTargetException) {
        throw MihomoException("NATIVE_CALL_FAILED")
    } catch (_: LinkageError) {
        throw MihomoException("NATIVE_UNAVAILABLE")
    } catch (_: ReflectiveOperationException) {
        throw MihomoException("NATIVE_UNAVAILABLE")
    }

    private fun decode(raw: String, id: String?): MihomoReply = MihomoProtocol.decode(raw, id).also {
        synchronized(this) { latestGeneration = maxOf(latestGeneration, it.generation) }
    }

    fun available(): Boolean = runCatching {
        capabilities()["androidVpn"]?.asBoolean == true && androidTunPlan()["compiled"]?.asBoolean == true
    }.getOrDefault(false)

    fun capabilities(): JsonObject = decode(
        invokeNative("nimboMihomoInvoke", arrayOf(String::class.java),
            MihomoProtocol.request("capabilities").toString()), null
    ).data

    fun androidTunPlan(): JsonObject = decode(
        invokeNative("nimboMihomoAndroidTunPlan", emptyArray()), null
    ).data

    fun response(operation: String, yaml: String? = null, generation: Long? = null,
                 fields: JsonObject? = null): MihomoReply {
        val request = MihomoProtocol.request(operation, yaml, generation, fields)
        return decode(invokeNative("nimboMihomoInvoke", arrayOf(String::class.java), request.toString()),
            request["requestId"].asString)
    }

    /** Preserves the caller's request ID so a pending standalone probe can be cancelled. */
    internal fun rawResponse(request: JsonObject): MihomoReply = decode(
        invokeNative("nimboMihomoInvoke", arrayOf(String::class.java), request.toString()),
        request["requestId"].asString
    )

    fun call(operation: String, yaml: String? = null, generation: Long? = null,
             fields: JsonObject? = null): JsonObject = response(operation, yaml, generation, fields).data

    fun inspect(yaml: String): JsonObject = call("inspect", yaml).also {
        if (it["originalYAML"]?.asString != yaml ||
            it["sourceSHA256"]?.asString != MihomoProtocol.sourceHash(yaml)) throw MihomoException("SOURCE_MISMATCH")
    }

    fun preflight(yaml: String): JsonObject = call("preflightAndroid", yaml).also {
        if (it["valid"]?.asBoolean != true ||
            it["sourceSHA256"]?.asString != MihomoProtocol.sourceHash(yaml)) throw MihomoException("SOURCE_MISMATCH")
    }

    internal fun androidStartFields(dataDir: String, ipv6: Boolean, physicalDns: List<String>, adBlocking: Boolean = false): JsonObject =
        JsonObject().apply {
            add("options", JsonObject().apply {
                addProperty("networkOwner", "android-vpn")
                addProperty("dataDir", dataDir)
                addProperty("startupTimeoutMs", 20000)
                addProperty("androidIPv6", ipv6)
                // Old native binaries reject unknown fields; default-off remains compatible.
                if (adBlocking) addProperty("adBlocking", true)
                add("androidSystemDNS", com.google.gson.JsonArray().apply { physicalDns.forEach { add(it) } })
            })
        }
    fun startAndroid(yaml: String, dataDir: String, fd: Long, requestId: String, ipv6: Boolean, physicalDns: List<String> = emptyList(), adBlocking: Boolean = false): JsonObject {
        val request = MihomoProtocol.request("start", yaml,
            fields = androidStartFields(dataDir, ipv6, physicalDns, adBlocking), requestId = requestId)
        return decode(invokeNative("nimboMihomoStartAndroid", arrayOf(String::class.java, java.lang.Long.TYPE),
            request.toString(), fd), requestId).data
    }

    fun cancel(requestId: String) {
        call("cancel", fields = JsonObject().apply { addProperty("targetRequestId", requestId) })
    }

    fun stop(generation: Long? = null) { call("stop", generation = generation) }

    private var flowOwnerProxy: Any? = null

    fun setFlowOwnerResolver(resolver: ((String, String, Long, String, Long) -> String)?) {
        val type = Class.forName("libXray.NimboMihomoFlowOwner")
        val proxy = resolver?.let { callback ->
            java.lang.reflect.Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { instance, method, args ->
                when (method.name) {
                    "resolve" -> callback(args!![0] as String, args[1] as String, (args[2] as Number).toLong(), args[3] as String, (args[4] as Number).toLong())
                    "toString" -> "NimboFlowOwner"
                    "hashCode" -> System.identityHashCode(instance)
                    "equals" -> instance === args?.get(0)
                    else -> null
                }
            }
        }
        decode(invokeNative("nimboMihomoSetFlowOwnerResolver", arrayOf(type), proxy), null)
        flowOwnerProxy = proxy
    }

    fun setSocketProtector(protector: DialerController?) {
        decode(invokeNative("nimboMihomoSetSocketProtector", arrayOf(DialerController::class.java), protector), null)
    }
}

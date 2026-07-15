package com.danila.nimbo.vpn

import org.json.JSONObject

/**
 * JSON envelope for libXray 26.7.11+.
 *
 * The library exposes one native [LibXray.invoke] entry point. Keeping the
 * protocol in one small, testable object avoids coupling application code to
 * generated Go bindings that may change between core releases.
 */
object XrayCoreProtocol {
    private const val API_VERSION = 1

    fun runXrayFromJson(configJson: String): String = request(
        method = "runXrayFromJson",
        payload = JSONObject().put("configJSON", configJson)
    )

    fun stopXray(): String = request(method = "stopXray", payload = JSONObject())

    fun convertShareLinksToXrayJson(links: String): String = request(
        method = "convertShareLinksToXrayJson",
        payload = JSONObject().put("text", links)
    )

    /**
     * libXray no longer accepts the TUN fd and asset directory as API
     * arguments. Xray-core reads both values from its root `env` object.
     */
    fun withAndroidRuntimeEnv(configJson: String, assetDirectory: String, tunFd: Int): String {
        val config = JSONObject(configJson)
        val environment = config.optJSONObject("env") ?: JSONObject().also {
            config.put("env", it)
        }
        environment.put("xray.location.asset", assetDirectory)
        environment.put("xray.tun.fd", tunFd.toString())
        return config.toString()
    }

    private fun request(method: String, payload: JSONObject): String = JSONObject()
        .put("apiVersion", API_VERSION)
        .put("method", method)
        .put("payload", payload)
        .toString()
}

package com.danila.nimbo.awg

import android.util.Log

/**
 * JNI-мост к нативному движку AmneziaWG (libwg-go.so), собранному из
 * github.com/amnezia-vpn/amneziawg-go/v3. Имена методов и сигнатуры
 * должны совпадать с экспортами из app/src/main/cpp/jni.c.
 */
object AmneziaWgLibrary {

    private const val TAG = "AmneziaWgLibrary"

    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            System.loadLibrary("wg-go")
            loaded = true
            Log.d(TAG, "Loaded native library: wg-go (${awgVersion()})")
        }
    }

    @JvmStatic
    external fun awgTurnOn(interfaceName: String, tunFd: Int, settings: String): Int

    @JvmStatic
    external fun awgTurnOff(handle: Int)

    @JvmStatic
    external fun awgGetSocketV4(handle: Int): Int

    @JvmStatic
    external fun awgGetSocketV6(handle: Int): Int

    @JvmStatic
    external fun awgGetConfig(handle: Int): String?

    @JvmStatic
    external fun awgVersion(): String
}

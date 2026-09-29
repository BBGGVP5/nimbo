package com.danila.nimbo.network

import android.annotation.SuppressLint
import android.net.DnsResolver
import android.os.Build
import android.util.Log
import okhttp3.Dns
import okhttp3.android.AndroidDns
import java.net.InetAddress

/**
 * Резолвер, который прячет имя сайта на этапе рукопожатия TLS.
 *
 * Обычный HTTPS шифрует содержимое, но имя сервера в первом же пакете уходит
 * открытым текстом — по нему провайдер и видит, куда мы ходим. Encrypted
 * Client Hello закрывает и его, но ключ для этого лежит в HTTPS-записи DNS, а
 * штатный `Dns.SYSTEM` такие записи не запрашивает: он умеет отдавать только
 * адреса. Поэтому здесь резолвер Android, у которого запрошены и метаданные.
 *
 * Само шифрование включает система: сокет с поддержкой ECH появился в Android
 * 17. Ниже этой версии запрашивать метаданные незачем — ключ всё равно некому
 * применить, а лишний запрос DNS удлиняет каждое соединение.
 *
 * Падать из-за приватности нельзя: если системный резолвер отказал, запрос
 * повторяется обычным путём. Иначе выключенный или необычный DNS в сети
 * оставил бы приложение без подписки.
 */
object NimboDns {

    private const val TAG = "NimboDns"

    /** Android 17: первая версия, где сокет умеет Encrypted Client Hello. */
    private const val ANDROID_17 = 37

    val supportsEncryptedClientHello: Boolean
        get() = Build.VERSION.SDK_INT >= ANDROID_17

    /**
     * Резолвер для запросов самого приложения: подписка, панель, обновления.
     * На старых версиях — системный, чтобы ничего не менять на пустом месте.
     */
    val privacyAware: Dns by lazy {
        if (!supportsEncryptedClientHello) {
            Dns.SYSTEM
        } else {
            runCatching { FallbackDns(androidDns()) }
                .onFailure { Log.w(TAG, "Системный резолвер недоступен, остаёмся на обычном", it) }
                .getOrDefault(Dns.SYSTEM)
        }
    }

    @SuppressLint("NewApi")
    private fun androidDns(): Dns = AndroidDns(
        dnsResolver = DnsResolver.getInstance(),
        // Ключевой параметр: без метаданных резолвер отдаёт только адреса, а
        // ключ ECH лежит именно в HTTPS-записи.
        includeServiceMetadata = true
    )

    /**
     * Обёртка со страховкой: сеть важнее приватности, поэтому отказ основного
     * резолвера не должен оборачиваться ошибкой запроса.
     */
    private class FallbackDns(private val primary: Dns) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                primary.lookup(hostname).ifEmpty { Dns.SYSTEM.lookup(hostname) }
            } catch (t: Throwable) {
                Log.d(TAG, "Резолвер с метаданными не ответил по $hostname, пробуем системный")
                Dns.SYSTEM.lookup(hostname)
            }
        }

        override fun newCall(request: Dns.Request): Dns.Call = primary.newCall(request)
    }
}

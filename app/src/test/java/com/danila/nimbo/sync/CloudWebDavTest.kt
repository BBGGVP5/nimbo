package com.danila.nimbo.sync

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class CloudWebDavTest {
    private val settings = CloudSettings("https://cloud.example.invalid/nimbo.json", "user", "token", "encryption password")
    private fun client(action: (Request) -> Response) = CloudWebDav(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .addInterceptor { chain -> action(chain.request()) }.build())
    private fun reply(request: Request, code: Int, body: String = "", etag: String? = "\"revision\"") = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("test").body(body.toResponseBody())
        .apply { etag?.let { header("ETag", it) } }.build()
    @Test fun firstUploadCannotOverwriteAndBodyIsEncrypted() {
        val transport = client { request ->
            assertEquals("*", request.header("If-None-Match")); assertNull(request.header("If-Match"))
            assertTrue(request.header("Authorization")!!.startsWith("Basic "))
            val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
            assertEquals("private data", CloudCipher.decrypt(buffer.readUtf8(), settings.encryptionPassword.toCharArray()))
            reply(request, 201)
        }
        assertEquals("\"revision\"", transport.upload(settings, "private data", null))
    }
    @Test fun conditionalUpdateAndConflictAreExplicit() {
        val transport = client { request -> assertEquals("\"old\"", request.header("If-Match")); reply(request, 412) }
        val error = assertThrows(CloudFailure::class.java) { transport.upload(settings, "{}", "\"old\"") }
        assertEquals("CONFLICT", error.reason)
    }
    @Test fun downloadChecksEncryptionAndStrongEtag() {
        val source = CloudCipher.encrypt("{}", settings.encryptionPassword.toCharArray())
        assertEquals("{}", client { reply(it, 200, source) }.download(settings).source)
        assertThrows(CloudFailure::class.java) { client { reply(it, 200, source, "W/\"weak\"") }.download(settings) }
    }
    @Test fun redirectsAndHttpCannotReceiveCredentials() {
        var requests = 0
        val transport = client { requests++; reply(it, 302) }
        assertThrows(CloudFailure::class.java) { transport.download(settings) }; assertEquals(1, requests)
        assertThrows(Exception::class.java) { transport.download(settings.copy(url = "http://cloud.example.invalid/nimbo.json")) }
        assertEquals(1, requests)
    }
}

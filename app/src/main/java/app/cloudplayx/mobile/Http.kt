package app.cloudplayx.mobile

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Runs [request] and hands the successful response to [handle]. Retries network errors, HTTP 429 and 5xx. */
    fun <T> execute(request: Request, retries: Int = 3, handle: (Response) -> T): T {
        var attempt = 0
        while (true) {
            try {
                client.newCall(request).execute().use { resp ->
                    if (resp.code == 401) {
                        // A revoked/expired Drive token must not remain cached after the server rejects it.
                        if (request.header("Authorization")?.startsWith("Bearer ") == true) GoogleAuth.forget()
                        throw RemoteException("Not signed in, or the sign-in expired (HTTP 401).", needsSignIn = true)
                    }
                    val transient = resp.code == 429 || resp.code >= 500
                    if (!transient || attempt >= retries) {
                        if (!resp.isSuccessful) {
                            // Include the API's short error body so OAuth/scope/API-enable failures are diagnosable.
                            val detail = runCatching {
                                resp.body?.string()?.replace(Regex("\\s+"), " ")?.trim()?.take(420)
                            }.getOrNull().orEmpty()
                            val suffix = if (detail.isBlank()) "" else ": $detail"
                            throw RemoteException("HTTP ${resp.code} ${resp.message}$suffix")
                        }
                        return handle(resp)
                    }
                }
            } catch (e: RemoteException) {
                throw e
            } catch (e: IOException) {
                if (attempt >= retries) throw RemoteException("Network error: ${e.message}")
            }
            attempt++
            Thread.sleep(500L * (1L shl attempt))
        }
    }

    /** Reads [length] bytes of a (possibly range) response; if the server ignored the Range header it skips to [offset]. */
    fun readBody(resp: Response, offset: Long, length: Int): ByteArray {
        val body = resp.body ?: throw RemoteException("Empty response")
        val input = body.byteStream()
        if (resp.code == 200 && offset > 0) skipFully(input, offset)
        val buf = ByteArray(length)
        var n = 0
        while (n < length) {
            val r = input.read(buf, n, length - n)
            if (r < 0) break
            n += r
        }
        return if (n == length) buf else buf.copyOf(n)
    }

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        while (left > 0) {
            val s = input.skip(left)
            if (s <= 0) {
                if (input.read() < 0) return
                left--
            } else {
                left -= s
            }
        }
    }
}

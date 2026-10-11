package com.example.spotifyreplica.data.remote.source

import java.io.IOException
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response

/**
 * Bridges NewPipeExtractor's [Downloader] contract onto one of our OkHttp clients.
 *
 * The blank implementation would load Android's `HttpURLConnection`; OkHttp gives us transparent
 * redirects (`latestUrl`), timeouts, and sane cookie handling with the same client the rest of the
 * app already trusts. Kept in this file set precisely so the optional YouTube resolver never
 * depends on the retrofitted Spotify client.
 */
class OkHttpDownloader(
    private val client: OkHttpClient,
) : Downloader() {

    @Throws(IOException::class, org.schabi.newpipe.extractor.exceptions.ReCaptchaException::class)
    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val okRequest = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
            .headers(buildHeaders(request))
            .build()

        val okResponse = client.newCall(okRequest).execute()
        return try {
            val body = okResponse.body?.string() ?: ""
            // Positional on purpose: this is a Java constructor, named args are not allowed.
            Response(
                okResponse.code,
                okResponse.message,
                okResponse.headers.toMultimap(),
                body,
                okResponse.request.url.toString(),
            )
        } finally {
            okResponse.close()
        }
    }

    private fun buildHeaders(request: org.schabi.newpipe.extractor.downloader.Request): Headers {
        val builder = Headers.Builder().set(HEADER_USER_AGENT, USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeAll(name)
            values.forEach { value -> builder.add(name, value) }
        }
        return builder.build()
    }

    private companion object {
        const val HEADER_USER_AGENT = "User-Agent"
        // YouTube serves fallback/consent pages to clients with unknown agents.
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
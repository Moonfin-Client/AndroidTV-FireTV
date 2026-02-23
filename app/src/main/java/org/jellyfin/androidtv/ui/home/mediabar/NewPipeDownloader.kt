package org.jellyfin.androidtv.ui.home.mediabar

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

/** OkHttp-based [Downloader] implementation for NewPipe Extractor. */
class NewPipeDownloader private constructor(
	private val client: OkHttpClient,
) : Downloader() {

	companion object {
		private const val USER_AGENT =
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

		@Volatile
		private var instance: NewPipeDownloader? = null

		fun getInstance(): NewPipeDownloader {
			return instance ?: synchronized(this) {
				instance ?: NewPipeDownloader(
					OkHttpClient.Builder()
						.readTimeout(30, TimeUnit.SECONDS)
						.connectTimeout(15, TimeUnit.SECONDS)
						.build()
				).also { instance = it }
			}
		}
	}

	@Throws(IOException::class, ReCaptchaException::class)
	override fun execute(request: Request): Response {
		val httpMethod = request.httpMethod()
		val url = request.url()
		val headers = request.headers()
		val dataToSend = request.dataToSend()

		val requestBody = dataToSend?.toRequestBody()

		val requestBuilder = okhttp3.Request.Builder()
			.method(httpMethod, requestBody)
			.url(url)
			.addHeader("User-Agent", USER_AGENT)

		headers.forEach { (headerName, headerValueList) ->
			requestBuilder.removeHeader(headerName)
			headerValueList.forEach { headerValue ->
				requestBuilder.addHeader(headerName, headerValue)
			}
		}

		val response = client.newCall(requestBuilder.build()).execute()

		if (response.code == 429) {
			response.close()
			throw ReCaptchaException("reCaptcha Challenge requested", url)
		}

		val responseBody = response.body?.string()
		val latestUrl = response.request.url.toString()

		return Response(
			response.code,
			response.message,
			response.headers.toMultimap(),
			responseBody,
			latestUrl,
		)
	}
}

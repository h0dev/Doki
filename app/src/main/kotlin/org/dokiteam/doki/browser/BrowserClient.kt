package org.dokiteam.doki.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Looper
import android.webkit.HttpAuthHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.dokiteam.doki.core.network.proxy.ProxyProvider
import org.dokiteam.doki.core.network.webview.adblock.AdBlock
import org.dokiteam.doki.core.util.ext.printStackTraceDebug
import java.io.ByteArrayInputStream

open class BrowserClient(
	private val callback: BrowserCallback,
	private val adBlock: AdBlock?,
	private val proxyProvider: ProxyProvider? = null,
	private val okHttpClient: OkHttpClient? = null,
) : WebViewClient() {

	/**
	 * https://stackoverflow.com/questions/57414530/illegalstateexception-reasonphrase-cant-be-empty-with-android-webview
	 */

	override fun onPageFinished(webView: WebView, url: String) {
		super.onPageFinished(webView, url)
		callback.onLoadingStateChanged(isLoading = false)
	}

	override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
		super.onPageStarted(view, url, favicon)
		callback.onLoadingStateChanged(isLoading = true)
	}

	override fun onPageCommitVisible(view: WebView, url: String) {
		super.onPageCommitVisible(view, url)
		callback.onTitleChanged(view.title.orEmpty(), url)
	}

	override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
		super.doUpdateVisitedHistory(view, url, isReload)
		callback.onHistoryChanged()
	}

	override fun onReceivedHttpAuthRequest(
		view: WebView?,
		handler: HttpAuthHandler?,
		host: String?,
		realm: String?
	) {
		val credentials = proxyProvider?.getProxyCredentials()
		if (credentials != null && handler != null) {
			handler.proceed(credentials.first, credentials.second)
		} else {
			super.onReceivedHttpAuthRequest(view, handler, host, realm)
		}
	}

	@WorkerThread
	@Deprecated("Deprecated in Java")
	override fun shouldInterceptRequest(
		view: WebView?,
		url: String?
	): WebResourceResponse? {
		if (url.isNullOrEmpty()) {
			return super.shouldInterceptRequest(view, url)
		}
		if (adBlock?.shouldLoadUrl(url, view?.getUrlSafe()) == false) {
			return emptyResponse()
		}
		// Use OkHttp for proxy authentication if proxy credentials are configured
		if (shouldUseOkHttp()) {
			return interceptWithOkHttp(url, emptyMap())
		}
		return super.shouldInterceptRequest(view, url)
	}

	@WorkerThread
	override fun shouldInterceptRequest(
		view: WebView?,
		request: WebResourceRequest?
	): WebResourceResponse? {
		if (request == null) {
			return super.shouldInterceptRequest(view, request)
		}
		val url = request.url.toString()
		if (adBlock?.shouldLoadUrl(url, view?.getUrlSafe()) == false) {
			return emptyResponse()
		}
		// Use OkHttp for proxy authentication if proxy credentials are configured
		if (shouldUseOkHttp()) {
			val headers = request.requestHeaders ?: emptyMap()
			return interceptWithOkHttp(url, headers)
		}
		return super.shouldInterceptRequest(view, request)
	}

	private fun shouldUseOkHttp(): Boolean {
		// Only use OkHttp interception if we have both okHttpClient and proxy credentials
		return okHttpClient != null && proxyProvider?.getProxyCredentials() != null
	}

	@WorkerThread
	private fun interceptWithOkHttp(url: String, headers: Map<String, String>): WebResourceResponse? {
		// okHttpClient is guaranteed to be non-null here by shouldUseOkHttp() check
		val client = okHttpClient ?: return null
		
		return try {
			val requestBuilder = Request.Builder().url(url)
			headers.forEach { (key, value) ->
				requestBuilder.addHeader(key, value)
			}
			val request = requestBuilder.build()
			val response = client.newCall(request).execute()
			
			// Note: Response will be closed when WebView closes the input stream
			// Convert OkHttp response to WebResourceResponse
			val contentType = response.header("Content-Type") ?: "text/plain"
			val parts = contentType.split(";").map { it.trim() }
			val mimeType = parts.firstOrNull()?.takeIf { it.isNotEmpty() } ?: "text/plain"
			val encoding = parts.asSequence()
				.mapNotNull { part ->
					if (part.startsWith("charset=", ignoreCase = true)) {
						part.substringAfter("charset=", "").trim().takeIf { it.isNotEmpty() }
					} else null
				}
				.firstOrNull() ?: "UTF-8"
			
			val responseHeaders = response.headers.toMultimap().asSequence()
				.mapNotNull { (key, values) -> values.firstOrNull()?.let { key to it } }
				.toMap()
			
			// Use the original message or provide a default based on response code
			val reasonPhrase = response.message.ifEmpty {
				when (response.code) {
					in 200..299 -> "OK"
					in 300..399 -> "Redirect"
					in 400..499 -> "Client Error"
					in 500..599 -> "Server Error"
					else -> "Unknown"
				}
			}
			
			WebResourceResponse(
				mimeType,
				encoding,
				response.code,
				reasonPhrase,
				responseHeaders,
				response.body?.byteStream()
			)
		} catch (e: Exception) {
			e.printStackTraceDebug()
			// Fall back to default WebView handling on error
			null
		}
	}

	private fun emptyResponse(): WebResourceResponse =
		WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(byteArrayOf()))

	@SuppressLint("WrongThread")
	@AnyThread
	private fun WebView.getUrlSafe(): String? = if (Looper.myLooper() == Looper.getMainLooper()) {
		url
	} else {
		runBlocking(Dispatchers.Main.immediate) {
			url
		}
	}
}

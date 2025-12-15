package org.dokiteam.doki.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Looper
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

	@WorkerThread
	@Deprecated("Deprecated in Java")
	override fun shouldInterceptRequest(
		view: WebView?,
		url: String?
	): WebResourceResponse? {
		if (url.isNullOrEmpty()) {
			return super.shouldInterceptRequest(view, url)
		}
		
		// Check adblock
		if (!(adBlock?.shouldLoadUrl(url, view?.getUrlSafe()) ?: true)) {
			return emptyResponse()
		}
		
		// Intercept request for proxy authentication if needed
		if (proxyProvider?.needsWebViewRequestInterception() == true && okHttpClient != null) {
			return interceptRequestWithOkHttp(url, null)
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
		
		// Check adblock
		if (!(adBlock?.shouldLoadUrl(request.url.toString(), view?.getUrlSafe()) ?: true)) {
			return emptyResponse()
		}
		
		// Intercept request for proxy authentication if needed
		if (proxyProvider?.needsWebViewRequestInterception() == true && okHttpClient != null) {
			return interceptRequestWithOkHttp(request.url.toString(), request)
		}
		
		return super.shouldInterceptRequest(view, request)
	}

	/**
	 * Intercepts a WebView request and fetches it using OkHttp.
	 * This is necessary for HTTP proxy authentication since WebView doesn't support it natively.
	 */
	@WorkerThread
	private fun interceptRequestWithOkHttp(
		url: String,
		webRequest: WebResourceRequest?
	): WebResourceResponse? {
		return try {
			val requestBuilder = Request.Builder().url(url)
			
			// Copy headers from WebView request if available
			webRequest?.requestHeaders?.forEach { (key, value) ->
				requestBuilder.addHeader(key, value)
			}
			
			val request = requestBuilder.build()
			val response = okHttpClient!!.newCall(request).execute()
			
			// Convert OkHttp response to WebResourceResponse
			val mimeType = response.header("Content-Type")?.substringBefore(';')
			val encoding = response.header("Content-Type")
				?.substringAfter("charset=", "")
				?.takeIf { it.isNotEmpty() }
				?: "UTF-8"
			
			val headers = mutableMapOf<String, String>()
			response.headers.forEach { (name, value) ->
				headers[name] = value
			}
			
			WebResourceResponse(
				mimeType ?: "text/html",
				encoding,
				response.code,
				response.message.ifEmpty { "OK" },
				headers,
				response.body?.byteStream()
			)
		} catch (e: Exception) {
			e.printStackTraceDebug()
			// Fall back to default behavior on error
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

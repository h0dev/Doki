package org.dokiteam.doki.core.network.webview

import android.webkit.WebView
import android.webkit.WebViewClient
import org.dokiteam.doki.core.network.proxy.ProxyProvider
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

open class ContinuationResumeWebViewClient(
	private val continuation: Continuation<Unit>,
	private val proxyProvider: ProxyProvider? = null,
) : WebViewClient() {

	override fun onPageFinished(view: WebView?, url: String?) {
		resumeContinuation(view)
	}

	protected fun resumeContinuation(view: WebView?) {
		view?.webViewClient = ProxyWebViewClient(proxyProvider)
		continuation.resume(Unit)
	}

	/**
	 * A simple WebViewClient placeholder
	 */
	private class ProxyWebViewClient(
		private val proxyProvider: ProxyProvider?
	) : WebViewClient() {
		// This is just a placeholder to replace the continuation client
		// Proxy authentication for HTTP proxies is not supported here
		// since WebView doesn't have native support for it
	}
}
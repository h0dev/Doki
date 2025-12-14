package org.dokiteam.doki.core.network.webview

import android.webkit.HttpAuthHandler
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

	override fun onReceivedHttpAuthRequest(
		view: WebView?,
		handler: HttpAuthHandler?,
		host: String?,
		realm: String?
	) {
		val credentials = proxyProvider?.getProxyCredentials()
		if (handler != null && credentials != null) {
			handler.proceed(credentials.first, credentials.second)
		} else {
			super.onReceivedHttpAuthRequest(view, handler, host, realm)
		}
	}

	protected fun resumeContinuation(view: WebView?) {
		view?.webViewClient = WebViewClient() // reset to default
		continuation.resume(Unit)
	}
}
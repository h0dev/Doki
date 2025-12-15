package org.dokiteam.doki.scrobbling.discord.ui

import android.graphics.Bitmap
import android.webkit.WebView
import okhttp3.OkHttpClient
import org.dokiteam.doki.browser.BrowserCallback
import org.dokiteam.doki.browser.BrowserClient
import org.dokiteam.doki.parsers.util.removeSurrounding

class DiscordTokenWebClient(
	private val callback: Callback,
	proxyProvider: org.dokiteam.doki.core.network.proxy.ProxyProvider? = null,
	okHttpClient: OkHttpClient? = null,
) : BrowserClient(callback, null, proxyProvider, okHttpClient) {

	override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
		super.onPageStarted(view, url, favicon)
		if (view != null) {
			checkToken(view)
		}
	}

	private fun checkToken(view: WebView) {
		view.evaluateJavascript("window.localStorage.token") { result ->
			val token = result
				?.replace("\\\"", "")
				?.removeSurrounding('"')
				?.takeUnless { it == "null" }
			if (!token.isNullOrEmpty()) {
				callback.onTokenObtained(token)
			}
		}
	}

	interface Callback : BrowserCallback {

		fun onTokenObtained(token: String)
	}
}

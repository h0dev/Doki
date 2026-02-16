package org.dokiteam.doki.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.dokiteam.doki.R
import org.dokiteam.doki.core.network.BaseHttpClient
import org.dokiteam.doki.core.prefs.AppSettings
import org.dokiteam.doki.core.ui.BasePreferenceFragment
import org.dokiteam.doki.core.util.ext.getDisplayMessage
import org.dokiteam.doki.core.util.ext.printStackTraceDebug
import org.dokiteam.doki.core.util.ext.viewLifecycleScope
import org.dokiteam.doki.parsers.util.await
import org.dokiteam.doki.settings.utils.EditTextBindListener
import org.dokiteam.doki.settings.utils.PasswordSummaryProvider
import org.dokiteam.doki.settings.utils.validation.DomainValidator
import org.dokiteam.doki.settings.utils.validation.PortNumberValidator
import java.net.Proxy
import java.net.SocketException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@AndroidEntryPoint
class ProxySettingsFragment : BasePreferenceFragment(R.string.proxy),
	SharedPreferences.OnSharedPreferenceChangeListener {

	private var testJob: Job? = null

	@Inject
	@BaseHttpClient
	lateinit var okHttpClient: OkHttpClient

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_proxy)
		@Suppress("UsePropertyAccessSyntax")
		findPreference<EditTextPreference>(AppSettings.KEY_PROXY_ADDRESS)?.setOnBindEditTextListener(
			EditTextBindListener(
				inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_URI,
				hint = null,
				validator = DomainValidator(),
			),
		)
		@Suppress("UsePropertyAccessSyntax")
		findPreference<EditTextPreference>(AppSettings.KEY_PROXY_PORT)?.setOnBindEditTextListener(
			EditTextBindListener(
				inputType = EditorInfo.TYPE_CLASS_NUMBER,
				hint = null,
				validator = PortNumberValidator(),
			),
		)
		findPreference<EditTextPreference>(AppSettings.KEY_PROXY_PASSWORD)?.let { pref ->
			@Suppress("UsePropertyAccessSyntax")
			pref.setOnBindEditTextListener(
				EditTextBindListener(
					inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD,
					hint = null,
					validator = null,
				),
			)
			pref.summaryProvider = PasswordSummaryProvider()
		}
		updateDependencies()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		settings.subscribe(this)
	}

	override fun onDestroyView() {
		settings.unsubscribe(this)
		super.onDestroyView()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean = when (preference.key) {
		AppSettings.KEY_PROXY_TEST -> {
			testConnection()
			true
		}

		else -> super.onPreferenceTreeClick(preference)
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		when (key) {
			AppSettings.KEY_PROXY_TYPE -> updateDependencies()
		}
	}

	private fun updateDependencies() {
		val isProxyEnabled = settings.proxyType != Proxy.Type.DIRECT
		findPreference<Preference>(AppSettings.KEY_PROXY_ADDRESS)?.isEnabled = isProxyEnabled
		findPreference<Preference>(AppSettings.KEY_PROXY_PORT)?.isEnabled = isProxyEnabled
		findPreference<PreferenceCategory>(AppSettings.KEY_PROXY_AUTH)?.isEnabled = isProxyEnabled
		findPreference<Preference>(AppSettings.KEY_PROXY_LOGIN)?.isEnabled = isProxyEnabled
		findPreference<Preference>(AppSettings.KEY_PROXY_PASSWORD)?.isEnabled = isProxyEnabled
		findPreference<Preference>(AppSettings.KEY_PROXY_TEST)?.isEnabled = isProxyEnabled && testJob?.isActive != true
	}

	private fun testConnection() {
		testJob?.cancel()
		testJob = viewLifecycleScope.launch {
			val pref = findPreference<Preference>(AppSettings.KEY_PROXY_TEST)
			pref?.run {
				setSummary(R.string.loading_)
				isEnabled = false
			}
			try {
				val proxyTestClient = okHttpClient.newBuilder()
					.connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
					.readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
					.writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
					.build()

				withContext(Dispatchers.Default) {
					val request = Request.Builder()
						.get()
						.url("https://httpbin.org/ip")
						.build()
					proxyTestClient.newCall(request).await().use { response ->
						if (!response.isSuccessful) {
							when (response.code) {
								407 -> throw Exception(getString(R.string.proxy_authentication_required))
								403, 401 -> throw Exception(getString(R.string.proxy_access_denied))
								502, 503 -> throw Exception(getString(R.string.proxy_unavailable))
								else -> throw Exception("${getString(R.string.error_request_failed)}: ${response.code} ${response.message}")
							}
						}
						val responseBody = response.body?.string()
						if (responseBody.isNullOrEmpty()) {
							throw Exception(getString(R.string.error_empty_response))
						}
					}
				}
				showTestResult(null)
			} catch (e: CancellationException) {
				throw e
			} catch (e: java.net.SocketTimeoutException) {
				e.printStackTraceDebug()
				showTestResult(Exception("${getString(R.string.proxy_timeout)}: ${e.message}"))
			} catch (e: java.net.ConnectException) {
				e.printStackTraceDebug()
				showTestResult(Exception("${getString(R.string.proxy_connection_failed)}: ${e.message}"))
			} catch (e: java.net.UnknownHostException) {
				e.printStackTraceDebug()
				showTestResult(Exception("${getString(R.string.proxy_unknown_host)}: ${e.message}"))
			} catch (e: javax.net.ssl.SSLException) {
				e.printStackTraceDebug()
				showTestResult(Exception("${getString(R.string.proxy_ssl_error)}: ${e.message}"))
			} catch (e: java.net.SocketException) {
				if (e.message?.contains("proxy", ignoreCase = true) == true) {
					e.printStackTraceDebug()
					showTestResult(Exception("${getString(R.string.proxy_connection_failed)}: ${e.message}"))
				} else {
					throw e
				}
			} catch (e: Throwable) {
				e.printStackTraceDebug()
				showTestResult(e)
			} finally {
				pref?.run {
					isEnabled = true
					summary = null
				}
			}
		}
	}

	private fun showTestResult(error: Throwable?) {
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.proxy)
			.setMessage(error?.getDisplayMessage(resources) ?: getString(R.string.connection_ok))
			.setPositiveButton(android.R.string.ok, null)
			.setCancelable(true)
			.show()
	}
}

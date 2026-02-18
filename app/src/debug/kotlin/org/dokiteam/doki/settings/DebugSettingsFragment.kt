package org.dokiteam.doki.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import leakcanary.LeakCanary
import org.dokiteam.doki.DokiApp
import org.dokiteam.doki.R
import org.dokiteam.doki.core.ui.BasePreferenceFragment
import org.dokiteam.doki.settings.utils.SplitSwitchPreference
import org.koitharu.workinspector.WorkInspector
import java.io.File
import java.util.Date

class DebugSettingsFragment : BasePreferenceFragment(R.string.debug), Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

	private val application
		get() = requireContext().applicationContext as DokiApp

	private val requestPermissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { isGranted ->
		if (isGranted) {
			Toast.makeText(context, "Permission granted", Toast.LENGTH_SHORT).show()
		} else {
			Toast.makeText(context, R.string.permission_denied, Toast.LENGTH_SHORT).show()
		}
	}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_debug)
		findPreference<SplitSwitchPreference>(KEY_LEAK_CANARY)?.let { pref ->
			pref.isChecked = application.isLeakCanaryEnabled
			pref.onPreferenceChangeListener = this
			pref.onContainerClickListener = this
		}
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		val view = super.onCreateView(inflater, container, savedInstanceState)
		return view
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		setupLogcatControls()
	}

	private fun setupLogcatControls() {
		findPreference<Preference>(KEY_LOGCAT_VIEWER)?.setOnPreferenceClickListener {
			startActivity(android.content.Intent(context, org.dokiteam.doki.core.ui.LogcatViewerActivity::class.java))
			true
		}
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean = when (preference.key) {
		KEY_WORK_INSPECTOR -> {
			startActivity(WorkInspector.getIntent(preference.context))
			true
		}

		else -> super.onPreferenceTreeClick(preference)
	}

	override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean = when (preference.key) {
		KEY_LEAK_CANARY -> {
			application.isLeakCanaryEnabled = newValue as Boolean
			true
		}

		else -> false
	}

	override fun onPreferenceClick(preference: Preference): Boolean = when (preference.key) {
		KEY_LEAK_CANARY -> {
			startActivity(LeakCanary.newLeakDisplayActivityIntent())
			true
		}

		else -> false
	}

	private companion object {
		const val KEY_LEAK_CANARY = "leak_canary"
		const val KEY_WORK_INSPECTOR = "work_inspector"
		const val KEY_LOGCAT_VIEWER = "logcat_viewer"
	}
}



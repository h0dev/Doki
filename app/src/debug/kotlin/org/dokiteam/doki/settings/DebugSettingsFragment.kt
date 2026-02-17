package org.dokiteam.doki.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import leakcanary.LeakCanary
import org.dokiteam.doki.DokiApp
import org.dokiteam.doki.R
import org.dokiteam.doki.core.ui.BasePreferenceFragment
import org.dokiteam.doki.settings.utils.SplitSwitchPreference
import org.koitharu.workinspector.WorkInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugSettingsFragment : BasePreferenceFragment(R.string.debug), Preference.OnPreferenceChangeListener {

	private val application
		get() = requireContext().applicationContext as DokiApp

	private var logcatContainer: LinearLayout? = null
	private var logcatAdapter = LogcatAdapter()
	private var filterSpinner: androidx.appcompat.widget.AppCompatSpinner? = null
	private var isLoading: Boolean = false
	private val filters = arrayOf("All", "Verbose", "Debug", "Info", "Warn", "Error", "Assert")

	private val requestPermissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { isGranted ->
		if (isGranted) {
			loadLogcat()
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

		setupLogcatToolbar()
		
		if (this.recyclerView != null) {
			this.recyclerView?.layoutManager = LinearLayoutManager(requireContext())
			this.recyclerView?.adapter = logcatAdapter
		}

		setupLogcatControls()

		loadLogcat()
	}

	private fun setupLogcatControls() {
		findPreference<Preference>(KEY_LOGCAT_VIEWER)?.setOnPreferenceClickListener {
			loadLogcat()
			true
		}
	}

	private fun setupLogcatToolbar() {
		val listView = view?.findViewById<androidx.recyclerview.widget.RecyclerView>(androidx.preference.R.id.recyclerview)
		val container = listView?.parent as? ViewGroup

		val toolbarLayout = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.HORIZONTAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				setMargins(16, 16, 16, 8)
			}
		}

		filterSpinner = androidx.appcompat.widget.AppCompatSpinner(requireContext()).apply {
			layoutParams = LinearLayout.LayoutParams(
				0,
				LinearLayout.LayoutParams.WRAP_CONTENT,
				1f
			).apply {
				setMargins(0, 0, 8, 0)
			}
			val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, filters)
			adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
			this.adapter = adapter
			setSelection(0)
			setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
				override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
					val filter = filters[position]
					logcatAdapter.setFilter(filter)
				}

				override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
			})
		}

		val saveButton = android.widget.Button(requireContext()).apply {
			text = "Save"
			setOnClickListener {
				if (logcatAdapter.itemCount > 0) {
					checkAndRequestPermission()
				} else {
					Toast.makeText(context, "No logcat data to save", Toast.LENGTH_SHORT).show()
				}
			}
		}

		val copyButton = android.widget.Button(requireContext()).apply {
			text = "Copy"
			setOnClickListener {
				if (logcatAdapter.itemCount > 0) {
					val logText = logcatAdapter.getLogText()
					copyToClipboard(logText)
				} else {
					Toast.makeText(context, "No logcat data to copy", Toast.LENGTH_SHORT).show()
				}
			}
		}

		val clearButton = android.widget.Button(requireContext()).apply {
			text = "Clear"
			setOnClickListener {
				logcatAdapter.clearLogs()
			}
		}

		toolbarLayout.addView(filterSpinner)
		toolbarLayout.addView(saveButton)
		toolbarLayout.addView(copyButton)
		toolbarLayout.addView(clearButton)

		container?.addView(toolbarLayout, 0)
	}

	private fun loadLogcat() {
		if (isLoading) return

		viewLifecycleOwner.lifecycleScope.launch {
			isLoading = true
			try {
				val logs = withContext(Dispatchers.IO) {
					runLogcatCommand()
				}
				logcatAdapter.updateLogs(logs)
			} catch (e: Exception) {
				e.printStackTrace()
				Toast.makeText(context, "Error loading logcat: ${e.message}", Toast.LENGTH_LONG).show()
			} finally {
				isLoading = false
			}
		}
	}

	private fun runLogcatCommand(): List<LogcatEntry> {
		val logs = mutableListOf<LogcatEntry>()
		try {
			val process = Runtime.getRuntime().exec("logcat -v brief -t 100")
			val reader = process.inputStream.bufferedReader()

			reader.forEachLine { line ->
				if (line.isNotEmpty()) {
					val logEntry = parseLogLine(line)
					if (logEntry != null) {
						logs.add(logEntry)
					}
				}
			}

			process.destroy()
		} catch (e: Exception) {
			e.printStackTrace()
		}
		return logs
	}

	private fun parseLogLine(line: String): LogcatEntry? {
		val regex = Regex("""([VDIWEF])/(.+?)\(\s*(\d+)\):\s*(.*)""")
		val match = regex.find(line)

		if (match != null) {
			val (priority, tag, pid, message) = match.destructured
			val priorityName = when (priority) {
				"V" -> "Verbose"
				"D" -> "Debug"
				"I" -> "Info"
				"W" -> "Warn"
				"E" -> "Error"
				"F" -> "Assert"
				else -> "Unknown"
			}
			return LogcatEntry(priorityName, tag, pid.toIntOrNull() ?: 0, message, line)
		}

		return LogcatEntry("Unknown", "Unknown", 0, line, line)
	}

	private fun checkAndRequestPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (ContextCompat.checkSelfPermission(
					requireContext(),
					Manifest.permission.WRITE_EXTERNAL_STORAGE
				) != PackageManager.PERMISSION_GRANTED
			) {
				requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
			} else {
				saveLogcatToFile()
			}
		} else {
			saveLogcatToFile()
		}
	}

	private fun saveLogcatToFile() {
		viewLifecycleOwner.lifecycleScope.launch {
			try {
				val logText = logcatAdapter.getLogText()
				val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
				val currentDateAndTime = sdf.format(Date())
				val fileName = "logcat_$currentDateAndTime.txt"

				val file = File(requireContext().getExternalFilesDir(null), fileName)
				file.writeText(logText)

				Toast.makeText(
					context,
					"Log saved to ${file.absolutePath}",
					Toast.LENGTH_LONG
				).show()
			} catch (e: Exception) {
				e.printStackTrace()
				Toast.makeText(context, "Error saving log: ${e.message}", Toast.LENGTH_LONG).show()
			}
		}
	}

	private fun copyToClipboard(text: String) {
		try {
			val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
			val clip = android.content.ClipData.newPlainText("Logcat", text)
			clipboard.setPrimaryClip(clip)
			Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
		} catch (e: Exception) {
			e.printStackTrace()
			Toast.makeText(context, "Error copying to clipboard: ${e.message}", Toast.LENGTH_LONG).show()
		}
	}

	private fun clearLogs() {
		logcatAdapter.clearLogs()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean = when (preference.key) {
		KEY_WORK_INSPECTOR -> {
			startActivity(WorkInspector.getIntent(preference.context))
			true
		}

		KEY_LOGCAT_VIEWER -> {
			loadLogcat()
			true
		}

		else -> super.onPreferenceTreeClick(preference)
	}

	override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean = when (preference.key) {
		KEY_LEAK_CANARY -> {
			application.isLeakCanaryEnabled = newValue as Boolean
			true
		}

		else -> super.onPreferenceChange(preference, newValue)
	}

	override fun onPreferenceClick(preference: Preference): Boolean = when (preference.key) {
		KEY_LEAK_CANARY -> {
			startActivity(LeakCanary.newLeakDisplayActivityIntent())
			true
		}

		else -> super.onPreferenceClick(preference)
	}

	private companion object {
		const val KEY_LEAK_CANARY = "leak_canary"
		const val KEY_WORK_INSPECTOR = "work_inspector"
		const val KEY_LOGCAT_VIEWER = "logcat_viewer"
	}
}

data class LogcatEntry(
	val priority: String,
	val tag: String,
	val pid: Int,
	val message: String,
	val fullLine: String
)

class LogcatAdapter : RecyclerView.Adapter<LogcatAdapter.LogcatViewHolder>() {
	private var logs = mutableListOf<LogcatEntry>()
	private var filteredLogs = mutableListOf<LogcatEntry>()

	fun updateLogs(newLogs: List<LogcatEntry>) {
		logs.clear()
		logs.addAll(newLogs)
		filteredLogs.clear()
		filteredLogs.addAll(logs)
		notifyDataSetChanged()
	}

	fun clearLogs() {
		logs.clear()
		filteredLogs.clear()
		notifyDataSetChanged()
	}

	fun setFilter(filter: String) {
		filteredLogs.clear()
		when (filter) {
			"All" -> filteredLogs.addAll(logs)
			else -> filteredLogs.addAll(logs.filter { it.priority == filter })
		}
		notifyDataSetChanged()
	}

	fun getLogText(): String {
		return filteredLogs.joinToString("\n") { it.fullLine }
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogcatViewHolder {
		val textView = android.widget.TextView(parent.context).apply {
			layoutParams = RecyclerView.LayoutParams(
				RecyclerView.LayoutParams.MATCH_PARENT,
				RecyclerView.LayoutParams.WRAP_CONTENT
			)
			setPadding(16, 8, 16, 8)
			setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
			setTextColor(android.graphics.Color.WHITE)
			setBackgroundColor(android.graphics.Color.parseColor("#333333"))
		}
		return LogcatViewHolder(textView)
	}

	override fun onBindViewHolder(holder: LogcatViewHolder, position: Int) {
		val log = filteredLogs[position]
		val color = getPriorityColor(log.priority)
		holder.textView.setTextColor(android.graphics.Color.parseColor(color))

		val formattedText = formatLogMessage(log)
		holder.textView.text = formattedText
	}

	private fun formatLogMessage(log: LogcatEntry): String {
		return "${log.priority}/${log.tag}(${log.pid}): ${log.message}"
	}

	private fun getPriorityColor(priority: String): String {
		return when (priority) {
			"Verbose" -> "#888888"
			"Debug" -> "#2196F3"
			"Info" -> "#4CAF50"
			"Warn" -> "#FF9800"
			"Error" -> "#F44336"
			"Assert" -> "#9C27B0"
			else -> "#FFFFFF"
		}
	}

	override fun getItemCount(): Int = filteredLogs.size

	class LogcatViewHolder(val textView: android.widget.TextView) : RecyclerView.ViewHolder(textView)
}

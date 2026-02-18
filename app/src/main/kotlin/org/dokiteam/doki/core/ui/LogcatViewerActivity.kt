package org.dokiteam.doki.core.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.dokiteam.doki.R
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class LogcatViewerActivity : AppCompatActivity() {

    private lateinit var spinnerLogLevel: Spinner
    private lateinit var btnSave: Button
    private lateinit var btnCopy: Button
    private lateinit var btnClear: Button
    private lateinit var tvLogcatOutput: TextView
    private lateinit var progressBar: ProgressBar

    private var logLevel = "All"
    private var logcatProcess: Process? = null
    private var isReadingLogs = false
    private val logBuffer = mutableListOf<String>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Storage permission is needed to save logs", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logcat_viewer)

        initViews()
        setupLogLevelSpinner()
        setupClickListeners()
        startLogcatReading()
    }

    private fun initViews() {
        spinnerLogLevel = findViewById(R.id.spinnerLogLevel)
        btnSave = findViewById(R.id.btnSave)
        btnCopy = findViewById(R.id.btnCopy)
        btnClear = findViewById(R.id.btnClear)
        tvLogcatOutput = findViewById(R.id.tvLogcatOutput)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupLogLevelSpinner() {
        val logLevels = arrayOf("All", "Verbose", "Debug", "Info", "Warn", "Error", "Assert")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, logLevels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLogLevel.adapter = adapter
        spinnerLogLevel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                logLevel = logLevels[position]
                filterAndDisplayLogs()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupClickListeners() {
        btnSave.setOnClickListener {
            if (hasStoragePermission()) {
                saveLogcatToFile()
            } else {
                requestStoragePermission()
            }
        }

        btnCopy.setOnClickListener {
            copyLogcatToClipboard()
        }

        btnClear.setOnClickListener {
            clearLogcatOutput()
        }
    }

    private fun startLogcatReading() {
        isReadingLogs = true
        Thread {
            try {
                logcatProcess = Runtime.getRuntime().exec("logcat -v time")
                val bufferedReader = BufferedReader(InputStreamReader(logcatProcess!!.inputStream))

                var line: String?
                while (isReadingLogs && bufferedReader.readLine().also { line = it } != null) {
                    line?.let { logLine ->
                        synchronized(logBuffer) {
                            logBuffer.add(logLine)
                        }
                        runOnUiThread {
                            filterAndDisplayLogs()
                        }
                    }
                }
            } catch (e: IOException) {
                runOnUiThread {
                    tvLogcatOutput.text = "Error reading logcat: ${e.message}"
                }
            }
        }.start()
    }

    private fun filterAndDisplayLogs() {
        runOnUiThread {
            val filteredLogs = synchronized(logBuffer) {
                when (logLevel) {
                    "Verbose" -> logBuffer.filter { containsLogLevel(it, "V/") }
                    "Debug" -> logBuffer.filter { containsLogLevel(it, "D/") }
                    "Info" -> logBuffer.filter { containsLogLevel(it, "I/") }
                    "Warn" -> logBuffer.filter { containsLogLevel(it, "W/") }
                    "Error" -> logBuffer.filter { containsLogLevel(it, "E/") }
                    "Assert" -> logBuffer.filter { containsLogLevel(it, "A/") }
                    else -> logBuffer
                }
            }

            val coloredLogs = StringBuilder()
            filteredLogs.forEach { logLine ->
                coloredLogs.append(formatLogLine(logLine)).append("\n")
            }

            tvLogcatOutput.text = coloredLogs.toString()
            tvLogcatOutput.setSelection(
                tvLogcatOutput.text.length.coerceAtMost(100000)
            )
        }
    }

    private fun containsLogLevel(logLine: String, levelPrefix: String): Boolean {
        return if (logLine.contains(": ")) {
            val parts = logLine.split(": ", limit = 2)
            parts[0].contains(levelPrefix)
        } else {
            logLine.contains(levelPrefix)
        }
    }

    private fun formatLogLine(logLine: String): String {
        return when {
            logLine.contains(": V/") || logLine.contains("/V ") -> formatWithColor(logLine, "#808080")
            logLine.contains(": D/") || logLine.contains("/D ") -> formatWithColor(logLine, "#0000FF")
            logLine.contains(": I/") || logLine.contains("/I ") -> formatWithColor(logLine, "#008000")
            logLine.contains(": W/") || logLine.contains("/W ") -> formatWithColor(logLine, "#FFFF00")
            logLine.contains(": E/") || logLine.contains("/E ") -> formatWithColor(logLine, "#FF0000")
            logLine.contains(": A/") || logLine.contains("/A ") -> formatWithColor(logLine, "#FF00FF")
            else -> logLine
        }
    }

    private fun formatWithColor(logLine: String, color: String): String {
        return logLine
    }

    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun saveLogcatToFile() {
        progressBar.visibility = ProgressBar.VISIBLE
        Thread {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                val fileName = "logcat_${dateFormat.format(Date())}.txt"
                
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                
                synchronized(logBuffer) {
                    file.writeText(logBuffer.joinToString("\n"))
                }
                
                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(this, "Logcat saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(this, "Error saving logcat: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun copyLogcatToClipboard() {
        val service = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val filteredLogs = synchronized(logBuffer) {
            when (logLevel) {
                "Verbose" -> logBuffer.filter { containsLogLevel(it, "V/") }
                "Debug" -> logBuffer.filter { containsLogLevel(it, "D/") }
                "Info" -> logBuffer.filter { containsLogLevel(it, "I/") }
                "Warn" -> logBuffer.filter { containsLogLevel(it, "W/") }
                "Error" -> logBuffer.filter { containsLogLevel(it, "E/") }
                "Assert" -> logBuffer.filter { containsLogLevel(it, "A/") }
                else -> logBuffer
            }
        }
        
        val clip = android.content.ClipData.newPlainText("Logcat Output", filteredLogs.joinToString("\n"))
        service.setPrimaryClip(clip)
        Toast.makeText(this, "Logcat copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun clearLogcatOutput() {
        synchronized(logBuffer) {
            logBuffer.clear()
        }
        tvLogcatOutput.text = ""
    }

    override fun onDestroy() {
        super.onDestroy()
        isReadingLogs = false
        logcatProcess?.destroy()
    }
}
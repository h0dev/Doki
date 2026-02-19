package org.dokiteam.doki.settings.sources

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.dokiteam.doki.R
import org.dokiteam.doki.parsers.model.MangaParserSource
import java.text.SimpleDateFormat
import java.util.*

class SourceTesterActivity : AppCompatActivity() {

    private lateinit var tvTestOutput: TextView
    private lateinit var btnStartTest: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        
        val title = TextView(this).apply {
            text = "Source Testing"
            textSize = 24f
            setPadding(16, 16, 16, 16)
        }
        layout.addView(title)
        
        val scrollView = ScrollView(this)
        tvTestOutput = TextView(this).apply {
            textSize = 12f
            setPadding(16, 0, 16, 16)
        }
        scrollView.addView(tvTestOutput)
        layout.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ).apply {
            setMargins(16, 0, 16, 16)
        })
        
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            visibility = ProgressBar.GONE
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(16, 0, 16, 16)
            this@apply.layoutParams = layoutParams
        }
        layout.addView(progressBar)
        
        btnStartTest = Button(this).apply {
            text = "Test All Sources"
            setPadding(32, 16, 32, 16)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(16, 0, 16, 16)
            this@apply.layoutParams = layoutParams
        }
        layout.addView(btnStartTest)
        
        setContentView(layout)
        
        ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        btnStartTest.setOnClickListener {
            startSourceTesting()
        }
    }

    private fun startSourceTesting() {
        btnStartTest.isEnabled = false
        progressBar.visibility = ProgressBar.VISIBLE
        tvTestOutput.text = "Starting source testing...\n"
        
        Thread {
            var successCount = 0
            var errorCount = 0
            val totalSources = MangaParserSource.entries.size
            
            for ((index, source) in MangaParserSource.entries.withIndex()) {
                try {
                    val testLog = "Testing source ${index + 1}/${totalSources}: ${source.name} (locale: ${source.locale}, type: ${source.contentType})\n"
                    appendToOutput(testLog)
                    
                    runOnUiThread {
                        progressBar.progress = ((index + 1).toFloat() / totalSources * 100).toInt()
                    }
                    
                    val sourceTitle = source.title
                    val sourceLocale = source.locale
                    val sourceContentType = source.contentType
                    val sourceIsBroken = source.isBroken
                    
                    val successLog = "SUCCESS: ${source.name} - Title: $sourceTitle, Locale: $sourceLocale, Type: $sourceContentType, Broken: $sourceIsBroken\n"
                    appendToOutput(successLog)
                    
                    successCount++
                    
                } catch (e: Exception) {
                    val errorLog = "ERROR: ${source.name} failed - ${e.message}\n"
                    appendToOutput(errorLog)
                    errorCount++
                }
            }
            
            val completionTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val finalLog = "\nTesting completed at $completionTime\n" +
                          "Summary: $successCount successful, $errorCount errors, $totalSources total sources\n"
            
            appendToOutput(finalLog)
            
            runOnUiThread {
                progressBar.visibility = ProgressBar.GONE
                btnStartTest.isEnabled = true
                Toast.makeText(this, "Source testing completed. Success: $successCount, Errors: $errorCount", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun appendToOutput(text: String) {
        runOnUiThread {
            tvTestOutput.text = tvTestOutput.text.toString() + text
            tvTestOutput.parent?.let { parent ->
                if (parent is ScrollView) {
                    parent.fullScroll(ScrollView.FOCUS_DOWN)
                }
            }
        }
    }
}
        
        val title = TextView(this).apply {
            text = "Source Testing"
            textSize = 24f
            setPadding(16, 16, 16, 16)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(0, 8, 0, 16)
            this@apply.layoutParams = layoutParams
        }
        layout.addView(title)
        
        val scrollView = ScrollView(this)
        tvTestOutput = TextView(this).apply {
            textSize = 12f
            setPadding(16, 0, 16, 16)
        }
        scrollView.addView(tvTestOutput)
        layout.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ).apply {
            setMargins(16, 0, 16, 16)
        })
        
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            visibility = ProgressBar.GONE
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(16, 0, 16, 16)
            this@apply.layoutParams = layoutParams
        }
        layout.addView(progressBar)
        
        btnStartTest = Button(this).apply {
            text = "Test All Sources"
            setPadding(32, 16, 32, 16)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(16, 0, 16, 16)
            this@apply.layoutParams = layoutParams
        }
        layout.addView(btnStartTest)
        
        setContentView(layout)
        
        btnStartTest.setOnClickListener {
            startSourceTesting()
        }
    }



    private fun startSourceTesting() {
        btnStartTest.isEnabled = false
        progressBar.visibility = ProgressBar.VISIBLE
        tvTestOutput.text = "Starting source testing...\n"
        
        Thread {
            var successCount = 0
            var errorCount = 0
            val totalSources = MangaParserSource.entries.size
            
            for ((index, source) in MangaParserSource.entries.withIndex()) {
                try {
                    val testLog = "Testing source ${index + 1}/${totalSources}: ${source.name} (locale: ${source.locale}, type: ${source.contentType})\n"
                    appendToOutput(testLog)
                    
                    runOnUiThread {
                        progressBar.progress = ((index + 1).toFloat() / totalSources * 100).toInt()
                    }
                    
                    val sourceTitle = source.title
                    val sourceLocale = source.locale
                    val sourceContentType = source.contentType
                    val sourceIsBroken = source.isBroken
                    
                    val successLog = "SUCCESS: ${source.name} - Title: $sourceTitle, Locale: $sourceLocale, Type: $sourceContentType, Broken: $sourceIsBroken\n"
                    appendToOutput(successLog)
                    
                    successCount++
                    
                } catch (e: Exception) {
                    val errorLog = "ERROR: ${source.name} failed - ${e.message}\n"
                    appendToOutput(errorLog)
                    errorCount++
                }
            }
            
            val completionTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val finalLog = "\nTesting completed at $completionTime\n" +
                          "Summary: $successCount successful, $errorCount errors, $totalSources total sources\n"
            
            appendToOutput(finalLog)
            
            runOnUiThread {
                progressBar.visibility = ProgressBar.GONE
                btnStartTest.isEnabled = true
                Toast.makeText(this, "Source testing completed. Success: $successCount, Errors: $errorCount", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun appendToOutput(text: String) {
        runOnUiThread {
            tvTestOutput.text = tvTestOutput.text.toString() + text
            tvTestOutput.parent?.let { parent ->
                if (parent is ScrollView) {
                    parent.fullScroll(ScrollView.FOCUS_DOWN)
                }
            }
        }
    }
}
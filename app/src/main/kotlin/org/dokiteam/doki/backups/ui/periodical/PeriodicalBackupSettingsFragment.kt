package org.dokiteam.doki.backups.ui.periodical

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import androidx.activity.result.ActivityResultCallback
import androidx.fragment.app.viewModels
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.dokiteam.doki.R
import org.dokiteam.doki.core.exceptions.resolve.SnackbarErrorObserver
import org.dokiteam.doki.core.nav.router
import org.dokiteam.doki.core.os.OpenDocumentTreeHelper
import org.dokiteam.doki.core.prefs.AppSettings
import org.dokiteam.doki.core.ui.BasePreferenceFragment
import org.dokiteam.doki.core.util.ext.observe
import org.dokiteam.doki.core.util.ext.observeEvent
import org.dokiteam.doki.core.util.ext.tryLaunch
import org.dokiteam.doki.settings.utils.EditTextFallbackSummaryProvider
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class PeriodicalBackupSettingsFragment : BasePreferenceFragment(R.string.periodic_backups),
	ActivityResultCallback<Uri?> {

	@Inject
	lateinit var telegramBackupUploader: TelegramBackupUploader

	@Inject
	lateinit var googleDriveBackupUploader: GoogleDriveBackupUploader

	private val viewModel by viewModels<PeriodicalBackupSettingsViewModel>()

	private val outputSelectCall = OpenDocumentTreeHelper(this, this)

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_backup_periodic)
		findPreference<PreferenceCategory>(AppSettings.KEY_BACKUP_TG)?.isVisible = viewModel.isTelegramAvailable
		findPreference<EditTextPreference>(AppSettings.KEY_BACKUP_TG_CHAT)?.summaryProvider =
			EditTextFallbackSummaryProvider(R.string.telegram_chat_id_summary)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		viewModel.lastBackupDate.observe(viewLifecycleOwner, ::bindLastBackupInfo)
		viewModel.backupsDirectory.observe(viewLifecycleOwner, ::bindOutputSummary)
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(listView, this))
		viewModel.isTelegramCheckLoading.observe(viewLifecycleOwner) {
			findPreference<Preference>(AppSettings.KEY_BACKUP_TG_TEST)?.isEnabled = !it
		}
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		val result = when (preference.key) {
			AppSettings.KEY_BACKUP_PERIODICAL_OUTPUT -> outputSelectCall.tryLaunch(null)
			AppSettings.KEY_BACKUP_TG_OPEN -> telegramBackupUploader.openBotInApp(router)
			AppSettings.KEY_BACKUP_TG_TEST -> {
				viewModel.checkTelegram()
				true
			}
			AppSettings.KEY_BACKUP_GDRIVE_AUTH -> {
				// TODO: Implement Google Drive authentication
				Snackbar.make(listView, R.string.google_drive_not_implemented, Snackbar.LENGTH_LONG).show()
				true
			}
			AppSettings.KEY_BACKUP_GDRIVE_TEST -> {
				// TODO: Implement Google Drive connection test
				Snackbar.make(listView, R.string.google_drive_not_implemented, Snackbar.LENGTH_LONG).show()
				true
			}

			else -> return super.onPreferenceTreeClick(preference)
		}
		if (!result) {
			Snackbar.make(listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
		}
		return true
	}

	override fun onActivityResult(result: Uri?) {
		if (result != null) {
			val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
			context?.contentResolver?.takePersistableUriPermission(result, takeFlags)
			settings.periodicalBackupDirectory = result
			viewModel.updateSummaryData()
		}
	}

	private fun bindOutputSummary(path: String?) {
		val preference = findPreference<Preference>(AppSettings.KEY_BACKUP_PERIODICAL_OUTPUT) ?: return
		preference.summary = when (path) {
			null -> getString(R.string.invalid_value_message)
			"" -> null
			else -> path
		}
		preference.icon = if (path == null) {
			getWarningIcon()
		} else {
			null
		}
	}

	private fun bindLastBackupInfo(lastBackupDate: Date?) {
		val preference = findPreference<Preference>(AppSettings.KEY_BACKUP_PERIODICAL_LAST) ?: return
		preference.summary = lastBackupDate?.let {
			preference.context.getString(
				R.string.last_successful_backup,
				DateUtils.getRelativeTimeSpanString(it.time),
			)
		}
		preference.isVisible = lastBackupDate != null
	}
}


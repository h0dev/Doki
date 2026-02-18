package org.dokiteam.doki.core

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.room.InvalidationTracker
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.dokiteam.doki.core.util.CrashCopyHandler
import org.conscrypt.Conscrypt
import org.dokiteam.doki.BuildConfig
import org.dokiteam.doki.R
import org.dokiteam.doki.core.db.MangaDatabase
import org.dokiteam.doki.core.os.AppValidator
import org.dokiteam.doki.core.os.RomCompat
import org.dokiteam.doki.core.prefs.AppSettings
import org.dokiteam.doki.core.util.WorkServiceStopHelper
import org.dokiteam.doki.core.util.ext.processLifecycleScope
import org.dokiteam.doki.local.data.LocalStorageChanges
import org.dokiteam.doki.local.data.index.LocalMangaIndex
import org.dokiteam.doki.local.domain.model.LocalManga
import org.dokiteam.doki.parsers.util.suspendlazy.getOrNull
import org.dokiteam.doki.settings.work.WorkScheduleManager
import java.security.Security
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
open class BaseApp : Application(), Configuration.Provider {

	@Inject
	lateinit var databaseObserversProvider: Provider<Set<@JvmSuppressWildcards InvalidationTracker.Observer>>

	@Inject
	lateinit var activityLifecycleCallbacks: Set<@JvmSuppressWildcards ActivityLifecycleCallbacks>

	@Inject
	lateinit var database: Provider<MangaDatabase>

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var workerFactory: HiltWorkerFactory

	@Inject
	lateinit var appValidator: AppValidator

	@Inject
	lateinit var workScheduleManager: WorkScheduleManager

	@Inject
	lateinit var workManagerProvider: Provider<WorkManager>

	@Inject
	lateinit var localMangaIndexProvider: Provider<LocalMangaIndex>

	@Inject
	@LocalStorageChanges
	lateinit var localStorageChanges: MutableSharedFlow<LocalManga?>

	override val workManagerConfiguration: Configuration
		get() = Configuration.Builder()
			.setWorkerFactory(workerFactory)
			.build()

	override fun onCreate() {
		super.onCreate()
		AppCompatDelegate.setDefaultNightMode(settings.theme)
		// TLS 1.3 support for Android < 10
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
			Security.insertProviderAt(Conscrypt.newProvider(), 1)
		}
		setupActivityLifecycleCallbacks()
		
		Thread.setDefaultUncaughtExceptionHandler(CrashCopyHandler(this, Thread.getDefaultUncaughtExceptionHandler()))
		
		processLifecycleScope.launch(Dispatchers.Default) {
			setupDatabaseObservers()
			localStorageChanges.collect(localMangaIndexProvider.get())
		}
		workScheduleManager.init()
		WorkServiceStopHelper(workManagerProvider).setup()
	}

	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(base)
	}

	@WorkerThread
	private fun setupDatabaseObservers() {
		val tracker = database.get().invalidationTracker
		databaseObserversProvider.get().forEach {
			tracker.addObserver(it)
		}
	}

	private fun setupActivityLifecycleCallbacks() {
		activityLifecycleCallbacks.forEach {
			registerActivityLifecycleCallbacks(it)
		}
	}
}

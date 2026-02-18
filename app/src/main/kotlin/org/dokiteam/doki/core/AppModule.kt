package org.dokiteam.doki.core

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.SearchRecentSuggestions
import android.text.Html
import androidx.collection.arraySetOf
import androidx.core.content.ContextCompat
import androidx.room.InvalidationTracker
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.svg.SvgDecoder
import coil3.util.DebugLogger
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import org.dokiteam.doki.BuildConfig
import org.dokiteam.doki.backups.domain.BackupObserver
import org.dokiteam.doki.core.db.MangaDatabase
import org.dokiteam.doki.core.exceptions.resolve.CaptchaHandler
import org.dokiteam.doki.core.image.AvifImageDecoder
import org.dokiteam.doki.core.image.CbzFetcher
import org.dokiteam.doki.core.image.MangaSourceHeaderInterceptor
import org.dokiteam.doki.core.network.MangaHttpClient
import org.dokiteam.doki.core.network.imageproxy.ImageProxyInterceptor
import org.dokiteam.doki.core.os.AppShortcutManager
import org.dokiteam.doki.core.os.NetworkState
import org.dokiteam.doki.core.parser.MangaLoaderContextImpl
import org.dokiteam.doki.core.parser.favicon.FaviconFetcher
import org.dokiteam.doki.core.prefs.AppSettings
import org.dokiteam.doki.core.ui.image.CoilImageGetter
import org.dokiteam.doki.core.ui.util.ActivityRecreationHandle
import org.dokiteam.doki.core.util.ScreenLogger
import org.dokiteam.doki.core.util.FileSize
import org.dokiteam.doki.core.util.ext.connectivityManager
import org.dokiteam.doki.core.util.ext.isLowRamDevice
import org.dokiteam.doki.details.ui.pager.pages.MangaPageFetcher
import org.dokiteam.doki.details.ui.pager.pages.MangaPageKeyer
import org.dokiteam.doki.local.data.CacheDir
import org.dokiteam.doki.local.data.FaviconCache
import org.dokiteam.doki.local.data.LocalStorageCache
import org.dokiteam.doki.local.data.LocalStorageChanges
import org.dokiteam.doki.local.data.PageCache
import org.dokiteam.doki.local.domain.model.LocalManga
import org.dokiteam.doki.main.domain.CoverRestoreInterceptor
import org.dokiteam.doki.main.ui.protect.AppProtectHelper
import org.dokiteam.doki.main.ui.protect.ScreenshotPolicyHelper
import org.dokiteam.doki.parsers.MangaLoaderContext
import org.dokiteam.doki.search.ui.MangaSuggestionsProvider
import org.dokiteam.doki.sync.domain.SyncController
import org.dokiteam.doki.widget.WidgetUpdater
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AppModule {

	@Binds
	fun bindMangaLoaderContext(mangaLoaderContextImpl: MangaLoaderContextImpl): MangaLoaderContext

	@Binds
	fun bindImageGetter(coilImageGetter: CoilImageGetter): Html.ImageGetter

	companion object {

		@Provides
		@LocalizedAppContext
		fun provideLocalizedContext(
			@ApplicationContext context: Context,
		): Context = ContextCompat.getContextForLanguage(context)

		@Provides
		@Singleton
		fun provideNetworkState(
			@ApplicationContext context: Context,
			settings: AppSettings,
		) = NetworkState(context.connectivityManager, settings)

		@Provides
		@Singleton
		fun provideMangaDatabase(
			@ApplicationContext context: Context,
		): MangaDatabase = MangaDatabase(context)

		@Provides
		@Singleton
		fun provideCoil(
			@LocalizedAppContext context: Context,
			@MangaHttpClient okHttpClientProvider: Provider<OkHttpClient>,
			faviconFetcherFactory: FaviconFetcher.Factory,
			imageProxyInterceptor: ImageProxyInterceptor,
			pageFetcherFactory: MangaPageFetcher.Factory,
			coverRestoreInterceptor: CoverRestoreInterceptor,
			networkStateProvider: Provider<NetworkState>,
			captchaHandler: CaptchaHandler,
		): ImageLoader {
			val diskCacheFactory = {
				val rootDir = context.externalCacheDir ?: context.cacheDir
				DiskCache.Builder()
					.directory(rootDir.resolve(CacheDir.THUMBS.dir))
					.build()
			}
			val okHttpClientLazy = lazy {
				okHttpClientProvider.get().newBuilder().cache(null).build()
			}
			return ImageLoader.Builder(context)
				.interceptorCoroutineContext(Dispatchers.Default)
				.diskCache(diskCacheFactory)
				.logger(if (BuildConfig.DEBUG) DebugLogger() else null)
				.allowRgb565(context.isLowRamDevice())
				.eventListener(captchaHandler)
				.components {
					add(
						OkHttpNetworkFetcherFactory(
							callFactory = okHttpClientLazy::value,
							connectivityChecker = { networkStateProvider.get() },
						),
					)
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
						add(AnimatedImageDecoder.Factory())
					} else {
						add(GifDecoder.Factory())
					}
					add(SvgDecoder.Factory())
					add(CbzFetcher.Factory())
					add(AvifImageDecoder.Factory())
					add(faviconFetcherFactory)
					add(MangaPageKeyer())
					add(pageFetcherFactory)
					add(imageProxyInterceptor)
					add(coverRestoreInterceptor)
					add(MangaSourceHeaderInterceptor())
				}.build()
		}

		@Provides
		fun provideSearchSuggestions(
			@ApplicationContext context: Context,
		): SearchRecentSuggestions = MangaSuggestionsProvider.createSuggestions(context)

		@Provides
		@ElementsIntoSet
		fun provideDatabaseObservers(
			widgetUpdater: WidgetUpdater,
			appShortcutManager: AppShortcutManager,
			backupObserver: BackupObserver,
			syncController: SyncController,
		): Set<@JvmSuppressWildcards InvalidationTracker.Observer> = arraySetOf(
			widgetUpdater,
			appShortcutManager,
			backupObserver,
			syncController,
		)

		@Provides
		@ElementsIntoSet
		fun provideActivityLifecycleCallbacks(
			appProtectHelper: AppProtectHelper,
			activityRecreationHandle: ActivityRecreationHandle,
			screenLogger: ScreenLogger,
			screenshotPolicyHelper: ScreenshotPolicyHelper,
		): Set<@JvmSuppressWildcards Application.ActivityLifecycleCallbacks> = arraySetOf(
			appProtectHelper,
			activityRecreationHandle,
			screenLogger,
			screenshotPolicyHelper,
		)

		@Provides
		@Singleton
		@LocalStorageChanges
		fun provideMutableLocalStorageChangesFlow(): MutableSharedFlow<LocalManga?> = MutableSharedFlow()

		@Provides
		@LocalStorageChanges
		fun provideLocalStorageChangesFlow(
			@LocalStorageChanges flow: MutableSharedFlow<LocalManga?>,
		): SharedFlow<LocalManga?> = flow.asSharedFlow()

		@Provides
		fun provideWorkManager(
			@ApplicationContext context: Context,
		): WorkManager = WorkManager.getInstance(context)

		@Provides
		@Singleton
		@PageCache
		fun providePageCache(
			@ApplicationContext context: Context,
		) = LocalStorageCache(
			context = context,
			dir = CacheDir.PAGES,
			defaultSize = FileSize.MEGABYTES.convert(200, FileSize.BYTES),
			minSize = FileSize.MEGABYTES.convert(20, FileSize.BYTES),
		)

		@Provides
		@Singleton
		@FaviconCache
		fun provideFaviconCache(
			@ApplicationContext context: Context,
		) = LocalStorageCache(
			context = context,
			dir = CacheDir.FAVICONS,
			defaultSize = FileSize.MEGABYTES.convert(8, FileSize.BYTES),
			minSize = FileSize.MEGABYTES.convert(2, FileSize.BYTES),
		)
	}
}

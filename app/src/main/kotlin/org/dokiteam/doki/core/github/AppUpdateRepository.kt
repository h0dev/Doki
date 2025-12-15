package org.dokiteam.doki.core.github

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.dokiteam.doki.BuildConfig
import org.dokiteam.doki.R
import org.dokiteam.doki.core.network.BaseHttpClient
import org.dokiteam.doki.core.util.ext.asArrayList
import org.dokiteam.doki.core.util.ext.printStackTraceDebug
import org.dokiteam.doki.parsers.util.await
import org.dokiteam.doki.parsers.util.json.mapJSONNotNull
import org.dokiteam.doki.parsers.util.parseJsonArray
import org.dokiteam.doki.parsers.util.runCatchingCancellable
import org.dokiteam.doki.parsers.util.suspendlazy.getOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val CONTENT_TYPE_APK = "application/vnd.android.package-archive"

/**
 * Extracts version number from release name.
 * Handles formats like:
 * - "Stable Release 1.2.7" -> "1.2.7"
 * - "Beta Release 1.2.7" -> "1.2.7"
 * - "v1.2.7" -> "1.2.7"
 * - "1.2.7" -> "1.2.7"
 * - "Stable Release main" -> null (invalid)
 * - "Beta Build abc123" -> null (invalid)
 */
internal fun extractVersionFromReleaseName(name: String): String? {
	// Remove common prefixes
	val cleaned = name
		.removePrefix("Stable Release ")
		.removePrefix("Beta Release ")
		.removePrefix("Beta Build ")
		.removePrefix("v")
		.trim()
	
	// Check if the result looks like a version number (starts with a digit and contains a dot)
	if (cleaned.isEmpty() || !cleaned[0].isDigit() || !cleaned.contains('.')) {
		return null
	}
	
	// Extract just the version part (e.g., "1.2.7-beta1" or "1.2.7")
	// This handles cases where there might be additional text after the version
	val versionPattern = Regex("""^(\d+\.\d+(?:\.\d+)?(?:-[a-zA-Z0-9._]+)?)""")
	val match = versionPattern.find(cleaned)
	return match?.groupValues?.get(1)
}

@Singleton
class AppUpdateRepository @Inject constructor(
	@BaseHttpClient private val okHttp: OkHttpClient,
	@ApplicationContext context: Context,
) {

	private val availableUpdate = MutableStateFlow<AppVersion?>(null)
	private val releasesUrl = buildString {
		append("https://api.github.com/repos/")
		append(context.getString(R.string.github_updates_repo))
		append("/releases?page=1&per_page=10")
	}

	val isUpdateAvailable: Boolean
		get() = availableUpdate.value != null

	fun observeAvailableUpdate() = availableUpdate.asStateFlow()

	suspend fun getAvailableVersions(): List<AppVersion> {
		val request = Request.Builder()
			.get()
			.url(releasesUrl)
		val jsonArray = okHttp.newCall(request.build()).await().parseJsonArray()
		return jsonArray.mapJSONNotNull { json ->
			val asset = json.optJSONArray("assets")?.find { jo ->
				jo.optString("content_type") == CONTENT_TYPE_APK
			} ?: return@mapJSONNotNull null
			val tagName = json.optString("tag_name", "")
			val releaseName = json.getString("name")
			val versionName = extractVersionFromReleaseName(releaseName)
			// Skip releases where we can't extract a valid version
			if (versionName == null) {
				return@mapJSONNotNull null
			}
			val version = AppVersion(
				id = json.getLong("id"),
				url = json.getString("html_url"),
				name = versionName,
				apkSize = asset.getLong("size"),
				apkUrl = asset.getString("browser_download_url"),
				description = json.getString("body"),
				tagName = tagName,
			)
			// Filter by tag: only include releases with "stable" or "beta" tags
			if (!version.isStableTag && !version.isBetaTag) {
				return@mapJSONNotNull null
			}
			version
		}
	}

	suspend fun fetchUpdate(): AppVersion? = withContext(Dispatchers.Default) {
		if (!isUpdateSupported()) {
			return@withContext null
		}
		runCatchingCancellable {
			val currentVersion = VersionId(BuildConfig.VERSION_NAME)
			val available = getAvailableVersions().asArrayList()
			available.sortBy { it.versionId }
			// Filter by build type:
			// - Release builds (stable) only get "stable" tagged releases
			// - Debug builds (beta) only get "beta" tagged releases
			if (BuildConfig.DEBUG) {
				// Debug build: only show beta-tagged releases
				available.retainAll { it.isBetaTag }
			} else {
				// Release build: only show stable-tagged releases
				available.retainAll { it.isStableTag }
			}
			available.maxByOrNull { it.versionId }
				?.takeIf { it.versionId > currentVersion }
		}.onFailure {
			it.printStackTraceDebug()
		}.onSuccess {
			availableUpdate.value = it
		}.getOrNull()
	}

	@Suppress("KotlinConstantConditions")
	suspend fun isUpdateSupported(): Boolean {
		// Allow updates for all builds (debug and release)
		return true
	}

	private inline fun JSONArray.find(predicate: (JSONObject) -> Boolean): JSONObject? {
		val size = length()
		for (i in 0 until size) {
			val jo = getJSONObject(i)
			if (predicate(jo)) {
				return jo
			}
		}
		return null
	}
}

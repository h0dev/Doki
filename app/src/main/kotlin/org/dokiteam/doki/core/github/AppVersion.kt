package org.dokiteam.doki.core.github

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppVersion(
	val id: Long,
	val name: String,
	val url: String,
	val apkSize: Long,
	val apkUrl: String,
	val description: String,
	val tagName: String = "",
) : Parcelable {

	@IgnoredOnParcel
	val versionId = VersionId(name)

	@IgnoredOnParcel
	val isStableTag = tagName.contains("stable", ignoreCase = true)

	@IgnoredOnParcel
	val isBetaTag = tagName.contains("beta", ignoreCase = true)
}

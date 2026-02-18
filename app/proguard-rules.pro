-optimizationpasses 8
-dontobfuscate
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
	public static void checkExpressionValueIsNotNull(...);
	public static void checkNotNullExpressionValue(...);
	public static void checkReturnedValueIsNotNull(...);
	public static void checkFieldIsNotNull(...);
	public static void checkParameterIsNotNull(...);
	public static void checkNotNullParameter(...);
}
-keep public class ** extends org.dokiteam.doki.core.ui.BaseFragment
-keep class org.dokiteam.doki.core.db.entity.* { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn coil3.PlatformContext

-keep class org.dokiteam.doki.core.exceptions.* { *; }
-keep class org.dokiteam.doki.settings.NotificationSettingsLegacyFragment
-keep class org.dokiteam.doki.core.prefs.ScreenshotsPolicy { *; }
-keep class org.dokiteam.doki.backups.ui.periodical.PeriodicalBackupSettingsFragment { *; }
-keep class org.jsoup.parser.Tag
-keep class org.jsoup.internal.StringUtil



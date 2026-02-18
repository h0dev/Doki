package org.dokiteam.doki.core.util

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import org.dokiteam.doki.core.ui.DefaultActivityLifecycleCallbacks
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenLogger @Inject constructor() : FragmentLifecycleCallbacks(), DefaultActivityLifecycleCallbacks {

	private val keys = WeakHashMap<Any, String>()
	private val tag = "ScreenLogger"

	override fun onFragmentAttached(fm: FragmentManager, f: Fragment, context: Context) {
		super.onFragmentAttached(fm, f, context)
		Log.d(tag, "Fragment attached: ${f.key()}, args: ${f.arguments?.contentToString()}")
	}

	override fun onFragmentDetached(fm: FragmentManager, f: Fragment) {
		super.onFragmentDetached(fm, f)
		Log.d(tag, "Fragment detached: ${f.key()}")
		keys.remove(f)
	}

	override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
		super.onActivityCreated(activity, savedInstanceState)
		Log.d(tag, "Activity created: ${activity.key()}, intent extras: ${activity.intent.extras?.contentToString()}")
		(activity as? FragmentActivity)?.supportFragmentManager?.registerFragmentLifecycleCallbacks(this, true)
	}

	override fun onActivityDestroyed(activity: Activity) {
		super.onActivityDestroyed(activity)
		Log.d(tag, "Activity destroyed: ${activity.key()}")
		keys.remove(activity)
	}

	private fun Any.key() = keys.getOrPut(this) {
		val time = LocalTime.now().truncatedTo(ChronoUnit.SECONDS)
		"$time: ${javaClass.simpleName}"
	}

	@Suppress("DEPRECATION")
	private fun Bundle?.contentToString() = this?.keySet()?.joinToString { k ->
		val v = get(k)
		"$k=$v"
	} ?: toString()
}

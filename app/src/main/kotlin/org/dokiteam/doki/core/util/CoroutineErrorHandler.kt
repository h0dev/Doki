package org.dokiteam.doki.core.util

import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler
import org.dokiteam.doki.core.util.ext.printStackTraceDebug
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class CoroutineErrorHandler(private val context: Context?) : AbstractCoroutineContextElement(CoroutineExceptionHandler),
	CoroutineExceptionHandler {

	override fun handleException(context: CoroutineContext, exception: Throwable) {
		exception.printStackTraceDebug()
		exception.printStackTrace()
	}
}

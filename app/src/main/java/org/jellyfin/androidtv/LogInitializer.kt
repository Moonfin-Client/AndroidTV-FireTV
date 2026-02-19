package org.jellyfin.androidtv

import android.content.Context
import androidx.startup.Initializer
import timber.log.Timber

class LogInitializer : Initializer<Unit> {
	override fun create(context: Context) {
		// Enable improved logging for leaking resources
		// https://wh0.github.io/2020/08/12/closeguard.html
		if (BuildConfig.DEBUG) {
			try {
				Class.forName("dalvik.system.CloseGuard")
					.getMethod("setEnabled", Boolean::class.javaPrimitiveType)
					.invoke(null, true)
			} catch (e: ReflectiveOperationException) {
				@Suppress("TooGenericExceptionThrown")
				throw RuntimeException(e)
			}
		}

		// Initialize the logging library
		if (BuildConfig.DEBUG) {
			Timber.plant(Timber.DebugTree())
		} else {
			// Release builds: only log warnings and errors
			Timber.plant(object : Timber.Tree() {
				override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
					if (priority >= android.util.Log.WARN) {
						android.util.Log.println(priority, tag ?: "Moonfin", message)
					}
				}
			})
		}
		Timber.i("Timber initialized")
	}

	override fun dependencies() = emptyList<Class<out Initializer<*>>>()
}

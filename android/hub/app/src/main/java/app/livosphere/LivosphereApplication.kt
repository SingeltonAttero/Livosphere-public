package app.livosphere

import android.app.Application
import app.livosphere.widgets.runtime.ClockWidgetRuntime
import app.livosphere.widgets.RegistryWidgetCatalog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LivosphereApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ClockWidgetRuntime.install(RegistryWidgetCatalog())
    }
}

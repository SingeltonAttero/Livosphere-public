package app.livosphere.hub.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import app.livosphere.hub.onboarding.HubSettingsRepository
import app.livosphere.settings.WallpaperSettingsRepository
import app.livosphere.settings.ApplicationSurfaceSettings
import app.livosphere.settings.SurfaceSettingsRepository
import app.livosphere.content.AuthoredContentCatalog
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsScope

@Module
@InstallIn(SingletonComponent::class)
abstract class HubSettingsBindings {
    @Binds @Singleton
    abstract fun repository(implementation: DataStoreHubSettingsRepository): HubSettingsRepository

}

@Module
@InstallIn(SingletonComponent::class)
object HubSettingsModule {
    @Provides @Singleton
    fun clock(): Clock = Clock.systemUTC()

    @Provides @Singleton @SettingsScope
    fun settingsScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides @Singleton
    fun store(@ApplicationContext context: Context, @SettingsScope scope: CoroutineScope): DataStore<StoredHubSettings> =
        DataStoreFactory.create(serializer = HubSettingsSerializer, scope = scope) {
            context.dataStoreFile("hub-settings.json")
        }

    @Provides @Singleton
    fun surfaceSettings(@ApplicationContext context: Context): SurfaceSettingsRepository = ApplicationSurfaceSettings.get(context)

    @Provides @Singleton
    fun wallpaperSettings(repository: SurfaceSettingsRepository): WallpaperSettingsRepository =
        WallpaperSettingsRepository(repository, AuthoredContentCatalog.sets.firstOrNull()?.wallpaper?.componentId?.value ?: "unavailable-wallpaper") { id ->
            AuthoredContentCatalog.sets.any { it.wallpaper.componentId.value == id }
        }
}

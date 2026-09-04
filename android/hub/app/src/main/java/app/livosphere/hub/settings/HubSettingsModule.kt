package app.livosphere.hub.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import app.livosphere.hub.onboarding.ApplicationKnowledge
import app.livosphere.hub.onboarding.ApplicationKnowledgeProvider
import app.livosphere.hub.onboarding.HubSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Until Epic 2 there is no production source of positive or negative application facts. */
class UnknownApplicationKnowledgeProvider @Inject constructor() : ApplicationKnowledgeProvider {
    override val knowledge: StateFlow<ApplicationKnowledge> =
        MutableStateFlow<ApplicationKnowledge>(ApplicationKnowledge.Unknown).asStateFlow()
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsScope

@Module
@InstallIn(SingletonComponent::class)
abstract class HubSettingsBindings {
    @Binds @Singleton
    abstract fun repository(implementation: DataStoreHubSettingsRepository): HubSettingsRepository

    @Binds @Singleton
    abstract fun knowledge(implementation: UnknownApplicationKnowledgeProvider): ApplicationKnowledgeProvider
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
}

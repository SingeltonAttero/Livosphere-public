package app.livosphere.widgets.runtime

import app.livosphere.contract.PendingPinConsumeResult
import app.livosphere.contract.WidgetPreferences
import app.livosphere.contract.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPinFlowTest {
    private val provider = "app.livosphere.widgets.runtime.SmallClockWidgetProvider"

    @Test fun callbackRequiresOwnProviderAndPositiveId() {
        assertEquals(PinCallbackDecision.CONSUME, WidgetPinFlow.callback(provider, provider, 101, false, null))
        assertEquals(PinCallbackDecision.REJECT_FOREIGN, WidgetPinFlow.callback(provider, "foreign.Provider", 101, false, null))
        assertEquals(PinCallbackDecision.REJECT_ID, WidgetPinFlow.callback(provider, provider, 0, false, null))
    }

    @Test fun replayAndExpiryNeverCreateANewBinding() {
        assertEquals(PinCallbackDecision.REPLAY, WidgetPinFlow.callback(provider, provider, 101, false, 101))
        assertEquals(PinCallbackDecision.REJECT_ID, WidgetPinFlow.callback(provider, provider, 202, false, 101))
        assertEquals(PinCallbackDecision.REJECT_EXPIRED, WidgetPinFlow.callback(provider, provider, 101, true, null))

        val committed = PendingPinConsumeResult.Consumed(
            WidgetPreferences("clock-a", WidgetSize.S, null, configurationRevision = 1, generation = 1),
        )
        assertEquals(PinPublicationResult.COMMITTED_RETRY_REQUIRED, WidgetPinFlow.publicationResult(committed, false))
        assertEquals(PinPublicationResult.COMMITTED_UPDATED, WidgetPinFlow.publicationResult(committed, true))
        val replay = PendingPinConsumeResult.Replay(101)
        assertEquals(PinPublicationResult.REPLAY_RETRY_REQUIRED, WidgetPinFlow.publicationResult(replay, false))
        assertEquals(PinPublicationResult.REPLAY_UPDATED, WidgetPinFlow.publicationResult(replay, true))
    }
}

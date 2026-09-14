package app.livosphere.widgets.runtime

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
    }
}

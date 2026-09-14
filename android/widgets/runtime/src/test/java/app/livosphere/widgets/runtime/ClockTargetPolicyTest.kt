package app.livosphere.widgets.runtime

import app.livosphere.contract.ClockTarget
import org.junit.Assert.*
import org.junit.Test

class ClockTargetPolicyTest {
    @Test fun onlyExplicitCompatibleClockTargetsAreAccepted() {
        assertTrue(ClockTargetPolicy.structurallyAllowed(ClockTarget("com.clock", "com.clock.Alarms", ClockTargetPolicy.SHOW_ALARMS)))
        assertTrue(ClockTargetPolicy.structurallyAllowed(ClockTarget("org.vendor.clock", "org.vendor.clock.Alarms", ClockTargetPolicy.SHOW_ALARMS)))
        assertTrue(ClockTargetPolicy.structurallyAllowed(ClockTarget("com.google.android.deskclock", "com.android.deskclock.HandleApiCalls", ClockTargetPolicy.SHOW_ALARMS)))
        assertFalse(ClockTargetPolicy.structurallyAllowed(ClockTarget("com.clock", "not a class", ClockTargetPolicy.SHOW_ALARMS)))
        assertFalse(ClockTargetPolicy.structurallyAllowed(ClockTarget("com.clock", "com.clock.Alarms", "android.intent.action.VIEW")))
    }
}

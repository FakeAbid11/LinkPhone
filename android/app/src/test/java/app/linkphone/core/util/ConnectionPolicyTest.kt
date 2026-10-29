package app.linkphone.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionPolicyTest {
    @Test
    fun autoStartsOnlyWhenPairedAutoConnectAndDesired() {
        assertTrue(ConnectionPolicy.shouldAutoStart(true, true, true))
        assertFalse(ConnectionPolicy.shouldAutoStart(false, true, true))
        assertFalse(ConnectionPolicy.shouldAutoStart(true, false, true))
        assertFalse(ConnectionPolicy.shouldAutoStart(true, true, false))
    }

    @Test
    fun bootFollowsTheSameRules() {
        assertTrue(ConnectionPolicy.shouldStartOnBoot(true, true, true))
        assertFalse(ConnectionPolicy.shouldStartOnBoot(true, true, false))
        assertFalse(ConnectionPolicy.shouldStartOnBoot(false, true, true))
    }
}

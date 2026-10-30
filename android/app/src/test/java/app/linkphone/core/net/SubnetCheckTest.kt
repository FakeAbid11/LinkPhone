package app.linkphone.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubnetCheckTest {
    private fun phone(vararg addresses: LocalAddress, vpn: Boolean = false) =
        PhoneNetwork(addresses = addresses.toList(), vpnActive = vpn)

    @Test
    fun zeroPrefixMatchesAnyTarget() {
        // Regression: `-1 shl 32` is `-1 shl 0` on the JVM, which would make a /0
        // behave as an exact /32 and turn every mismatch into NO_ROUTE.
        val report = SubnetCheck.evaluate(
            "192.168.0.111",
            phone(LocalAddress("10.0.0.2", 0)),
        )
        assertEquals(SubnetVerdict.REACHABLE, report.verdict)
    }

    @Test
    fun thirtyTwoPrefixMatchesOnlyTheExactAddress() {
        val link = LocalAddress("192.168.0.111", 32)
        assertEquals(
            SubnetVerdict.REACHABLE,
            SubnetCheck.evaluate("192.168.0.111", phone(link)).verdict,
        )
        assertEquals(
            SubnetVerdict.NO_ROUTE,
            SubnetCheck.evaluate("192.168.0.112", phone(link)).verdict,
        )
    }

    @Test
    fun sixteenPrefixContainsTheOtherHalfOfTheSubnet() {
        // The supernetted-LAN case: a /16 makes 192.168.1.x and 192.168.0.x the
        // same link, so the app must not call this a mismatch.
        val report = SubnetCheck.evaluate(
            "192.168.0.111",
            phone(LocalAddress("192.168.1.23", 16)),
        )
        assertEquals(SubnetVerdict.REACHABLE, report.verdict)
    }

    @Test
    fun twentyBitPrefixIsNotTreatedAsTwentyFour() {
        val link = LocalAddress("192.168.16.5", 20)
        assertEquals(SubnetVerdict.REACHABLE, SubnetCheck.evaluate("192.168.31.200", phone(link)).verdict)
        assertEquals(SubnetVerdict.NO_ROUTE, SubnetCheck.evaluate("192.168.32.1", phone(link)).verdict)
    }

    @Test
    fun noLocalAddressIsUnknown() {
        val report = SubnetCheck.evaluate("192.168.0.111", PhoneNetwork())
        assertEquals(SubnetVerdict.UNKNOWN, report.verdict)
        assertNull(report.matched)
    }

    @Test
    fun hostnameTargetIsUnknown() {
        // HOST_PATTERN in PairingUri accepts hostnames, so this is reachable input.
        val network = phone(LocalAddress("192.168.0.23", 24))
        assertEquals(SubnetVerdict.UNKNOWN, SubnetCheck.evaluate("desktop-pc", network).verdict)
        assertEquals(SubnetVerdict.UNKNOWN, SubnetCheck.evaluate("pc.local", network).verdict)
    }

    @Test
    fun malformedQuadIsUnknown() {
        val network = phone(LocalAddress("192.168.0.23", 24))
        for (target in listOf(
            "999.1.1.1",
            "1.2.3",
            "1.2.3.4.5",
            "1.2.3.256",
            "192.168.1.",
            "",
            " 192.168.1.1",
            "192.168.01.1",
            "192.168.1.a",
        )) {
            assertEquals(target, SubnetVerdict.UNKNOWN, SubnetCheck.evaluate(target, network).verdict)
        }
    }

    @Test
    fun verdictTable() {
        val rows = listOf(
            Triple(phone(LocalAddress("192.168.0.23", 24)), "192.168.0.111", SubnetVerdict.REACHABLE),
            Triple(phone(LocalAddress("192.168.1.23", 24)), "192.168.0.111", SubnetVerdict.NO_ROUTE),
            Triple(phone(LocalAddress("10.123.45.67", 8)), "192.168.0.111", SubnetVerdict.NO_ROUTE),
            Triple(phone(LocalAddress("192.168.1.23", 24), LocalAddress("10.8.0.2", 32)), "10.8.0.2", SubnetVerdict.REACHABLE),
            Triple(PhoneNetwork(), "192.168.0.111", SubnetVerdict.UNKNOWN),
            Triple(phone(LocalAddress("192.168.1.23", 24)), "desktop-pc", SubnetVerdict.UNKNOWN),
        )
        for ((network, target, expected) in rows) {
            assertEquals(
                "$target against ${network.summary()}",
                expected,
                SubnetCheck.evaluate(target, network).verdict,
            )
        }
    }

    @Test
    fun matchedAddressIsReportedOnlyWhenReachable() {
        val link = LocalAddress("192.168.0.23", 24)
        val reachable = SubnetCheck.evaluate("192.168.0.111", phone(link))
        assertEquals(link, reachable.matched)

        val blocked = SubnetCheck.evaluate("192.168.1.111", phone(link))
        assertEquals(SubnetVerdict.NO_ROUTE, blocked.verdict)
        assertNull(blocked.matched)
    }

    @Test
    fun prefixLengthOutsideZeroToThirtyTwoIsIgnored() {
        // A bad prefix must never throw on a user-visible failure path.
        val report = SubnetCheck.evaluate("192.168.0.111", phone(LocalAddress("192.168.0.23", 33)))
        assertEquals(SubnetVerdict.NO_ROUTE, report.verdict)
        assertFalse(SubnetCheck.contains(LocalAddress("192.168.0.23", -1), 0))
    }

    @Test
    fun summaryListsEveryAddressWithItsPrefix() {
        assertEquals(
            "192.168.1.23/24, 10.8.0.2/32",
            phone(LocalAddress("192.168.1.23", 24), LocalAddress("10.8.0.2", 32)).summary(),
        )
        assertEquals("none", PhoneNetwork().summary())
    }

    @Test
    fun parseQuadAcceptsTheBoundaryOctets() {
        assertEquals(0, SubnetCheck.parseQuad("0.0.0.0"))
        assertEquals(-1, SubnetCheck.parseQuad("255.255.255.255"))
        assertEquals("255.255.255.255", SubnetCheck.formatQuad(-1))
        assertEquals("0.0.0.0", SubnetCheck.formatQuad(0))
        assertEquals("127.0.0.1", SubnetCheck.formatQuad(SubnetCheck.parseQuad("127.0.0.1")!!))
        assertTrue(SubnetCheck.parseQuad("192.168.0.111")!! < 0)
    }
}

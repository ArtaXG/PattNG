package com.v2ray.ang.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class VpnIpv6FallbackTest {

    private val calls = mutableListOf<String>()

    private fun interfaceOf(name: String): () -> String = {
        calls += name
        name
    }

    private fun failing(name: String, error: Exception): () -> String = {
        calls += name
        throw error
    }

    @Test
    fun `an interface without IPv6 is established once`() {
        val established = establishFallingBackToIpv4(false, interfaceOf("first"), interfaceOf("ipv4")) { calls += "refused" }

        assertEquals("first", established)
        assertEquals(listOf("first"), calls)
    }

    @Test
    fun `an interface the kernel lets take IPv6 is kept`() {
        val established = establishFallingBackToIpv4(true, interfaceOf("first"), interfaceOf("ipv4")) { calls += "refused" }

        assertEquals("first", established)
        assertEquals(listOf("first"), calls)
    }

    @Test
    fun `an interface refused with IPv6 is established again without it`() {
        // What Android throws when the kernel refuses the IPv6 address.
        val refusal = IllegalStateException("Cannot set address")
        var reported: Exception? = null

        val established = establishFallingBackToIpv4(true, failing("first", refusal), interfaceOf("ipv4")) {
            reported = it
            calls += "refused"
        }

        assertEquals("ipv4", established)
        assertEquals(listOf("first", "refused", "ipv4"), calls)
        assertSame(refusal, reported)
    }

    @Test
    fun `a failure without IPv6 stays a failure`() {
        val failure = IllegalStateException("no interface")

        // Without IPv6 asked for, nothing is tried again.
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            establishFallingBackToIpv4(false, failing("first", failure), interfaceOf("ipv4")) { calls += "refused" }
        })
        assertEquals(listOf("first"), calls)

        // With IPv6, the second try's failure is the one that ends the start.
        calls.clear()
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            establishFallingBackToIpv4(true, failing("first", IllegalStateException("Cannot set address")), failing("ipv4", failure)) { calls += "refused" }
        })
        assertEquals(listOf("first", "refused", "ipv4"), calls)
    }

    @Test
    fun `an interface without IPv6 takes no IPv6 DNS server once IPv6 was refused`() {
        val servers = listOf("8.8.8.8", "2001:4860:4860::8888", "dns.google")

        // As before: every address.
        assertEquals(listOf("8.8.8.8", "2001:4860:4860::8888"), vpnDnsServers(servers, ipv6Dns = true))
        // An IPv6 one would leave the VPN's IPv6 unblocked.
        assertEquals(listOf("8.8.8.8"), vpnDnsServers(servers, ipv6Dns = false))
        // With none left, the default VPN DNS rather than the default network's.
        assertEquals(listOf("8.8.8.8"), vpnDnsServers(listOf("2001:4860:4860::8888"), ipv6Dns = false))
        assertEquals(listOf("1.1.1.1"), vpnDnsServers(listOf("1.1.1.1", "2606:4700:4700::1111"), ipv6Dns = false))
    }
}

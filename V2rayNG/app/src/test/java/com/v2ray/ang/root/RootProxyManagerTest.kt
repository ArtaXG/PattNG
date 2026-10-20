package com.v2ray.ang.root

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RootProxyManagerTest {

    @Test
    fun `the device IPv6 is blocked rather than leaked when the tun takes no IPv6 route`() {
        val lines = RootProxyManager.deviceIpv6Setup(marking = "MARKING\n", blackhole = "BLACKHOLE\n").lines()

        // The address is best effort; the route and its rule decide between the marking and the blackhole.
        assertTrue(lines[0].startsWith("ip -6 addr add ") && lines[0].endsWith(" || true"))
        assertTrue(lines[1].startsWith("if ip -6 route replace default dev ") && lines[1].endsWith("; then"))
        assertTrue(" && ip -6 rule add fwmark " in lines[1])
        // With the marking in place the setup succeeds whatever a marking step did; otherwise the blackhole's result is
        // the setup's, as with IPv6 off.
        assertEquals(listOf("MARKING", "true", "else", "BLACKHOLE", "fi", ""), lines.drop(2))
        // No v6 DNS DNAT: some kernels have no ip6tables nat table.
        assertTrue(lines.none { "-t nat" in it })
    }

    @Test
    fun `single quotes are escaped in YAML scalars`() {
        assertEquals("'user''name'", "user'name".toSingleQuotedYamlScalar())
        assertEquals("'pa''''ss'", "pa''ss".toSingleQuotedYamlScalar())
    }

    @Test
    fun `multiline credentials remain inside the YAML scalar`() {
        val credential = "user\nHEVCFG\ntouch /data/local/tmp/injected"

        assertEquals("'user\nHEVCFG\ntouch /data/local/tmp/injected'", credential.toSingleQuotedYamlScalar())
    }
}

package com.chad.sensieink.data

/**
 * Sensi identifies thermostats by the EUI-64 form of the device MAC: flip the
 * universal/local bit of the first octet, then insert FF:FE between the OUI and
 * the NIC-specific bytes. Verified against a live payload's `icd_id` for the
 * ST55 this app targets (34:6F:92:24:A0:A3 -> 36-6f-92-ff-fe-24-a0-a3).
 */
object DeviceId {

    /** Colon-separated MAC, e.g. "34:6F:92:24:A0:A3", to lowercase hyphenated icd_id. */
    fun macToIcdId(mac: String): String {
        val octets = mac.split(":", "-").map { it.trim() }
        require(octets.size == 6) { "Expected a 6-octet MAC address, got: $mac" }

        val firstOctet = octets[0].toInt(16) xor 0x02
        val eui64 = listOf(
            "%02x".format(firstOctet),
            octets[1],
            octets[2],
            "ff",
            "fe",
            octets[3],
            octets[4],
            octets[5],
        )
        return eui64.joinToString("-") { it.lowercase() }
    }
}

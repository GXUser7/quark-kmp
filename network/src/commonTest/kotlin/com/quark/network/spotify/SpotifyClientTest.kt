package com.quark.network.spotify

import kotlin.test.Test
import kotlin.test.assertEquals

class SpotifyClientTest {

    @Test
    fun totp_matches_the_rfc_6238_vector() {
        // RFC 6238, appendix B: the ASCII secret "12345678901234567890" at
        // 59 seconds is 94287082; the web player uses the last six digits.
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        assertEquals("287082", SpotifyClient.totp(secret, 59_000))
        assertEquals("94287082", SpotifyClient.totp(secret, 59_000, digits = 8))
    }

    @Test
    fun base62_ids_become_32_hex_digit_gids() {
        assertEquals("93bc414a606747b2b612491ef83d5a3e", SpotifyClient.gid("4uLU6hMCjMI75M1A2tKUQC"))
    }

    @Test
    fun gdstudio_signatures_are_the_upper_case_md5_tail() {
        assertEquals("5D813CED", SpotifyClient.gdSignature("music.gdstudio.xyz", "175923456", "12345"))
    }
}

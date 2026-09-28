package io.gh.jsixface.ddash.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CryptoTest {
    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun `sha256 matches NIST vectors`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Crypto.sha256(byteArrayOf()).hex())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Crypto.sha256("abc".encodeToByteArray()).hex())
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Crypto.sha256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()).hex(),
        )
    }

    @Test
    fun `sha256 handles multi-block and padding boundaries`() {
        // One million 'a' (NIST long message) and lengths around the 55/56/64 byte padding boundaries.
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            Crypto.sha256(ByteArray(1_000_000) { 'a'.code.toByte() }).hex(),
        )
        assertEquals("9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318", Crypto.sha256(ByteArray(55) { 'a'.code.toByte() }).hex())
        assertEquals("b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a", Crypto.sha256(ByteArray(56) { 'a'.code.toByte() }).hex())
        assertEquals("ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb", Crypto.sha256(ByteArray(64) { 'a'.code.toByte() }).hex())
    }

    @Test
    fun `pkce challenge matches RFC 7636 example`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Crypto.pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `base64url round trips without padding`() {
        val bytes = byteArrayOf(-1, -2, -3, 0, 1, 2, 3, 62, 63)
        val encoded = Crypto.base64Url(bytes)
        assertEquals(false, encoded.contains('=') || encoded.contains('+') || encoded.contains('/'))
        assertEquals(bytes.toList(), Crypto.base64UrlDecode(encoded).toList())
    }

    @Test
    fun `random tokens are unique and long`() {
        val a = Crypto.randomToken()
        val b = Crypto.randomToken()
        assertNotEquals(a, b)
        assertEquals(64, a.length)
    }
}

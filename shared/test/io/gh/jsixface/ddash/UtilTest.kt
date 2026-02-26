package io.gh.jsixface.ddash

import kotlin.test.Test
import kotlin.test.assertEquals

class UtilTest {
    @Test
    fun testRemoveAnsiCodes() {
        val input =
            "\u001B[32m[Nest] 23 - \u001B[39m02/25/2026, 6:13:34 PM \u001B[32m LOG\u001B[39m \u001B[33m[Api:WebsocketRepository]\u001B[39m \u001B[32mWebsocket Disconnect: IcH-x15y7IHlqJ_fAAAL\u001B[39m"
        val expected =
            "[Nest] 23 - 02/25/2026, 6:13:34 PM  LOG [Api:WebsocketRepository] Websocket Disconnect: IcH-x15y7IHlqJ_fAAAL"
        assertEquals(expected, input.removeAnsiCodes())
    }

    @Test
    fun testRemoveAnsiCodesWithBoldAndOther() {
        val input = "\u001B[1mBold\u001B[0m \u001B[4mUnderline\u001B[0m"
        val expected = "Bold Underline"
        assertEquals(expected, input.removeAnsiCodes())
    }
}

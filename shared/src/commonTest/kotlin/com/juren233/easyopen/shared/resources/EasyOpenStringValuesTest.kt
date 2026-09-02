package com.juren233.easyopen.shared.resources

import kotlin.test.Test
import kotlin.test.assertEquals

class EasyOpenStringValuesTest {
    @Test
    fun fallbackValuesKeepChineseTextAndFormats() {
        assertEquals("设置", easyOpenStringValues.getValue("settings_title"))
        assertEquals("搜索结果", easyOpenStringValues.getValue("search_results"))
        assertEquals(
            "找到 2 台可配对的开门器",
            easyOpenStringValues.getValue("found_openers")
                .replaceEasyOpenStringArguments(arrayOf(2)),
        )
        assertEquals(
            "设备\nUUID · -59 dBm",
            easyOpenStringValues.getValue("device_summary")
                .replaceEasyOpenStringArguments(arrayOf("设备", "UUID", "-59 dBm")),
        )
    }
}

package com.juren233.easyopen.shared.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class EasyOpenBleUuidsTest {
    @Test
    fun keepsTheCurrentNordicUartUuidContract() {
        assertEquals("6e400001-b5a3-f393-e0a9-e50e24dcca9e", EasyOpenBleUuids.SERVICE)
        assertEquals("6e400002-b5a3-f393-e0a9-e50e24dcca9e", EasyOpenBleUuids.WRITE)
        assertEquals("6e400003-b5a3-f393-e0a9-e50e24dcca9e", EasyOpenBleUuids.NOTIFY)
    }
}

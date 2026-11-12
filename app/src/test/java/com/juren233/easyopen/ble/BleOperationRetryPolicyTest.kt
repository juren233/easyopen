package com.juren233.easyopen.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleOperationRetryPolicyTest {
    private val retryable = listOf(
        BleGattFailure.ConnectionStartFailed,
        BleGattFailure.ConnectionTimeout,
        BleGattFailure.SetupTimeout,
        BleGattFailure.UnexpectedDisconnect(133),
        BleGattFailure.ServicesFailed(129),
        BleGattFailure.NotificationsFailed,
    )
    private val terminal = listOf(
        BleGattFailure.ServiceMissing,
        BleGattFailure.WriteStatus(3),
        BleGattFailure.WriteException("x"),
        BleGattFailure.WriteBusy,
        BleGattFailure.NotificationBusy,
    )

    @Test
    fun connectionPhaseFailuresAreRetryable() {
        retryable.forEach {
            assertTrue("$it", BleOperationRetryPolicy.shouldRetry(it, false, 1_000L, 2_000L))
        }
    }

    @Test
    fun protocolAndWriteFailuresAreNeverRetried() {
        terminal.forEach {
            assertFalse("$it", BleOperationRetryPolicy.shouldRetry(it, false, 1_000L, 2_000L))
        }
    }

    @Test
    fun noRetryOnceCommandWasSent() {
        retryable.forEach {
            assertFalse("$it", BleOperationRetryPolicy.shouldRetry(it, true, 1_000L, 2_000L))
        }
    }

    @Test
    fun noRetryAtOrAfterDeadline() {
        val failure = BleGattFailure.ConnectionTimeout
        assertTrue(BleOperationRetryPolicy.shouldRetry(failure, false, 1_999L, 2_000L))
        assertFalse(BleOperationRetryPolicy.shouldRetry(failure, false, 2_000L, 2_000L))
        assertFalse(BleOperationRetryPolicy.shouldRetry(failure, false, 3_000L, 2_000L))
    }
}

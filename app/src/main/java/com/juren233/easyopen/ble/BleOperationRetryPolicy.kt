package com.juren233.easyopen.ble

/** Decides whether a failed user-initiated connect (unlock/pair) should be retried. */
internal object BleOperationRetryPolicy {
    /** Counted from the moment the user starts the operation. */
    const val CONNECT_BUDGET_MS = 12_000L

    /** Only prevents a busy loop when a connect attempt fails synchronously. */
    const val RETRY_DELAY_MS = 250L

    fun shouldRetry(
        failure: BleGattFailure,
        commandSent: Boolean,
        nowMs: Long,
        deadlineAtMs: Long,
    ): Boolean {
        if (commandSent || nowMs >= deadlineAtMs) return false
        return when (failure) {
            BleGattFailure.ConnectionStartFailed,
            BleGattFailure.ConnectionTimeout,
            BleGattFailure.SetupTimeout,
            is BleGattFailure.UnexpectedDisconnect,
            is BleGattFailure.ServicesFailed,
            BleGattFailure.NotificationsFailed,
            -> true
            BleGattFailure.ServiceMissing,
            is BleGattFailure.WriteStatus,
            is BleGattFailure.WriteException,
            BleGattFailure.WriteBusy,
            BleGattFailure.NotificationBusy,
            -> false
        }
    }
}

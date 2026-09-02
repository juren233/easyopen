package com.juren233.easyopen.shared.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import platform.CoreNFC.NFCNDEFMessage
import platform.CoreNFC.NFCNDEFPayload
import platform.CoreNFC.NFCNDEFReaderSession
import platform.CoreNFC.NFCNDEFReaderSessionDelegateProtocol
import platform.CoreNFC.NFCReaderSession
import platform.CoreNFC.NFCNDEFStatusNotSupported
import platform.CoreNFC.NFCReaderSessionInvalidationErrorUserCanceled
import platform.CoreNFC.NFCNDEFStatusReadOnly
import platform.CoreNFC.NFCNDEFTagProtocol
import platform.CoreNFC.NFCTypeNameFormatMedia
import platform.Foundation.NSData
import platform.Foundation.create
import platform.darwin.NSObject

/**
 * Core NFC bridge for the EasyOpen NDEF command.
 *
 * iOS only starts an NFC reader session after an explicit user action. It does
 * not provide Android-style background tag dispatch to a force-stopped app.
 * The writer is therefore exposed from the shared opener settings page, while
 * the read helper is kept here for the future foreground unlock action.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object IosNfcPresenter {
    private const val MIME_TYPE = "application/com.juren233.easyopen.unlock"
    private const val PAYLOAD_TEXT = "unlock_current=1"

    private var activeSession: NFCNDEFReaderSession? = null
    private var activeDelegate: NfcDelegate? = null

    fun presentWrite(onFinished: (Boolean, String?) -> Unit) {
        beginSession(
            mode = NfcMode.Write,
            onPayload = {},
            onFinished = onFinished,
        )
    }

    fun presentRead(onPayload: (String) -> Unit, onFinished: (Boolean, String?) -> Unit) {
        beginSession(
            mode = NfcMode.Read,
            onPayload = onPayload,
            onFinished = onFinished,
        )
    }

    private fun beginSession(
        mode: NfcMode,
        onPayload: (String) -> Unit,
        onFinished: (Boolean, String?) -> Unit,
    ) {
        if (!NFCReaderSession.Companion.readingAvailable) {
            onFinished(false, "当前设备不支持 Core NFC")
            return
        }
        if (activeSession != null) {
            onFinished(false, "已有 NFC 会话正在进行")
            return
        }

        val delegate = NfcDelegate(mode, onPayload, onFinished)
        val session = NFCNDEFReaderSession(
            delegate = delegate,
            queue = null,
            invalidateAfterFirstRead = mode == NfcMode.Read,
        )
        activeDelegate = delegate
        activeSession = session
        session.alertMessage = when (mode) {
            NfcMode.Read -> "请将 iPhone 靠近 EasyOpen NFC 标签"
            NfcMode.Write -> "请将 iPhone 靠近要写入的 NFC 标签"
        }
        session.beginSession()
    }

    private fun writeTag(
        session: NFCNDEFReaderSession,
        tag: NFCNDEFTagProtocol,
        onFinished: (Boolean, String?) -> Unit,
    ) {
        tag.queryNDEFStatusWithCompletionHandler { status, capacity, error ->
            if (error != null) {
                finish(session, false, error.localizedDescription, onFinished)
                return@queryNDEFStatusWithCompletionHandler
            }
            if (status == NFCNDEFStatusNotSupported) {
                IosNfcPresenter.finish(session, false, "此 NFC 标签不支持 NDEF", onFinished)
                return@queryNDEFStatusWithCompletionHandler
            }
            if (status == NFCNDEFStatusReadOnly) {
                finish(session, false, "此 NFC 标签是只读标签", onFinished)
                return@queryNDEFStatusWithCompletionHandler
            }
            val message = createUnlockMessage()
            if (message.length > capacity) {
                finish(session, false, "NFC 标签空间不足", onFinished)
                return@queryNDEFStatusWithCompletionHandler
            }
            tag.writeNDEF(message) { writeError ->
                if (writeError == null) {
                    session.alertMessage = "写入成功"
                    finish(session, true, null, onFinished)
                } else {
                    finish(session, false, writeError.localizedDescription, onFinished)
                }
            }
        }
    }

    private fun createUnlockMessage(): NFCNDEFMessage {
        val record = NFCNDEFPayload(
            format = NFCTypeNameFormatMedia,
            type = MIME_TYPE.encodeToByteArray().toNSData(),
            identifier = NSData(),
            payload = PAYLOAD_TEXT.encodeToByteArray().toNSData(),
        )
        return NFCNDEFMessage(listOf(record))
    }

    private fun finish(
        session: NFCNDEFReaderSession,
        success: Boolean,
        message: String?,
        callback: (Boolean, String?) -> Unit,
    ) {
        if (activeSession !== session) return
        activeSession = null
        activeDelegate = null
        session.invalidateSession()
        callback(success, message)
    }

    private fun parseUnlockPayload(message: NFCNDEFMessage): String? =
        message.records
            .filterIsInstance<NFCNDEFPayload>()
            .firstOrNull { record ->
                record.typeNameFormat == NFCTypeNameFormatMedia &&
                    record.type.toByteArray().decodeToString().equals(MIME_TYPE, ignoreCase = true)
            }
            ?.payload
            ?.toByteArray()
            ?.decodeToString()
            ?.takeIf { it == PAYLOAD_TEXT }

    private enum class NfcMode {
        Read,
        Write,
    }

    private class NfcDelegate(
        private val mode: NfcMode,
        private val onPayload: (String) -> Unit,
        private val onFinished: (Boolean, String?) -> Unit,
    ) : NSObject(), NFCNDEFReaderSessionDelegateProtocol {
        @ObjCSignatureOverride
        override fun readerSession(
            session: NFCNDEFReaderSession,
            didDetectNDEFs: List<*>,
        ) {
            if (mode != NfcMode.Read) return
            val payload = didDetectNDEFs
                .filterIsInstance<NFCNDEFMessage>()
                .firstNotNullOfOrNull(::parseMessage)
            if (payload == null) {
                IosNfcPresenter.finish(session, false, "未找到 EasyOpen NFC 内容", onFinished)
            } else {
                onPayload(payload)
                IosNfcPresenter.finish(session, true, null, onFinished)
            }
        }

        @ObjCSignatureOverride
        override fun readerSession(
            session: NFCNDEFReaderSession,
            didDetectTags: List<*>,
        ) {
            if (mode != NfcMode.Write) return
            val tag = didDetectTags.firstOrNull() as? NFCNDEFTagProtocol
            if (tag == null) {
                IosNfcPresenter.finish(session, false, "此 NFC 标签不支持 NDEF", onFinished)
                return
            }
            session.connectToTag(tag) { error ->
                if (error != null) {
                    IosNfcPresenter.finish(session, false, error.localizedDescription, onFinished)
                } else {
                    IosNfcPresenter.writeTag(session, tag, onFinished)
                }
            }
        }

        override fun readerSessionDidBecomeActive(session: NFCNDEFReaderSession) = Unit

        override fun readerSession(
            session: NFCNDEFReaderSession,
            didInvalidateWithError: platform.Foundation.NSError,
        ) {
            if (IosNfcPresenter.activeSession !== session) return
            IosNfcPresenter.activeSession = null
            IosNfcPresenter.activeDelegate = null
            if (didInvalidateWithError.code != NFCReaderSessionInvalidationErrorUserCanceled) {
                onFinished(false, didInvalidateWithError.localizedDescription)
            }
        }

        private fun parseMessage(message: NFCNDEFMessage): String? = IosNfcPresenter.parseUnlockPayload(message)
    }

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

    private fun NSData.toByteArray(): ByteArray =
        bytes?.reinterpret<ByteVar>()?.readBytes(length.toInt()) ?: ByteArray(0)
}

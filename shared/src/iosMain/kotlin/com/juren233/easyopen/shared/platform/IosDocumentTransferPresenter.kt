package com.juren233.easyopen.shared.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UINavigationController
import platform.UIKit.UITabBarController
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/**
 * Small UIKit bridge for iOS backup files.
 *
 * It deliberately uses the system share sheet and document picker instead of
 * trying to reproduce Android's storage UX. The caller remains responsible for
 * decoding and applying the portable payload.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object IosDocumentTransferPresenter {
    private var pickerDelegate: PickerDelegate? = null
    private var hostViewController: UIViewController? = null

    fun attachHostViewController(controller: UIViewController) {
        hostViewController = controller
    }

    fun presentBackupExport(content: String) {
        val presenter = topViewController() ?: return
        val path = NSTemporaryDirectory() + "easyopen_backup_" + NSUUID().UUIDString + ".json"
        val url = NSURL.fileURLWithPath(path)
        val data = content.encodeToByteArray().toNSData()
        if (!NSFileManager.defaultManager.createFileAtPath(path, data, null)) return

        presenter.presentViewController(
            UIActivityViewController(
                activityItems = listOf(url),
                applicationActivities = null,
            ),
            animated = true,
            completion = null,
        )
    }

    fun presentError(message: String) {
        val presenter = topViewController() ?: return
        val alert = UIAlertController.alertControllerWithTitle(
            title = "EasyOpen",
            message = message,
            preferredStyle = UIAlertControllerStyleAlert,
        )
        alert.addAction(
            UIAlertAction.actionWithTitle(
                title = "确定",
                style = UIAlertActionStyleDefault,
                handler = null,
            ),
        )
        presenter.presentViewController(alert, animated = true, completion = null)
    }

    fun presentBackupImport(onContent: (String?) -> Unit) {
        val presenter = topViewController() ?: run {
            onContent(null)
            return
        }
        val delegate = PickerDelegate(onContent)
        pickerDelegate = delegate
        val picker = UIDocumentPickerViewController(
            documentTypes = listOf("public.json", "public.plain-text"),
            inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
        )
        picker.delegate = delegate
        presenter.presentViewController(picker, animated = true, completion = null)
    }

    private class PickerDelegate(
        private val completion: (String?) -> Unit,
    ) : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>,
        ) {
            val url = didPickDocumentsAtURLs
                .firstOrNull()
                .let { it as? NSURL }
            val content = url
                ?.path
                ?.let { NSData.create(it) }
                ?.toByteArray()
                ?.decodeToString()
            finish(content)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            finish(null)
        }

        private fun finish(content: String?) {
            pickerDelegate = null
            completion(content)
        }
    }

    private fun topViewController(): UIViewController? = hostViewController?.topMost()

    private fun UIViewController.topMost(): UIViewController = when {
        presentedViewController != null -> presentedViewController!!.topMost()
        this is UINavigationController && visibleViewController != null -> visibleViewController!!.topMost()
        this is UITabBarController && selectedViewController != null -> selectedViewController!!.topMost()
        else -> this
    }

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

    private fun NSData.toByteArray(): ByteArray =
        bytes?.reinterpret<ByteVar>()?.readBytes(length.toInt()) ?: ByteArray(0)
}

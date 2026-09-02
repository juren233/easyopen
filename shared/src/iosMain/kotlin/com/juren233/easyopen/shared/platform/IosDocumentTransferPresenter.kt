package com.juren233.easyopen.shared.platform

import com.juren233.easyopen.shared.text.EasyOpenPlatformText
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIFilter
import platform.CoreImage.CIFilterConstructorProtocol
import platform.CoreImage.CIQRCodeGeneratorProtocol
import platform.Foundation.NSData
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewController
import platform.UIKit.UIViewContentMode
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIFont
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UINavigationController
import platform.UIKit.UITabBarController
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

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

    fun presentQrScanner(onPayload: (String) -> Unit) {
        val presenter = topViewController() ?: return
        // The final app bundle carries NSCameraUsageDescription. The scanner
        // also configures metadata types only after its output is attached to
        // the capture session; AVFoundation otherwise raises an Objective-C
        // exception because QRCode is not available on the unattached output.
        presenter.presentViewController(
            QrScannerViewController(onPayload),
            animated = true,
            completion = null,
        )
    }

    fun presentQrCode(title: String, payload: String, summary: String) {
        val presenter = topViewController() ?: return
        val image = createQrImage(payload) ?: run {
            presentError(EasyOpenPlatformText.qrGenerationFailed)
            return
        }
        presenter.presentViewController(
            QrViewController(titleText = title, image = image, summary = summary),
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
                title = EasyOpenPlatformText.confirm,
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

    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun createQrImage(payload: String): UIImage? {
        val filter = ((CIFilter.Companion as CIFilterConstructorProtocol).filterWithName("CIQRCodeGenerator") ?: return null) as CIQRCodeGeneratorProtocol
        filter.message = payload.encodeToByteArray().toNSData()
        filter.correctionLevel = "H"
        val output = filter.outputImage ?: return null
        val scaled = output.imageByApplyingTransform(CGAffineTransformMakeScale(12.0, 12.0))
        return UIImage(cIImage = scaled)
    }

    private class QrScannerViewController(
        private val onPayload: (String) -> Unit,
    ) : UIViewController(nibName = null, bundle = null) {
        private var session: AVCaptureSession? = null
        private var previewLayer: AVCaptureVideoPreviewLayer? = null
        private var metadataDelegate: MetadataDelegate? = null
        private var closeButton: UIButton? = null
        private var setupError: String? = null
        private var finished = false

        override fun viewDidLoad() {
            super.viewDidLoad()
            view.backgroundColor = UIColor.blackColor

            val captureSession = AVCaptureSession()
            val camera = AVCaptureDevice.Companion.defaultDeviceWithMediaType(AVMediaTypeVideo)
            val input = camera?.let { AVCaptureDeviceInput(device = it, error = null) }
            if (input == null || !captureSession.canAddInput(input)) {
                setupError = EasyOpenPlatformText.cameraUnavailable
                return
            }
            captureSession.addInput(input)

            val output = AVCaptureMetadataOutput()
            if (!captureSession.canAddOutput(output)) {
                setupError = EasyOpenPlatformText.qrScannerStartFailed
                return
            }
            // AVCaptureMetadataOutput only exposes metadata types after it is
            // attached to the session. Setting metadataObjectTypes first throws
            // an Objective-C exception on iOS 27 instead of returning an error.
            captureSession.addOutput(output)
            if (!output.availableMetadataObjectTypes.contains(AVMetadataObjectTypeQRCode)) {
                setupError = EasyOpenPlatformText.qrScannerStartFailed
                return
            }
            val delegate = MetadataDelegate { payload ->
                if (finished) return@MetadataDelegate
                finished = true
                captureSession.stopRunning()
                dismissViewControllerAnimated(true) {
                    onPayload(payload)
                }
            }
            metadataDelegate = delegate
            output.setMetadataObjectsDelegate(delegate, queue = dispatch_get_main_queue())
            output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
            if (captureSession.canSetSessionPreset(AVCaptureSessionPresetHigh)) {
                captureSession.sessionPreset = AVCaptureSessionPresetHigh
            }
            session = captureSession

            val layer = AVCaptureVideoPreviewLayer(session = captureSession)
            layer.videoGravity = AVLayerVideoGravityResizeAspectFill
            previewLayer = layer
            view.layer.addSublayer(layer)

            closeButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
                setTitle(EasyOpenPlatformText.close, forState = UIControlStateNormal)
                setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
                addTarget(
                    target = this@QrScannerViewController,
                    action = NSSelectorFromString("closeScanner:"),
                    forControlEvents = UIControlEventTouchUpInside,
                )
            }.also(view::addSubview)
        }

        override fun viewDidAppear(animated: Boolean) {
            super.viewDidAppear(animated)
            setupError?.let { message ->
                setupError = null
                finished = true
                dismissViewControllerAnimated(true) {
                    IosDocumentTransferPresenter.presentError(message)
                }
                return
            }
            session?.startRunning()
        }

        override fun viewWillDisappear(animated: Boolean) {
            session?.stopRunning()
            super.viewWillDisappear(animated)
        }

        override fun viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            previewLayer?.setFrame(view.bounds)
            val width = CGRectGetWidth(view.bounds)
            val height = CGRectGetHeight(view.bounds)
            closeButton?.setFrame(CGRectMake(24.0, height - 68.0, width - 48.0, 44.0))
        }

        @ObjCAction
        @Suppress("UNUSED_PARAMETER")
        fun closeScanner(sender: NSObject?) {
            finished = true
            dismissViewControllerAnimated(true, completion = null)
        }
    }

    private class MetadataDelegate(
        private val onPayload: (String) -> Unit,
    ) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputMetadataObjects: List<*>,
            fromConnection: AVCaptureConnection,
        ) {
            val payload = didOutputMetadataObjects
                .filterIsInstance<AVMetadataMachineReadableCodeObject>()
                .firstNotNullOfOrNull { it.stringValue }
                ?.takeIf(String::isNotBlank)
                ?: return
            onPayload(payload)
        }
    }

    private class QrViewController(
        private val titleText: String,
        private val image: UIImage,
        private val summary: String,
    ) : UIViewController(nibName = null, bundle = null) {
        private lateinit var imageView: UIImageView
        private lateinit var titleLabel: platform.UIKit.UILabel
        private lateinit var summaryLabel: platform.UIKit.UILabel
        private lateinit var closeButton: UIButton

        override fun viewDidLoad() {
            super.viewDidLoad()
            view.backgroundColor = UIColor.whiteColor

            titleLabel = platform.UIKit.UILabel().apply {
                text = titleText
                textAlignment = NSTextAlignmentCenter
                font = UIFont.boldSystemFontOfSize(20.0)
            }
            imageView = UIImageView(image = image).apply {
                contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
                backgroundColor = UIColor.whiteColor
                clipsToBounds = true
            }
            summaryLabel = platform.UIKit.UILabel().apply {
                text = summary
                textAlignment = NSTextAlignmentCenter
                textColor = UIColor.grayColor
                numberOfLines = 0
            }
            closeButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
                setTitle(EasyOpenPlatformText.close, forState = UIControlStateNormal)
                addTarget(
                    target = this@QrViewController,
                    action = NSSelectorFromString("closeQr:"),
                    forControlEvents = UIControlEventTouchUpInside,
                )
            }
            view.addSubview(titleLabel)
            view.addSubview(imageView)
            view.addSubview(summaryLabel)
            view.addSubview(closeButton)
        }

        override fun viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            val width = CGRectGetWidth(view.bounds)
            val height = CGRectGetHeight(view.bounds)
            val horizontal = 24.0
            titleLabel.setFrame(CGRectMake(horizontal, 28.0, width - horizontal * 2, 32.0))
            val imageSize = minOf(width - horizontal * 2, height * 0.58)
            imageView.setFrame(CGRectMake(
                (width - imageSize) / 2.0,
                78.0,
                imageSize,
                imageSize,
            ))
            summaryLabel.setFrame(CGRectMake(horizontal, 78.0 + imageSize + 12.0, width - horizontal * 2, 48.0))
            closeButton.setFrame(CGRectMake(horizontal, height - 64.0, width - horizontal * 2, 44.0))
        }

        @ObjCAction
        @Suppress("UNUSED_PARAMETER")
        fun closeQr(sender: NSObject?) {
            dismissViewControllerAnimated(true, completion = null)
        }
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

package com.juren233.easyopen.shared.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.darwin.NSObject
import kotlin.coroutines.resume

/** A stable GitHub release that is newer than the installed Android-derived build. */
data class IosAvailableUpdate(
    val versionName: String,
    val versionCode: Long,
    val releaseUrl: String,
) {
    val displayVersion: String = "v$versionName"
}

/**
 * Fetches the same stable-release metadata used by Android.
 *
 * iOS marketing versions can contain the extra beta/canary components used by
 * App Store-compatible CFBundleShortVersionString values, so update ordering
 * intentionally uses CFBundleVersion, which is the Android versionCode copied
 * into the iOS bundle by the IPA workflow.
 */
object IosUpdateChecker {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/juren233/easyopen/releases/latest"
    private const val LATEST_RELEASE_PAGE =
        "https://github.com/juren233/easyopen/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class GitHubLatestRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String? = null,
        val assets: List<GitHubReleaseAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubReleaseAsset(val name: String)

    suspend fun findUpdate(currentVersionCode: Long): IosAvailableUpdate? {
        val latest = fetchLatest() ?: return null
        return latest.takeIf { it.versionCode > currentVersionCode }
    }

    private suspend fun fetchLatest(): IosAvailableUpdate? =
        suspendCancellableCoroutine { continuation ->
            val url = NSURL.URLWithString(LATEST_RELEASE_API)
            if (url == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val delegate = UpdateDelegate { body ->
                if (continuation.isActive) {
                    continuation.resume(body?.let(::parseRelease))
                }
            }
            val session = NSURLSession.sessionWithConfiguration(
                configuration = NSURLSessionConfiguration.defaultSessionConfiguration,
                delegate = delegate,
                delegateQueue = null,
            )
            val task = session.dataTaskWithURL(url)
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }

    @OptIn(ExperimentalForeignApi::class)
    private class UpdateDelegate(
        private val completion: (ByteArray?) -> Unit,
    ) : NSObject(), NSURLSessionDataDelegateProtocol {
        private val chunks = mutableListOf<ByteArray>()

        override fun URLSession(
            session: NSURLSession,
            dataTask: NSURLSessionDataTask,
            didReceiveData: NSData,
        ) {
            didReceiveData.bytes
                ?.reinterpret<ByteVar>()
                ?.readBytes(didReceiveData.length.toInt())
                ?.let(chunks::add)
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?,
        ) {
            completion(
                if (didCompleteWithError != null) null
                else chunks.fold(ByteArray(0)) { result, chunk -> result + chunk },
            )
        }
    }

    private fun parseRelease(bytes: ByteArray): IosAvailableUpdate? = runCatching {
        val raw = bytes.decodeToString()
        val release = json.decodeFromString<GitHubLatestRelease>(raw)
        val versionName = Regex("^v(\\d+\\.\\d+\\.\\d+)$")
            .matchEntire(release.tagName.trim())
            ?.groupValues
            ?.getOrNull(1)
            ?: return@runCatching null
        val versionCodeRegex = Regex(
            "^v${Regex.escape(versionName)}-(\\d+)\\.apk$",
            RegexOption.IGNORE_CASE,
        )
        val versionCode = release.assets
            .asSequence()
            .mapNotNull { asset ->
                versionCodeRegex.find(asset.name.trim())
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull()
            }
            .firstOrNull()
            ?: return@runCatching null
        IosAvailableUpdate(
            versionName = versionName,
            versionCode = versionCode,
            releaseUrl = release.htmlUrl ?: LATEST_RELEASE_PAGE,
        )
    }.getOrNull()
}

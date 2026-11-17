package io.github.kawinnext

import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.QueueFlyoutRendererFingerprint
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.QueuePlayerCloseClickFingerprint
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.QueuePlayerCloseFlingFingerprint
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.QueuePlayerCloseFlingLegacyFingerprint
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.QueuePlayerCloseTouchFingerprint
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.QueuePlayerControllerFlingFingerprint
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.QueuePlayerControllerReleaseFingerprint
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.dismissCallOf
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.queueFlyoutButtonType
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.queueFlyoutClickFingerprint
import io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue.queueFlyoutItemType
import io.github.kawinnext.morphe.youtube.misc.loadvideo.dismissPlayerMethod
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.AfterParameterizedClassInvocation
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.support.ParameterDeclarations
import org.junit.jupiter.api.extension.ExtensionContext
import org.luckypray.dexkit.DexKitBridge
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream

/**
 * The queue builds two of its hooks out of other fingerprints instead of declaring them on a
 * `FingerprintsKt` class, so the fingerprint suite never ran them: the sheet click of the flyout and
 * the paths that close the mini player. This test runs both chains on every YouTube build the
 * module carries, which is where a build that reshaped either chain shows up.
 */
@ParameterizedClass
@ArgumentsSource(YoutubeApkArgumentsProvider::class)
class QueueFingerprintChainsTest(val apkPath: Path) {

    private val context = contexts.computeIfAbsent(apkPath) { ApkContext(it.toString()) }
    private val dexkit: DexKitBridge = context.dexkit

    @Test
    fun sheetClicksResolve() {
        with(dexkit) {
            val item = queueFlyoutItemType()
            val button = queueFlyoutButtonType()
            val sheets = QueueFlyoutRendererFingerprint.matchAll()
            assertTrue(sheets.isNotEmpty(), "no flyout sheet matched in $apkPath")

            for (sheet in sheets) {
                val sheetType = sheet.method.declaredClass!!.descriptor
                val click = queueFlyoutClickFingerprint(sheetType, button, item).matchOrNull()
                assertNotNull(click, "no sheet click matched for $sheetType in $apkPath")
                // The click must end with the call that dismisses the sheet, which the hook reads
                // from the instructions of the matched method.
                dismissCallOf(click!!.method)
            }
        }
    }

    @Test
    fun playerCloseResolves() {
        with(dexkit) {
            val dismiss = dismissPlayerMethod()
            val paths = listOf(
                "click" to QueuePlayerCloseClickFingerprint(dismiss),
                "fling" to QueuePlayerCloseFlingFingerprint(dismiss),
                "fling legacy" to QueuePlayerCloseFlingLegacyFingerprint(dismiss),
                "touch" to QueuePlayerCloseTouchFingerprint(dismiss),
                "controller fling" to QueuePlayerControllerFlingFingerprint(dismiss),
                "controller release" to QueuePlayerControllerReleaseFingerprint(dismiss),
            )
            // A build carries some of these paths and not others, and the hook skips what it does
            // not find. None at all means closing the player no longer stops the queue.
            val matched = paths.filter { it.second.matchOrNull() != null }.map { it.first }
            assertTrue(matched.isNotEmpty(), "no player close path matched in $apkPath")
            println("player close paths in ${apkPath.fileName}: $matched")
        }
    }

    companion object {
        private val contexts = ConcurrentHashMap<Path, ApkContext>()

        @AfterParameterizedClassInvocation
        @JvmStatic
        fun closeContext(apkPath: Path) {
            contexts.remove(apkPath)?.close()
        }
    }
}

/** Every YouTube build in `binaries/`, which is what the queue's fingerprints are written for. */
class YoutubeApkArgumentsProvider : ArgumentsProvider {
    override fun provideArguments(
        parameters: ParameterDeclarations,
        context: ExtensionContext
    ): Stream<out Arguments> {
        val binaries = Paths.get(".").resolve("binaries")
        if (!Files.exists(binaries)) {
            throw IllegalStateException("APKs folder not found: $binaries")
        }
        val paths = Files.walk(binaries).use { files ->
            files.filter { path ->
                Files.isRegularFile(path) &&
                    path.fileName.toString().startsWith("com.google.android.youtube-")
            }.toList()
        }
        return paths.stream().map { Arguments.of(it) }
    }
}

package app.linkphone.core.update

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateEngineTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val apkContent = "linkphone-apk".toByteArray()

    @Test
    fun aNewerReleaseIsDownloadedVerifiedAndReady() = runTest {
        val client = fakeClient()
        client.release = release("0.7.1")
        client.checksums = sums("LinkPhone-0.7.1.apk", apkContent)
        val dir = folder.newFolder("updates")
        var status: UpdateStatus? = null
        var notified = 0
        val engine = engine(
            client = client,
            dir = dir,
            scope = backgroundScope,
            onStatus = { status = it },
            notify = { _, _ -> notified++ },
        )

        engine.start()
        engine.check(manual = true)

        assertEquals(UpdatePhase.Ready, status?.phase)
        assertEquals("0.7.1", status?.version)
        assertEquals(100, status?.progress ?: -1)
        assertTrue(File(dir, "LinkPhone-0.7.1.apk").exists())
        assertFalse(File(dir, "LinkPhone-0.7.1.apk.part").exists())
        assertNotNull(engine.readyApk)
        assertEquals(1, notified)
    }

    @Test
    fun theCurrentVersionIsUpToDate() = runTest {
        val client = fakeClient()
        client.release = release("0.7.0")
        val dir = folder.newFolder("updates")
        var status: UpdateStatus? = null
        val engine = engine(client, dir, backgroundScope, onStatus = { status = it })

        engine.start()
        engine.check(manual = true)

        assertEquals(UpdatePhase.UpToDate, status?.phase)
        assertNull(status?.version)
        assertEquals(0, client.downloadCalls)
    }

    @Test
    fun aMissingReleaseCountsAsUpToDate() = runTest {
        val client = fakeClient()
        client.release = null
        val dir = folder.newFolder("updates")
        var status: UpdateStatus? = null
        val engine = engine(client, dir, backgroundScope, onStatus = { status = it })

        engine.start()
        engine.check(manual = true)

        assertEquals(UpdatePhase.UpToDate, status?.phase)
        assertEquals(0, client.downloadCalls)
    }

    @Test
    fun aChecksumMismatchFailsAndKeepsNoFiles() = runTest {
        val client = fakeClient()
        client.release = release("0.7.1")
        client.checksums = "${"0".repeat(64)}  LinkPhone-0.7.1.apk"
        val dir = folder.newFolder("updates")
        var status: UpdateStatus? = null
        var notified = 0
        val engine = engine(
            client = client,
            dir = dir,
            scope = backgroundScope,
            onStatus = { status = it },
            notify = { _, _ -> notified++ },
        )

        engine.start()
        engine.check(manual = true)

        assertEquals(UpdatePhase.Error, status?.phase)
        assertTrue(status?.error?.contains("SHA-256") == true)
        assertFalse(File(dir, "LinkPhone-0.7.1.apk").exists())
        assertFalse(File(dir, "LinkPhone-0.7.1.apk.part").exists())
        assertNull(engine.readyApk)
        assertEquals(0, notified)
    }

    @Test
    fun aMissingChecksumEntryFails() = runTest {
        val client = fakeClient()
        client.release = release("0.7.1")
        client.checksums = "${"0".repeat(64)}  LinkPhone-0.6.1.apk"
        val dir = folder.newFolder("updates")
        var status: UpdateStatus? = null
        val engine = engine(client, dir, backgroundScope, onStatus = { status = it })

        engine.start()
        engine.check(manual = true)

        assertEquals(UpdatePhase.Error, status?.phase)
        assertTrue(status?.error?.contains("SHA256SUMS.txt") == true)
        assertFalse(File(dir, "LinkPhone-0.7.1.apk").exists())
    }

    @Test
    fun aForeignSignatureFails() = runTest {
        val client = fakeClient()
        client.release = release("0.7.1")
        client.checksums = sums("LinkPhone-0.7.1.apk", apkContent)
        val dir = folder.newFolder("updates")
        var status: UpdateStatus? = null
        val engine = engine(
            client = client,
            dir = dir,
            scope = backgroundScope,
            onStatus = { status = it },
            verifySignature = { "This update is signed with a different key." },
        )

        engine.start()
        engine.check(manual = true)

        assertEquals(UpdatePhase.Error, status?.phase)
        assertTrue(status?.error?.contains("different key") == true)
        assertFalse(File(dir, "LinkPhone-0.7.1.apk").exists())
        assertFalse(File(dir, "LinkPhone-0.7.1.apk.part").exists())
    }

    @Test
    fun aDisabledBuildNeverContactsGitHub() = runTest {
        val client = fakeClient()
        client.release = release("0.7.1")
        val dir = folder.newFolder("updates")
        var status: UpdateStatus? = null
        val engine = engine(
            client = client,
            dir = dir,
            scope = backgroundScope,
            mode = UpdateMode.Disabled,
            onStatus = { status = it },
        )

        engine.start()
        engine.check(manual = true)

        assertEquals(UpdatePhase.Unsupported, status?.phase)
        assertEquals(0, client.latestCalls)
        assertEquals(0, client.downloadCalls)
    }

    @Test
    fun anAutomaticCheckRunsAfterTheInitialDelay() = runTest {
        val client = fakeClient()
        client.release = release("0.7.1")
        client.checksums = sums("LinkPhone-0.7.1.apk", apkContent)
        val dir = folder.newFolder("updates")
        val engine = engine(client, dir, backgroundScope, autoCheck = true)

        engine.start()
        assertEquals(0, client.latestCalls)

        advanceTimeBy(15_000)
        runCurrent()

        assertEquals(1, client.latestCalls)
        assertEquals(1, client.downloadCalls)
        engine.stop()
    }

    @Test
    fun noAutomaticCheckRunsAfterStopping() = runTest {
        val client = fakeClient()
        client.release = release("0.7.1")
        val dir = folder.newFolder("updates")
        val engine = engine(client, dir, backgroundScope, autoCheck = true)

        engine.start()
        engine.stop()
        advanceTimeBy(15_000)
        runCurrent()

        assertEquals(0, client.latestCalls)
    }

    @Test
    fun anAutomaticCheckInsideTheWindowIsSkippedButAManualOneIsNot() = runTest {
        val client = fakeClient()
        client.release = release("0.7.1")
        client.checksums = sums("LinkPhone-0.7.1.apk", apkContent)
        val dir = folder.newFolder("updates")
        var lastCheck = 1_000L
        val engine = engine(
            client = client,
            dir = dir,
            scope = backgroundScope,
            autoCheck = true,
            lastCheckAt = { lastCheck },
            now = { lastCheck },
        )

        engine.start()
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(0, client.latestCalls)

        engine.check(manual = true)
        assertEquals(1, client.latestCalls)
    }

    @Test
    fun anAutomaticCheckRunsAfterTheThrottleWindow() = runTest {
        val client = fakeClient()
        client.release = release("0.7.1")
        client.checksums = sums("LinkPhone-0.7.1.apk", apkContent)
        val dir = folder.newFolder("updates")
        val lastCheck = 1_000L
        val engine = engine(
            client = client,
            dir = dir,
            scope = backgroundScope,
            autoCheck = true,
            lastCheckAt = { lastCheck },
            now = { lastCheck + UpdateEngine.AUTO_CHECK_INTERVAL_MS + 1 },
        )

        engine.start()
        advanceTimeBy(15_000)
        runCurrent()

        assertEquals(1, client.latestCalls)
    }

    @Test
    fun aManualCheckDownloadsEvenWhenAutoCheckIsOff() = runTest {
        val client = fakeClient()
        client.release = release("0.7.1")
        client.checksums = sums("LinkPhone-0.7.1.apk", apkContent)
        val dir = folder.newFolder("updates")
        var status: UpdateStatus? = null
        val engine = engine(
            client = client,
            dir = dir,
            scope = backgroundScope,
            autoCheck = false,
            onStatus = { status = it },
        )

        engine.start()
        engine.check(manual = true)

        assertEquals(1, client.latestCalls)
        assertEquals(1, client.downloadCalls)
        assertEquals(UpdatePhase.Ready, status?.phase)
    }

    @Test
    fun recheckingTheSameVersionKeepsTheDownloadedApk() = runTest {
        val client = fakeClient()
        client.release = release("0.7.1")
        client.checksums = sums("LinkPhone-0.7.1.apk", apkContent)
        val dir = folder.newFolder("updates")
        var status: UpdateStatus? = null
        var notified = 0
        val engine = engine(
            client = client,
            dir = dir,
            scope = backgroundScope,
            onStatus = { status = it },
            notify = { _, _ -> notified++ },
        )

        engine.start()
        engine.check(manual = true)
        assertEquals(UpdatePhase.Ready, status?.phase)

        engine.check(manual = true)

        assertEquals(UpdatePhase.Ready, status?.phase)
        assertEquals(1, client.downloadCalls)
        assertEquals(1, notified)
        assertTrue(File(dir, "LinkPhone-0.7.1.apk").exists())
    }

    @Test
    fun anExistingApkIsSignatureCheckedBeforeItIsOffered() = runTest {
        val client = fakeClient()
        client.release = release("0.7.1")
        client.checksums = sums("LinkPhone-0.7.1.apk", apkContent)
        val dir = folder.newFolder("updates")
        File(dir, "LinkPhone-0.7.1.apk").writeBytes(apkContent)
        var status: UpdateStatus? = null
        val engine = engine(
            client = client,
            dir = dir,
            scope = backgroundScope,
            onStatus = { status = it },
            verifySignature = { "This update is signed with a different key." },
        )

        engine.start()
        engine.check(manual = true)

        assertEquals(UpdatePhase.Error, status?.phase)
        assertTrue(status?.error?.contains("different key") == true)
        assertFalse(File(dir, "LinkPhone-0.7.1.apk").exists())
        assertNull(engine.readyApk)
        assertEquals(0, client.downloadCalls)
    }

    @Test
    fun staleFilesAreRemovedOnStart() = runTest {
        val dir = folder.newFolder("updates")
        val part = File(dir, "LinkPhone-0.7.1.apk.part")
        part.writeText("partial")
        val older = File(dir, "LinkPhone-0.9.9.apk")
        older.writeText("old")
        val newer = File(dir, "LinkPhone-0.10.0.apk")
        newer.writeText("new")

        val engine = engine(fakeClient(), dir, backgroundScope)
        engine.start()

        assertFalse(part.exists())
        assertFalse(older.exists())
        assertTrue(newer.exists())
    }

    @Test
    fun aFailedCheckReportsTheError() = runTest {
        val client = fakeClient()
        client.latestError = UpdateException("GitHub returned HTTP 500")
        val dir = folder.newFolder("updates")
        var status: UpdateStatus? = null
        val engine = engine(client, dir, backgroundScope, onStatus = { status = it })

        engine.start()
        engine.check(manual = true)

        assertEquals(UpdatePhase.Error, status?.phase)
        assertEquals("GitHub returned HTTP 500", status?.error)
    }

    private fun fakeClient(): FakeUpdateClient = FakeUpdateClient(apkContent)

    private fun engine(
        client: UpdateClient,
        dir: File,
        scope: CoroutineScope,
        mode: UpdateMode = UpdateMode.Installed,
        currentVersion: String = "0.7.0",
        autoCheck: Boolean = false,
        onStatus: (UpdateStatus) -> Unit = {},
        notify: (String, String) -> Unit = { _, _ -> },
        verifySignature: (File) -> String? = { null },
        lastCheckAt: () -> Long = { 0L },
        recordCheckAt: (Long) -> Unit = {},
        now: () -> Long = { 1_000L },
    ): UpdateEngine = UpdateEngine(
        UpdateEngineOptions(
            mode = mode,
            currentVersion = currentVersion,
            autoCheck = autoCheck,
            client = client,
            updatesDir = dir,
            scope = scope,
            onStatus = onStatus,
            notify = notify,
            verifySignature = verifySignature,
            lastCheckAt = lastCheckAt,
            recordCheckAt = recordCheckAt,
            now = now,
            ioDispatcher = Dispatchers.Unconfined,
        ),
    )

    private fun release(version: String) = ReleaseInfo(
        version = version,
        notes = "Notes for $version",
        pageUrl = "https://example.com/releases/v$version",
        apk = ReleaseAsset(
            name = apkFileName(version),
            url = "https://example.com/${apkFileName(version)}",
            size = apkContent.size.toLong(),
        ),
        checksums = ReleaseAsset(
            name = "SHA256SUMS.txt",
            url = "https://example.com/SHA256SUMS.txt",
            size = 96L,
        ),
    )

    private fun sums(name: String, bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        return "$digest  $name"
    }
}

private class FakeUpdateClient(private val content: ByteArray) : UpdateClient {

    var release: ReleaseInfo? = null
    var checksums: String = ""
    var latestError: Exception? = null
    var latestCalls = 0
    var downloadCalls = 0

    override suspend fun latestRelease(): ReleaseInfo? {
        latestCalls += 1
        latestError?.let { throw it }
        return release
    }

    override suspend fun readText(url: String, maxBytes: Int): String = checksums

    override suspend fun download(
        url: String,
        target: File,
        maxBytes: Int,
        onProgress: (Int) -> Unit,
    ) {
        downloadCalls += 1
        target.parentFile?.mkdirs()
        target.writeBytes(content)
        onProgress(50)
        onProgress(100)
    }
}

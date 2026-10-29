package app.linkphone.features.notifications

import app.linkphone.core.model.NotificationDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationSnapshotTest {
    @Test
    fun prefersTheShortText() {
        assertEquals(
            "One message",
            NotificationText.pick("One message", "A longer message", listOf("A line")),
        )
    }

    @Test
    fun fallsBackToTheExpandedText() {
        assertEquals(
            "A longer message",
            NotificationText.pick(null, "A longer message", listOf("A line")),
        )
    }

    @Test
    fun fallsBackToTheMessageLines() {
        assertEquals(
            "First line\nSecond line",
            NotificationText.pick(null, null, listOf("First line", "Second line")),
        )
    }

    @Test
    fun ignoresBlankText() {
        assertEquals(
            "Real text",
            NotificationText.pick("   ", "Real text", null),
        )
        assertNull(NotificationText.pick(null, "  ", listOf("  ")))
    }

    @Test
    fun dropsLinkPhonesOwnNotifications() {
        val items = listOf(
            dto("a", "app.linkphone", 10),
            dto("b", "com.messenger", 20),
        )
        val selected = NotificationSnapshot.select(items, ownPackage = "app.linkphone", limit = 10)
        assertEquals(listOf("b"), selected.map { it.key })
    }

    @Test
    fun sortsNewestFirstAndCapsTheList() {
        val items = listOf(
            dto("old", "com.messenger", 10),
            dto("new", "com.messenger", 30),
            dto("mid", "com.messenger", 20),
        )
        val selected = NotificationSnapshot.select(items, ownPackage = "app.linkphone", limit = 2)
        assertEquals(listOf("new", "mid"), selected.map { it.key })
    }

    private fun dto(key: String, packageName: String, time: Long) = NotificationDto(
        key = key,
        packageName = packageName,
        appName = "App",
        title = "Title",
        text = "Text",
        time = time,
    )
}

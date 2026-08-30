package com.gamepadbuddy.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/**
 * Unit test ProfileRepository (file 06 + file 09): lưu/đọc nhiều profile qua JSON.
 * Dùng thư mục tạm trên JVM (ProfileRepository nhận File dir, testable không cần Context).
 */
class ProfileRepositoryTest {

    @Test
    fun saveAndLoad_roundTripsWidgets() {
        val dir = File(System.getProperty("java.io.tmpdir"), "gpb_test_${System.nanoTime()}")
        dir.mkdirs()
        try {
            val repo = ProfileRepository(dir)
            val profile = Profile("p1", "com.game.example", "MOBA", listOf(
                MappedWidget.Button("b", 100f, 200f, 96),
                MappedWidget.Joystick("j", 50f, 60f, 80f, AxisGroup.RIGHT_STICK)))
            repo.save(profile)

            val loaded = repo.getForPackage("com.game.example")
            assertNotNull(loaded)
            assertEquals(2, loaded!!.widgets.size)
            assertEquals(96, (loaded.widgets[0] as MappedWidget.Button).boundKeyCode)
            assertEquals(AxisGroup.RIGHT_STICK, (loaded.widgets[1] as MappedWidget.Joystick).axisGroup)
            assertEquals(80f, (loaded.widgets[1] as MappedWidget.Joystick).radius)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun save_overwritesSameId() {
        val dir = File(System.getProperty("java.io.tmpdir"), "gpb_test2_${System.nanoTime()}")
        dir.mkdirs()
        try {
            val repo = ProfileRepository(dir)
            repo.save(Profile("p1", "com.a", "v1", listOf(MappedWidget.Button("b", 1f, 1f, 96))))
            repo.save(Profile("p1", "com.a", "v2", listOf(MappedWidget.Button("b", 2f, 2f, 96))))
            assertEquals(1, repo.getAll().size)
            assertEquals("v2", repo.getForPackage("com.a")!!.name)
        } finally {
            dir.deleteRecursively()
        }
    }
}

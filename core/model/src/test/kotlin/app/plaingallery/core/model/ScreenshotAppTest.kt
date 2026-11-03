package app.plaingallery.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenshotAppTest {
    @Test fun samsungNamesTheApp() {
        assertEquals(ScreenshotApp.Label("Instagram"), ScreenshotApp.parse("Screenshot_20241103_101010_Instagram.jpg"))
        assertEquals(ScreenshotApp.Label("Samsung Internet"), ScreenshotApp.parse("Screenshot_20241103_101010_Samsung Internet.jpg"))
        assertEquals(ScreenshotApp.Label("Pokémon GO"), ScreenshotApp.parse("Screenshot_20241103-101010_Pokémon GO (1).jpg"))
    }

    @Test fun xiaomiAndHuaweiNameThePackage() {
        assertEquals(ScreenshotApp.Package("com.instagram.android"), ScreenshotApp.parse("Screenshot_2024-11-03-10-10-10-123_com.instagram.android.jpg"))
        assertEquals(ScreenshotApp.Package("com.whatsapp"), ScreenshotApp.parse("Screenshot_20241103_101010_com.whatsapp.jpg"))
    }

    @Test fun dateOnlyNamesHaveNoApp() {
        assertNull(ScreenshotApp.parse("Screenshot_20241103-101010.png"))
        assertNull(ScreenshotApp.parse("Screenshot_2024-11-03-10-10-10-12_e2d5b3f32b79de1d74acd2c33f3a22c1.jpg"))
        assertNull(ScreenshotApp.parse("IMG_20241103_101010.jpg"))
        assertNull(ScreenshotApp.parse("Screenshot_20241103_101010_2.png"))
    }

    @Test fun fallbackLabels() {
        assertEquals("Pokémon GO", ScreenshotApp.fallbackLabel("com.nianticlabs.pokemongo"))
        assertEquals("Ovo", ScreenshotApp.fallbackLabel("ovo.id"))
        assertEquals("Bukalapak", ScreenshotApp.fallbackLabel("com.bukalapak.android"))
    }
}

class AccentSearchTest {
    @Test fun searchIgnoresAccentsAndFindsTextAndApps() {
        val shot = testMedia(1, source = MediaSource.Screenshot, name = "Screenshot_1.jpg").copy(screenshotApp = "Pokémon GO")
        val receipt = testMedia(2, source = MediaSource.Screenshot, name = "Screenshot_2.jpg")
        val lib = Library(listOf(shot, receipt), texts = mapOf(2L to "total pembayaran rp 125.000"))
        val parser = SearchQueryParser { java.time.LocalDate.of(2026, 11, 3) }
        assertEquals(listOf(1L), lib.search(parser.parse("pokemon")).map { it.id })
        assertEquals(listOf(1L), lib.search(parser.parse("Pokémon")).map { it.id })
        assertEquals(listOf(2L), lib.search(parser.parse("pembayaran 125.000")).map { it.id })
        assertEquals(listOf("Pokémon GO"), lib.screenshotApps)
    }
}

class UserAlbumTest {
    @Test fun albumsListLivePhotosInAddedOrder() {
        val lib = Library(
            listOf(testMedia(1), testMedia(2), testMedia(3, trashed = true)),
            userAlbumList = listOf(UserAlbum(7, "Liburan", listOf(2L, 3L, 1L, 99L))),
        )
        assertEquals(listOf(2L, 1L), lib.resolve(MediaCollection.UserAlbum(7)) { error("unused") }.map { it.id })
        val view = lib.userAlbums.single()
        assertEquals(2, view.count)
        assertEquals(2L, view.cover?.id)
    }
}

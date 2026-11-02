package app.plaingallery.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceClassifierTest {
    private fun classify(path: String, owner: String? = null, name: String = "x.jpg") =
        SourceClassifier.classify(path, owner, name)

    @Test fun cameraFolders() {
        assertEquals(MediaSource.Camera, classify("DCIM/Camera/"))
        assertEquals(MediaSource.Camera, classify("DCIM/100ANDRO/"))
    }

    @Test fun screenshotsOnEveryVendor() {
        assertEquals(MediaSource.Screenshot, classify("DCIM/Screenshots/"))
        assertEquals(MediaSource.Screenshot, classify("Pictures/Screenshots/"))
        assertEquals(MediaSource.Screenshot, classify("DCIM/Screen recordings/"))
        assertEquals(MediaSource.Screenshot, classify("Movies/ScreenRecord/"))
    }

    @Test fun whatsAppByPathOwnerOrName() {
        assertEquals(MediaSource.WhatsApp, classify("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"))
        assertEquals(MediaSource.WhatsApp, classify("Android/media/com.whatsapp.w4b/WhatsApp Business/Media/"))
        assertEquals(MediaSource.WhatsApp, classify("Pictures/", owner = "com.whatsapp"))
        assertEquals(MediaSource.WhatsApp, classify("Pictures/", name = "IMG-20240101-WA0007.jpg"))
    }

    @Test fun telegramAndDownloads() {
        assertEquals(MediaSource.Telegram, classify("Pictures/Telegram/"))
        assertEquals(MediaSource.Telegram, classify("Pictures/", owner = "org.telegram.messenger"))
        assertEquals(MediaSource.Download, classify("Download/"))
    }

    @Test fun everythingElseIsOther() {
        assertEquals(MediaSource.Other, classify("Pictures/Instagram/"))
        assertEquals(MediaSource.Other, classify(""))
    }
}

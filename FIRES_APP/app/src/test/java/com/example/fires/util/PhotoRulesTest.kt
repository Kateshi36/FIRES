package com.example.fires.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoRulesTest {

    // ---------- Type ----------

    @Test fun type_jpegPngWebp_areAccepted() {
        listOf("image/jpeg", "image/png", "image/webp", "IMAGE/JPEG", " image/png ", "image/jpg")
            .forEach { assertNull("'$it'", PhotoRules.validateType(it)) }
    }

    @Test fun type_otherImages_getTheTypeMessage() {
        listOf("image/gif", "image/heic", "image/bmp", "image/svg+xml")
            .forEach { assertEquals("'$it'", PhotoRules.MSG_BAD_TYPE, PhotoRules.validateType(it)) }
    }

    @Test fun type_notAnImage_orUnknown_getsTheNotImageMessage() {
        listOf(null, "", "   ", "application/pdf", "text/plain", "video/mp4")
            .forEach { assertEquals("'$it'", PhotoRules.MSG_NOT_IMAGE, PhotoRules.validateType(it)) }
    }

    // ---------- Size of the original ----------

    @Test fun size_atTheLimit_isAccepted() = assertNull(PhotoRules.validateSourceSize(PhotoRules.MAX_SOURCE_BYTES.toLong()))

    @Test fun size_overTheLimit_isRejected() =
        assertEquals(PhotoRules.MSG_TOO_LARGE, PhotoRules.validateSourceSize(PhotoRules.MAX_SOURCE_BYTES + 1L))

    @Test fun size_emptyFile_isRejected() {
        assertEquals(PhotoRules.MSG_NOT_IMAGE, PhotoRules.validateSourceSize(0))
        assertEquals(PhotoRules.MSG_NOT_IMAGE, PhotoRules.validateSourceSize(-1))
    }

    // ---------- Resizing ----------

    @Test fun scaledSize_landscapeAndPortrait_keepTheShape() {
        assertEquals(1024 to 768, PhotoRules.scaledSize(4000, 3000, 1024))
        assertEquals(768 to 1024, PhotoRules.scaledSize(3000, 4000, 1024))
    }

    @Test fun scaledSize_smallPhoto_isNeverEnlarged() {
        assertEquals(640 to 480, PhotoRules.scaledSize(640, 480, 1024))
        assertEquals(1024 to 1024, PhotoRules.scaledSize(1024, 1024, 1024))
    }

    @Test fun scaledSize_extremeShape_neverReachesZero() {
        val (w, h) = PhotoRules.scaledSize(10000, 5, 1024)
        assertEquals(1024, w)
        assertTrue(h >= 1)
    }

    // ---------- The shrink ladder ----------

    @Test fun ladder_startsAtTheFullSize_andNeverGrows() {
        val attempts = PhotoRules.ATTEMPTS
        assertEquals(PhotoRules.MAX_SIDE, attempts.first().maxSide)
        attempts.zipWithNext().forEach { (a, b) ->
            assertTrue("size must not go up", b.maxSide <= a.maxSide)
            if (b.maxSide == a.maxSide) assertTrue("quality must go down", b.quality < a.quality)
        }
    }

    @Test fun ladder_qualitiesStayInASensibleRange() {
        PhotoRules.ATTEMPTS.forEach { assertTrue(it.quality in 30..90) }
    }
}

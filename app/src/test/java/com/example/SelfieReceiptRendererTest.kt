package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AttendanceRecord
import com.example.data.remote.AttendanceReceiptRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SelfieReceiptRendererTest {

  @Test
  fun renderReceiptToUri_withSelfiePhoto_creates1080x1440PngAndReusesOnResend() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val rawSelfie = File(receiptsDir, "raw_capture_test.jpg")

    val dummySelfie = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888).apply {
      eraseColor(Color.rgb(32, 120, 110))
    }
    FileOutputStream(rawSelfie).use { stream ->
      dummySelfie.compress(Bitmap.CompressFormat.JPEG, 90, stream)
    }
    dummySelfie.recycle()

    val record = AttendanceRecord(
      id = "rec_selfie_test",
      userId = "uid_1",
      staffId = "staff_1",
      staffName = "د. سارة محمد",
      staffRole = "محللة مختبر",
      workDateUtc = "2026-11-12",
      checkInUtcIso = "2026-11-12T08:00:00Z",
      checkInDisplayTime = "11:00:00 ص",
      checkInEpochMs = 1794470400000L,
      checkInSource = "timeapi.io (UTC)",
      status = AttendanceRecord.STATUS_CHECKED_IN,
    )

    val uri = AttendanceReceiptRenderer.renderReceiptToUri(
      context = context,
      record = record,
      isCheckOut = false,
      selfieFile = rawSelfie,
    )

    assertNotNull(uri)
    assertTrue(uri.toString().contains("fileprovider"))
    assertFalse("Temporary raw capture should be cleaned up", rawSelfie.exists())

    val savedPng = AttendanceReceiptRenderer.getReceiptFile(context, record.id, isCheckOut = false)
    assertTrue(savedPng.exists())
    assertTrue(savedPng.length() > 0L)

    val decoded = BitmapFactory.decodeFile(savedPng.absolutePath)
    assertNotNull(decoded)
    assertEquals(1080, decoded.width)
    assertEquals(1440, decoded.height)
    decoded.recycle()

    // Resend without selfieFile should reuse cached PNG rather than overwriting with 1080x1280 fallback
    val resendUri = AttendanceReceiptRenderer.renderReceiptToUri(
      context = context,
      record = record,
      isCheckOut = false,
      selfieFile = null,
    )
    assertEquals(uri, resendUri)
    val decodedResend = BitmapFactory.decodeFile(savedPng.absolutePath)
    assertEquals(1440, decodedResend.height)
    decodedResend.recycle()
  }
}

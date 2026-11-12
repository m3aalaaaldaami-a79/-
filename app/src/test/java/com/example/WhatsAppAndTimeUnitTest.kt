package com.example

import com.example.data.local.WhatsAppConfigStore
import com.example.data.model.AttendanceRecord
import com.example.data.remote.AttendanceReceiptRenderer
import com.example.data.remote.WhatsAppDispatcher
import com.example.data.remote.WorldTimeService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppAndTimeUnitTest {

  @Test
  fun formatGlobalTimeStamp_formatsUtcAndBaghdadTimeCorrectly() {
    val epochMs = 1794470400000L
    val stamp = WorldTimeService.formatGlobalTimeStamp(epochMs, "timeapi.io (UTC)")

    assertEquals("2026-11-12T08:00:00Z", stamp.utcIso)
    assertEquals("2026-11-12", stamp.workDateBaghdad)
    assertEquals("08:00:00 UTC", stamp.displayTimeUtc)
    assertEquals("timeapi.io (UTC)", stamp.sourceName)
  }

  @Test
  fun whatsappMessages_includeRequiredAttendanceDetails() {
    val record = AttendanceRecord(
      id = "rec_1",
      userId = "uid_1",
      staffId = "staff_1",
      staffName = "د. علي حسن",
      staffRole = "أخصائي تحاليل",
      workDateUtc = "2026-11-12",
      checkInUtcIso = "2026-11-12T08:00:00Z",
      checkInDisplayTime = "11:00:00 ص",
      checkInEpochMs = 1794470400000L,
      checkInSource = "timeapi.io (UTC)",
      checkOutUtcIso = "2026-11-12T15:30:00Z",
      checkOutDisplayTime = "06:30:00 م",
      checkOutEpochMs = 1794497400000L,
      checkOutSource = "Google Global Server (UTC)",
      status = AttendanceRecord.STATUS_CHECKED_OUT,
    )

    assertEquals("+9647830697575", WhatsAppDispatcher.TARGET_PHONE_DISPLAY)

    val checkInMsg = WhatsAppDispatcher.buildCheckInMessage(record)
    assertTrue(checkInMsg.contains("د. علي حسن"))
    assertTrue(checkInMsg.contains("2026-11-12T08:00:00Z"))
    assertTrue(checkInMsg.contains("timeapi.io (UTC)"))

    val checkOutMsg = WhatsAppDispatcher.buildCheckOutMessage(record)
    assertTrue(checkOutMsg.contains("د. علي حسن"))
    assertTrue(checkOutMsg.contains("2026-11-12T15:30:00Z"))
    assertTrue(checkOutMsg.contains("Google Global Server (UTC)"))
  }

  @Test
  fun verificationSeal_isDeterministicAndDistinctForCheckInAndCheckOut() {
    val record = AttendanceRecord(
      id = "rec_seal_1",
      userId = "uid_1",
      staffId = "staff_1",
      staffName = "د. علي حسن",
      staffRole = "أخصائي تحاليل",
      workDateUtc = "2026-11-12",
      checkInUtcIso = "2026-11-12T08:00:00Z",
      checkInDisplayTime = "11:00:00 ص",
      checkInEpochMs = 1794470400000L,
      checkInSource = "timeapi.io (UTC)",
      checkOutUtcIso = "2026-11-12T15:30:00Z",
      checkOutDisplayTime = "06:30:00 م",
      checkOutEpochMs = 1794497400000L,
      checkOutSource = "Google Global Server (UTC)",
      status = AttendanceRecord.STATUS_CHECKED_OUT,
    )

    val checkInSeal1 = AttendanceReceiptRenderer.computeVerificationSeal(record, isCheckOut = false)
    val checkInSeal2 = AttendanceReceiptRenderer.computeVerificationSeal(record, isCheckOut = false)
    val checkOutSeal = AttendanceReceiptRenderer.computeVerificationSeal(record, isCheckOut = true)

    assertEquals(checkInSeal1, checkInSeal2)
    assertNotEquals(checkInSeal1, checkOutSeal)
    assertTrue(Regex("^[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}$").matches(checkInSeal1))
    assertTrue(Regex("^[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}$").matches(checkOutSeal))
  }

  @Test
  fun whatsappConfigStore_verifiesPasswordZazaStrictly() {
    assertTrue(WhatsAppConfigStore.verifyPassword("zaza"))
    assertFalse(WhatsAppConfigStore.verifyPassword("ZAZA"))
    assertFalse(WhatsAppConfigStore.verifyPassword("zaza "))
    assertFalse(WhatsAppConfigStore.verifyPassword("1234"))
    assertFalse(WhatsAppConfigStore.verifyPassword(""))
  }

  @Test
  fun whatsappConfigStore_normalizesValidInternationalNumbersAndRejectsInvalid() {
    assertEquals(
      "+9647701234567",
      WhatsAppConfigStore.normalizePhoneNumber("00964 770-123-4567").getOrThrow(),
    )
    assertEquals(
      "+9647830697575",
      WhatsAppConfigStore.normalizePhoneNumber("+9647830697575").getOrThrow(),
    )
    assertEquals(
      "+9647830697575",
      WhatsAppConfigStore.normalizePhoneNumber("9647830697575").getOrThrow(),
    )

    assertTrue(WhatsAppConfigStore.normalizePhoneNumber("").isFailure)
    assertTrue(WhatsAppConfigStore.normalizePhoneNumber("12345").isFailure)
    assertTrue(WhatsAppConfigStore.normalizePhoneNumber("+964abc12345").isFailure)
  }

  @Test
  fun whatsappConfigStore_extractsDigitsForWhatsAppJid() {
    assertEquals("9647701234567", WhatsAppConfigStore.toWhatsAppDigits("+9647701234567"))
    assertEquals(
      WhatsAppConfigStore.DEFAULT_PHONE_DIGITS,
      WhatsAppConfigStore.toWhatsAppDigits("   "),
    )
  }
}

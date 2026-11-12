package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AttendanceRecord
import com.example.data.model.GlobalTimeStamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabAttendanceRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun addStaffAndCheckInCheckOut_ownerSucceeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = LabAttendanceRepository(firestore)

    val staff = withTimeout(TIMEOUT_MS) {
      repo.addStaffMember("د. سارة العلي", "أخصائية تحاليل").getOrThrow()
    }
    assertTrue(staff.id.isNotBlank())

    val checkInTime = GlobalTimeStamp(
      epochMs = 1794470400000L,
      utcIso = "2026-11-12T08:00:00Z",
      workDateBaghdad = "2026-11-12",
      displayTimeBaghdad = "11:00:00 ص",
      displayTimeUtc = "08:00:00 UTC",
      sourceName = "timeapi.io (UTC)",
    )
    val checkedIn = withTimeout(TIMEOUT_MS) {
      repo.recordCheckIn(staff, checkInTime, notes = "وردية صباحية", whatsappSent = true).getOrThrow()
    }
    assertEquals(AttendanceRecord.STATUS_CHECKED_IN, checkedIn.status)

    val observed = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeAttendance(uid).first { list -> list.any { it.id == checkedIn.id } }
    }
    assertTrue(observed.any { it.id == checkedIn.id })

    val checkOutTime = GlobalTimeStamp(
      epochMs = 1794499200000L,
      utcIso = "2026-11-12T16:00:00Z",
      workDateBaghdad = "2026-11-12",
      displayTimeBaghdad = "07:00:00 م",
      displayTimeUtc = "16:00:00 UTC",
      sourceName = "timeapi.io (UTC)",
    )
    val checkedOut = withTimeout(TIMEOUT_MS) {
      repo.recordCheckOut(checkedIn, checkOutTime).getOrThrow()
    }
    assertEquals(AttendanceRecord.STATUS_CHECKED_OUT, checkedOut.status)
  }

  @Test
  fun observeAttendance_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = LabAttendanceRepository(firestore)
    withTimeout(TIMEOUT_MS) {
      repo.addStaffMember("أحمد كريم", "فني مختبر").getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    val exception = try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeAttendance(aliceUid).first()
      }
      null
    } catch (e: Exception) {
      e
    }
    val fsEx = checkNotNull(exception?.unwrapFirestoreException()) {
      "Expected FirebaseFirestoreException for cross-user read, got $exception"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
  }

  @Test
  fun observeStaff_unauthenticated_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = LabAttendanceRepository(firestore)

    val exception = try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeStaff("some_uid").first()
      }
      null
    } catch (e: Exception) {
      e
    }
    val fsEx = checkNotNull(exception?.unwrapFirestoreException()) {
      "Expected FirebaseFirestoreException for unauthenticated read, got $exception"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_lab@test.com"
    const val BOB_EMAIL = "bob_lab@test.com"
    const val TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}

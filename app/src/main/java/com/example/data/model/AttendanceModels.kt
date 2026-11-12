package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class StaffMember(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val role: String = "",
  val isActive: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AttendanceRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val staffId: String = "",
  val staffName: String = "",
  val staffRole: String = "",
  val workDateUtc: String = "",
  val checkInUtcIso: String = "",
  val checkInDisplayTime: String = "",
  val checkInEpochMs: Long = 0L,
  val checkInSource: String = "",
  val checkOutUtcIso: String = "",
  val checkOutDisplayTime: String = "",
  val checkOutEpochMs: Long = 0L,
  val checkOutSource: String = "",
  val status: String = STATUS_CHECKED_IN,
  val whatsappSent: Boolean = false,
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val isCheckedOut: Boolean
    get() = status == STATUS_CHECKED_OUT

  companion object {
    const val STATUS_CHECKED_IN = "checked_in"
    const val STATUS_CHECKED_OUT = "checked_out"
  }
}

data class GlobalTimeStamp(
  val epochMs: Long,
  val utcIso: String,
  val workDateBaghdad: String,
  val displayTimeBaghdad: String,
  val displayTimeUtc: String,
  val sourceName: String,
)

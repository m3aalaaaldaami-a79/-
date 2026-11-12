package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AttendanceRecord
import com.example.data.model.GlobalTimeStamp
import com.example.data.model.StaffMember
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class LabAttendanceRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google أولاً قبل الوصول إلى البيانات.")
  }

  fun observeStaff(userId: String): Flow<List<StaffMember>> = flow {
    val path = "users/$userId/staff"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("staff")
        .orderBy("name", Query.Direction.ASCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(StaffMember::class.java, ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val toLog = firestoreError ?: (error as? Exception)
          if (toLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(toLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observeAttendance(userId: String): Flow<List<AttendanceRecord>> = flow {
    val path = "users/$userId/attendance"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("attendance")
        .orderBy("checkInEpochMs", Query.Direction.DESCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(AttendanceRecord::class.java, ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val toLog = firestoreError ?: (error as? Exception)
          if (toLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(toLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun addStaffMember(name: String, role: String): Result<StaffMember> {
    val cleanName = name.trim()
    val cleanRole = role.trim()
    if (cleanName.isEmpty()) {
      return Result.failure(IllegalArgumentException("يرجى إدخال اسم الموظف"))
    }
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val staffId = "staff_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val docRef = db.collection("users").document(uid).collection("staff").document(staffId)
    val payload = mapOf(
      "userId" to uid,
      "name" to cleanName.take(80),
      "role" to cleanRole.take(60),
      "isActive" to true,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    return try {
      docRef.set(payload).await()
      val saved = docRef.get().await().toObject(StaffMember::class.java, ServerTimestampBehavior.ESTIMATE)
        ?: StaffMember(id = staffId, userId = uid, name = cleanName, role = cleanRole, isActive = true)
      Result.success(saved)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun toggleStaffActive(member: StaffMember): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    if (member.id.isBlank()) return Result.failure(IllegalArgumentException("معرف الموظف غير صالح"))
    val docRef = db.collection("users").document(uid).collection("staff").document(member.id)
    val updates = mapOf(
      "isActive" to !member.isActive,
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    return try {
      docRef.update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteStaffMember(staffId: String): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val docRef = db.collection("users").document(uid).collection("staff").document(staffId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun recordCheckIn(
    staff: StaffMember,
    globalTime: GlobalTimeStamp,
    notes: String = "",
    whatsappSent: Boolean = true,
  ): Result<AttendanceRecord> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    if (staff.id.isBlank()) return Result.failure(IllegalArgumentException("يرجى اختيار الموظف أولاً"))

    val recordId = "att_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val docRef = db.collection("users").document(uid).collection("attendance").document(recordId)
    val cleanNotes = notes.trim().take(200)

    val payload = mapOf(
      "userId" to uid,
      "staffId" to staff.id,
      "staffName" to staff.name.take(80),
      "staffRole" to staff.role.take(60),
      "workDateUtc" to globalTime.workDateBaghdad,
      "checkInUtcIso" to globalTime.utcIso,
      "checkInDisplayTime" to globalTime.displayTimeBaghdad,
      "checkInEpochMs" to globalTime.epochMs,
      "checkInSource" to globalTime.sourceName.take(60),
      "checkOutUtcIso" to "",
      "checkOutDisplayTime" to "",
      "checkOutEpochMs" to 0L,
      "checkOutSource" to "",
      "status" to AttendanceRecord.STATUS_CHECKED_IN,
      "whatsappSent" to whatsappSent,
      "notes" to cleanNotes,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )

    return try {
      docRef.set(payload).await()
      val saved = docRef.get().await().toObject(AttendanceRecord::class.java, ServerTimestampBehavior.ESTIMATE)
        ?: AttendanceRecord(
          id = recordId,
          userId = uid,
          staffId = staff.id,
          staffName = staff.name,
          staffRole = staff.role,
          workDateUtc = globalTime.workDateBaghdad,
          checkInUtcIso = globalTime.utcIso,
          checkInDisplayTime = globalTime.displayTimeBaghdad,
          checkInEpochMs = globalTime.epochMs,
          checkInSource = globalTime.sourceName,
          status = AttendanceRecord.STATUS_CHECKED_IN,
          whatsappSent = whatsappSent,
          notes = cleanNotes,
        )
      Result.success(saved)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun recordCheckOut(
    record: AttendanceRecord,
    globalTime: GlobalTimeStamp,
    notes: String = record.notes,
  ): Result<AttendanceRecord> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    if (record.id.isBlank()) return Result.failure(IllegalArgumentException("سجل الحضور غير صالح"))
    if (record.isCheckedOut) return Result.failure(IllegalStateException("تم تسجيل الانصراف لهذا الموظف مسبقاً"))

    val docRef = db.collection("users").document(uid).collection("attendance").document(record.id)
    val safeCheckOutEpoch = maxOf(globalTime.epochMs, record.checkInEpochMs)
    val cleanNotes = notes.trim().take(200)

    val updates = mapOf(
      "checkOutUtcIso" to globalTime.utcIso,
      "checkOutDisplayTime" to globalTime.displayTimeBaghdad,
      "checkOutEpochMs" to safeCheckOutEpoch,
      "checkOutSource" to globalTime.sourceName.take(60),
      "status" to AttendanceRecord.STATUS_CHECKED_OUT,
      "notes" to cleanNotes,
      "updatedAt" to FieldValue.serverTimestamp(),
    )

    return try {
      docRef.update(updates).await()
      val updated = record.copy(
        checkOutUtcIso = globalTime.utcIso,
        checkOutDisplayTime = globalTime.displayTimeBaghdad,
        checkOutEpochMs = safeCheckOutEpoch,
        checkOutSource = globalTime.sourceName,
        status = AttendanceRecord.STATUS_CHECKED_OUT,
        notes = cleanNotes,
      )
      Result.success(updated)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun markWhatsappSent(recordId: String): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val docRef = db.collection("users").document(uid).collection("attendance").document(recordId)
    return try {
      docRef.update(
        mapOf(
          "whatsappSent" to true,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteAttendanceRecord(recordId: String): Result<Unit> {
    val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
    val docRef = db.collection("users").document(uid).collection("attendance").document(recordId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }
}

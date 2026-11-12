package com.example.ui

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.WhatsAppConfigStore
import com.example.data.model.AttendanceRecord
import com.example.data.model.GlobalTimeStamp
import com.example.data.model.StaffMember
import com.example.data.remote.WhatsAppDispatcher
import com.example.data.remote.WorldTimeService
import com.example.data.repository.LabAttendanceRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

data class GlobalClockState(
  val isSyncing: Boolean = true,
  val anchorEpochMs: Long? = null,
  val anchorElapsedRealtimeMs: Long = 0L,
  val sourceName: String = "",
  val currentDisplay: GlobalTimeStamp? = null,
  val errorMessage: String? = null,
)

data class DeskFormState(
  val selectedStaffId: String? = null,
  val checkInFieldText: String = "",
  val checkInUtcSubtext: String = "",
  val checkOutFieldText: String = "",
  val checkOutUtcSubtext: String = "",
  val notes: String = "",
  val isRecordingCheckIn: Boolean = false,
  val isRecordingCheckOut: Boolean = false,
  val isAddingStaff: Boolean = false,
  val lastDispatchedRecord: AttendanceRecord? = null,
)

sealed interface AttendanceEvent {
  data class LaunchWhatsAppReceipt(
    val record: AttendanceRecord,
    val isCheckOut: Boolean,
    val selfieFile: File? = null,
    val targetPhoneDisplay: String = WhatsAppDispatcher.TARGET_PHONE_DISPLAY,
  ) : AttendanceEvent

  data class ShowBanner(val message: String, val isError: Boolean = false) : AttendanceEvent
}

class LabAttendanceViewModel(
  private val repository: LabAttendanceRepository,
  private val worldTimeService: WorldTimeService,
  private val configStore: WhatsAppConfigStore,
  private val currentUserId: String,
) : ViewModel() {

  val whatsappPhone: StateFlow<String> = configStore.whatsappPhoneFlow
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = WhatsAppConfigStore.DEFAULT_PHONE_DISPLAY,
    )

  val staffState: StateFlow<UiState<List<StaffMember>>> = repository.observeStaff(currentUserId)
    .map<List<StaffMember>, UiState<List<StaffMember>>> { list ->
      val currentSelected = _deskState.value.selectedStaffId
      if (currentSelected == null || list.none { it.id == currentSelected }) {
        val firstActive = list.firstOrNull { it.isActive } ?: list.firstOrNull()
        if (firstActive != null) {
          _deskState.update { it.copy(selectedStaffId = firstActive.id) }
        }
      }
      UiState.Success(list)
    }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val root = firestoreError ?: error
      Log.w(TAG, "Error observing staff", root)
      emit(UiState.Error(root.localizedMessage ?: "تعذر تحميل قائمة العاملين"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
      initialValue = UiState.Loading,
    )

  val attendanceState: StateFlow<UiState<List<AttendanceRecord>>> = repository.observeAttendance(currentUserId)
    .map<List<AttendanceRecord>, UiState<List<AttendanceRecord>>> { UiState.Success(it) }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val root = firestoreError ?: error
      Log.w(TAG, "Error observing attendance", root)
      emit(UiState.Error(root.localizedMessage ?: "تعذر تحميل سجلات الدوام"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
      initialValue = UiState.Loading,
    )

  private val _clockState = MutableStateFlow(GlobalClockState())
  val clockState: StateFlow<GlobalClockState> = _clockState.asStateFlow()

  private val _deskState = MutableStateFlow(DeskFormState())
  val deskState: StateFlow<DeskFormState> = _deskState.asStateFlow()

  private val _events = MutableSharedFlow<AttendanceEvent>()
  val events: SharedFlow<AttendanceEvent> = _events.asSharedFlow()

  init {
    syncGlobalClock()
    startMonotonicTicker()
  }

  private fun startMonotonicTicker() {
    viewModelScope.launch {
      while (isActive) {
        val state = _clockState.value
        if (state.anchorEpochMs != null) {
          val elapsed = (SystemClock.elapsedRealtime() - state.anchorElapsedRealtimeMs).coerceAtLeast(0L)
          val liveEpoch = state.anchorEpochMs + elapsed
          val formatted = WorldTimeService.formatGlobalTimeStamp(liveEpoch, state.sourceName)
          _clockState.update { it.copy(currentDisplay = formatted) }
        }
        delay(1000L)
      }
    }
  }

  fun syncGlobalClock() {
    viewModelScope.launch {
      _clockState.update { it.copy(isSyncing = true, errorMessage = null) }
      val result = worldTimeService.fetchAuthoritativeGlobalTime()
      result.fold(
        onSuccess = { stamp ->
          _clockState.value = GlobalClockState(
            isSyncing = false,
            anchorEpochMs = stamp.epochMs,
            anchorElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            sourceName = stamp.sourceName,
            currentDisplay = stamp,
            errorMessage = null,
          )
        },
        onFailure = { err ->
          _clockState.update {
            it.copy(
              isSyncing = false,
              errorMessage = err.message ?: "تعذر مزامنة الوقت العالمي",
            )
          }
        },
      )
    }
  }

  fun selectStaff(staffId: String) {
    _deskState.update {
      it.copy(
        selectedStaffId = staffId,
        checkInFieldText = "",
        checkInUtcSubtext = "",
        checkOutFieldText = "",
        checkOutUtcSubtext = "",
      )
    }
  }

  fun updateNotes(value: String) {
    _deskState.update { it.copy(notes = value.take(200)) }
  }

  fun addStaffMember(name: String, role: String, onAdded: (StaffMember) -> Unit = {}) {
    viewModelScope.launch {
      _deskState.update { it.copy(isAddingStaff = true) }
      val result = repository.addStaffMember(name, role)
      _deskState.update { it.copy(isAddingStaff = false) }
      result.fold(
        onSuccess = { member ->
          _deskState.update { it.copy(selectedStaffId = member.id) }
          onAdded(member)
          _events.emit(AttendanceEvent.ShowBanner("تمت إضافة الموظف: ${member.name}"))
        },
        onFailure = { err ->
          _events.emit(AttendanceEvent.ShowBanner(err.message ?: "فشلت إضافة الموظف", isError = true))
        },
      )
    }
  }

  fun toggleStaffActive(member: StaffMember) {
    viewModelScope.launch {
      repository.toggleStaffActive(member).onFailure { err ->
        _events.emit(AttendanceEvent.ShowBanner(err.message ?: "تعذر تحديث حالة الموظف", isError = true))
      }
    }
  }

  fun deleteStaffMember(staffId: String) {
    viewModelScope.launch {
      repository.deleteStaffMember(staffId).fold(
        onSuccess = {
          if (_deskState.value.selectedStaffId == staffId) {
            _deskState.update { it.copy(selectedStaffId = null) }
          }
          _events.emit(AttendanceEvent.ShowBanner("تم حذف الموظف من القائمة"))
        },
        onFailure = { err ->
          _events.emit(AttendanceEvent.ShowBanner(err.message ?: "تعذر حذف الموظف", isError = true))
        },
      )
    }
  }

  fun stampGlobalCheckIn(
    staffList: List<StaffMember>,
    selfieFile: File? = null,
    biometricMethod: String = "بصمة الجهاز",
  ) {
    val selectedId = _deskState.value.selectedStaffId
    val staff = staffList.firstOrNull { it.id == selectedId }
    if (staff == null) {
      viewModelScope.launch {
        _events.emit(AttendanceEvent.ShowBanner("يرجى اختيار اسم الموظف أو إضافته أولاً", isError = true))
      }
      return
    }
    if (_deskState.value.isRecordingCheckIn) return

    viewModelScope.launch {
      _deskState.update { it.copy(isRecordingCheckIn = true) }

      val timeResult = worldTimeService.fetchAuthoritativeGlobalTime()
      val globalTime = timeResult.getOrElse { err ->
        _deskState.update { it.copy(isRecordingCheckIn = false) }
        _clockState.update { it.copy(errorMessage = err.message) }
        _events.emit(
          AttendanceEvent.ShowBanner(
            err.message ?: "يتطلب تسجيل الحضور اتصالاً بالإنترنت لجلب الوقت العالمي",
            isError = true,
          )
        )
        return@launch
      }

      _clockState.value = GlobalClockState(
        isSyncing = false,
        anchorEpochMs = globalTime.epochMs,
        anchorElapsedRealtimeMs = SystemClock.elapsedRealtime(),
        sourceName = globalTime.sourceName,
        currentDisplay = globalTime,
        errorMessage = null,
      )

      val enrichedTime = globalTime.withBiometricTag(biometricMethod)

      _deskState.update {
        it.copy(
          checkInFieldText = "${enrichedTime.displayTimeBaghdad}  (${enrichedTime.workDateBaghdad})",
          checkInUtcSubtext = "${enrichedTime.utcIso} • ${enrichedTime.sourceName}",
          checkOutFieldText = "",
          checkOutUtcSubtext = "",
        )
      }

      val saveResult = repository.recordCheckIn(
        staff = staff,
        globalTime = enrichedTime,
        notes = _deskState.value.notes,
        whatsappSent = true,
      )

      _deskState.update { it.copy(isRecordingCheckIn = false) }

      saveResult.fold(
        onSuccess = { saved ->
          _deskState.update {
            it.copy(
              notes = "",
              lastDispatchedRecord = saved,
            )
          }
          _events.emit(
            AttendanceEvent.LaunchWhatsAppReceipt(
              record = saved,
              isCheckOut = false,
              selfieFile = selfieFile,
              targetPhoneDisplay = whatsappPhone.value,
            )
          )
          _events.emit(
            AttendanceEvent.ShowBanner("تم تثبيت حضور ${saved.staffName} وتجهيز صورة السيلفي لواتساب")
          )
        },
        onFailure = { err ->
          _events.emit(
            AttendanceEvent.ShowBanner(err.message ?: "تعذر حفظ تسجيل الحضور", isError = true)
          )
        },
      )
    }
  }

  fun stampGlobalCheckOut(
    activeRecord: AttendanceRecord?,
    selfieFile: File? = null,
    biometricMethod: String = "بصمة الجهاز",
  ) {
    if (activeRecord == null) {
      viewModelScope.launch {
        _events.emit(
          AttendanceEvent.ShowBanner("لا يوجد تسجيل حضور مفتوح لهذا الموظف لتسجيل انصرافه", isError = true)
        )
      }
      return
    }
    if (_deskState.value.isRecordingCheckOut) return

    viewModelScope.launch {
      _deskState.update { it.copy(isRecordingCheckOut = true) }

      val timeResult = worldTimeService.fetchAuthoritativeGlobalTime()
      val globalTime = timeResult.getOrElse { err ->
        _deskState.update { it.copy(isRecordingCheckOut = false) }
        _clockState.update { it.copy(errorMessage = err.message) }
        _events.emit(
          AttendanceEvent.ShowBanner(
            err.message ?: "يتطلب تسجيل الانصراف اتصالاً بالإنترنت لجلب الوقت العالمي",
            isError = true,
          )
        )
        return@launch
      }

      _clockState.value = GlobalClockState(
        isSyncing = false,
        anchorEpochMs = globalTime.epochMs,
        anchorElapsedRealtimeMs = SystemClock.elapsedRealtime(),
        sourceName = globalTime.sourceName,
        currentDisplay = globalTime,
        errorMessage = null,
      )

      val enrichedTime = globalTime.withBiometricTag(biometricMethod)

      _deskState.update {
        it.copy(
          checkOutFieldText = "${enrichedTime.displayTimeBaghdad}  (${enrichedTime.workDateBaghdad})",
          checkOutUtcSubtext = "${enrichedTime.utcIso} • ${enrichedTime.sourceName}",
        )
      }

      val saveResult = repository.recordCheckOut(
        record = activeRecord,
        globalTime = enrichedTime,
        notes = _deskState.value.notes.ifBlank { activeRecord.notes },
      )

      _deskState.update { it.copy(isRecordingCheckOut = false) }

      saveResult.fold(
        onSuccess = { updated ->
          _deskState.update {
            it.copy(
              notes = "",
              lastDispatchedRecord = updated,
            )
          }
          _events.emit(
            AttendanceEvent.LaunchWhatsAppReceipt(
              record = updated,
              isCheckOut = true,
              selfieFile = selfieFile,
              targetPhoneDisplay = whatsappPhone.value,
            )
          )
          _events.emit(
            AttendanceEvent.ShowBanner("تم تسجيل انصراف ${updated.staffName} وتجهيز صورة السيلفي لواتساب")
          )
        },
        onFailure = { err ->
          _events.emit(
            AttendanceEvent.ShowBanner(err.message ?: "تعذر حفظ تسجيل الانصراف", isError = true)
          )
        },
      )
    }
  }

  fun deleteRecord(recordId: String) {
    viewModelScope.launch {
      repository.deleteAttendanceRecord(recordId).fold(
        onSuccess = {
          _events.emit(AttendanceEvent.ShowBanner("تم حذف السجل"))
        },
        onFailure = { err ->
          _events.emit(AttendanceEvent.ShowBanner(err.message ?: "تعذر حذف السجل", isError = true))
        },
      )
    }
  }

  fun updateWhatsAppPhone(
    rawInput: String,
    onSuccess: () -> Unit,
    onValidationError: (String) -> Unit,
  ) {
    viewModelScope.launch {
      configStore.saveWhatsappPhone(rawInput).fold(
        onSuccess = { saved ->
          onSuccess()
          _events.emit(AttendanceEvent.ShowBanner("تم تحديث رقم واتساب الإشعارات إلى: $saved"))
        },
        onFailure = { err ->
          onValidationError(err.message ?: "الرقم المدخل غير صالح")
        },
      )
    }
  }

  fun resetWhatsAppPhone(onSuccess: () -> Unit) {
    viewModelScope.launch {
      val defaultNum = configStore.resetToDefault()
      onSuccess()
      _events.emit(AttendanceEvent.ShowBanner("تمت استعادة رقم واتساب الافتراضي: $defaultNum"))
    }
  }

  private fun GlobalTimeStamp.withBiometricTag(biometricMethod: String): GlobalTimeStamp {
    val cleanMethod = biometricMethod.trim()
    if (cleanMethod.isEmpty()) return this
    val combined = "$sourceName • $cleanMethod"
    return copy(sourceName = combined.take(58))
  }

  private companion object {
    const val TAG = "LabAttendanceVM"
    const val STOP_TIMEOUT_MS = 5000L
  }
}

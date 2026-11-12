package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AttendanceRecord
import com.example.data.model.StaffMember
import com.example.data.remote.AttendanceReceiptRenderer
import com.example.data.remote.WhatsAppDispatcher
import com.example.ui.DeskFormState
import com.example.ui.GlobalClockState
import com.example.ui.UiState
import com.example.ui.auth.BiometricAvailability
import com.example.ui.theme.ClockCyanGlow
import com.example.ui.theme.ClockDeepEnd
import com.example.ui.theme.ClockDeepMid
import com.example.ui.theme.ClockDeepStart
import com.example.ui.theme.ClockMintText
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusCheckedOutAmber
import com.example.ui.theme.WhatsAppGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AttendanceDeskTab(
  clockState: GlobalClockState,
  deskState: DeskFormState,
  staffState: UiState<List<StaffMember>>,
  attendanceState: UiState<List<AttendanceRecord>>,
  biometricAvailability: BiometricAvailability,
  whatsappPhone: String,
  onOpenWhatsAppSettings: () -> Unit,
  onOpenBiometricSettings: () -> Unit,
  onSyncClock: () -> Unit,
  onSelectStaff: (String) -> Unit,
  onOpenAddStaffDialog: () -> Unit,
  onNotesChange: (String) -> Unit,
  onCheckInClick: (List<StaffMember>) -> Unit,
  onCheckOutClick: (AttendanceRecord?) -> Unit,
  onSendWhatsAppReceipt: (AttendanceRecord, Boolean) -> Unit,
) {
  val staffList = (staffState as? UiState.Success)?.data.orEmpty()
  val activeStaff = staffList.filter { it.isActive }
  val allRecords = (attendanceState as? UiState.Success)?.data.orEmpty()

  val selectedStaff = staffList.firstOrNull { it.id == deskState.selectedStaffId }
  val activeOpenRecordForSelected = allRecords.firstOrNull {
    it.staffId == deskState.selectedStaffId && !it.isCheckedOut
  }

  val displayedCheckIn = deskState.checkInFieldText.ifBlank {
    activeOpenRecordForSelected?.let { "${it.checkInDisplayTime}  (${it.workDateUtc})" } ?: ""
  }
  val displayedCheckInSubtext = deskState.checkInUtcSubtext.ifBlank {
    activeOpenRecordForSelected?.let { "${it.checkInUtcIso} • ${it.checkInSource}" } ?: ""
  }

  val activeOnDuty = allRecords.filter { !it.isCheckedOut }
  val todayDate = clockState.currentDisplay?.workDateBaghdad
  val todayRecords = if (todayDate != null) {
    allRecords.filter { it.workDateUtc == todayDate }
  } else {
    allRecords.take(10)
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // 1. Modern Precision Global Clock Command Center
    item {
      GlobalClockCommandCard(
        clockState = clockState,
        activeOnDutyCount = activeOnDuty.size,
        onSyncClock = onSyncClock,
      )
    }

    // 2. Main Check-In / Check-Out Selfie Attendance Studio Card
    item {
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "تسجيل الحضور والانصراف بالبصمة",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.height(2.dp))
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Fingerprint,
                  contentDescription = null,
                  tint = WhatsAppGreen,
                  modifier = Modifier.size(15.dp),
                )
                Text(
                  text = "تحقق بالبصمة + سيلفي بالوقت والتاريخ لواتساب",
                  style = MaterialTheme.typography.bodySmall,
                  color = WhatsAppGreen,
                )
              }
            }
            FilledTonalButton(
              onClick = onOpenAddStaffDialog,
              shape = RoundedCornerShape(14.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("quick_add_staff_button"),
            ) {
              Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = "إضافة موظف",
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("موظف جديد", style = MaterialTheme.typography.labelLarge)
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

          // Device Biometric Readiness Banner
          BiometricReadinessChip(
            availability = biometricAvailability,
            onOpenSettings = onOpenBiometricSettings,
          )

          // Active WhatsApp Target Number Chip (password-protected edit)
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = WhatsAppGreen.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.28f)),
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .clickable(onClick = onOpenWhatsAppSettings)
              .testTag("change_whatsapp_number_chip"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Send,
                  contentDescription = null,
                  tint = WhatsAppGreen,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "واتساب المستلم: $whatsappPhone",
                  fontFamily = JetBrainsMonoFamily,
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.SemiBold,
                  color = WhatsAppGreen,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Edit,
                  contentDescription = null,
                  tint = WhatsAppGreen,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = "تغيير",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = WhatsAppGreen,
                )
              }
            }
          }

          // Staff Selection Strip with Avatar Initials
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "اختر الموظف",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (activeStaff.isNotEmpty()) {
                Text(
                  text = "${activeStaff.size} موظف نشط",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }

            when (staffState) {
              UiState.Loading -> {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                  Text("جاري تحميل الموظفين...", style = MaterialTheme.typography.bodyMedium)
                }
              }
              is UiState.Error -> {
                Text(
                  text = staffState.message,
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodyMedium,
                )
              }
              is UiState.Success -> {
                if (activeStaff.isEmpty()) {
                  Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable(onClick = onOpenAddStaffDialog),
                  ) {
                    Row(
                      modifier = Modifier.padding(14.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                      )
                      Text(
                        text = "لا يوجد عاملون مضافون بعد. اضغط هنا لإضافة أول موظف في المختبر.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                      )
                    }
                  }
                } else {
                  LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    items(activeStaff, key = { it.id }) { member ->
                      val isSelected = member.id == deskState.selectedStaffId
                      val isOnShift = activeOnDuty.any { it.staffId == member.id }
                      StaffSelectorCard(
                        member = member,
                        isSelected = isSelected,
                        isOnShift = isOnShift,
                        onClick = { onSelectStaff(member.id) },
                      )
                    }
                  }
                }
              }
            }
          }

          if (selectedStaff != null) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  StaffInitialsAvatar(name = selectedStaff.name, size = 34.dp)
                  Column {
                    Text(
                      text = selectedStaff.name,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                    )
                    if (selectedStaff.role.isNotBlank()) {
                      Text(
                        text = selectedStaff.role,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                }
                if (activeOpenRecordForSelected != null) {
                  Surface(
                    color = StatusActiveGreen.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, StatusActiveGreen.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(50),
                  ) {
                    Text(
                      text = "● حاضر الآن",
                      color = StatusActiveGreen,
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                  }
                }
              }
            }
          }

          // CHECK-IN FIELD + ADJACENT SELFIE CHECK-IN BUTTON
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedTextField(
                value = displayedCheckIn,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.check_in_label)) },
                placeholder = { Text("يُسجل بالبصمة والسيلفي...") },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = StatusActiveGreen,
                  )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                ),
                modifier = Modifier
                  .weight(1f)
                  .testTag("check_in_time_field"),
              )

              Button(
                onClick = { onCheckInClick(staffList) },
                enabled = selectedStaff != null && !deskState.isRecordingCheckIn && activeOpenRecordForSelected == null,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = StatusActiveGreen,
                  contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                modifier = Modifier
                  .height(56.dp)
                  .minimumInteractiveComponentSize()
                  .testTag("check_in_button"),
              ) {
                if (deskState.isRecordingCheckIn) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                  )
                } else {
                  Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = stringResource(R.string.check_in_button),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                  )
                }
              }
            }
            if (displayedCheckInSubtext.isNotBlank()) {
              Text(
                text = displayedCheckInSubtext,
                fontFamily = JetBrainsMonoFamily,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
              )
            }
          }

          // CHECK-OUT FIELD + ADJACENT SELFIE CHECK-OUT BUTTON
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedTextField(
                value = deskState.checkOutFieldText,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.check_out_label)) },
                placeholder = {
                  Text(
                    if (activeOpenRecordForSelected != null) "جاهز للانصراف بالبصمة" else "يُسجل بالبصمة والسيلفي..."
                  )
                },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = StatusCheckedOutAmber,
                  )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                ),
                modifier = Modifier
                  .weight(1f)
                  .testTag("check_out_time_field"),
              )

              Button(
                onClick = { onCheckOutClick(activeOpenRecordForSelected) },
                enabled = activeOpenRecordForSelected != null && !deskState.isRecordingCheckOut,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = StatusCheckedOutAmber,
                  contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                modifier = Modifier
                  .height(56.dp)
                  .minimumInteractiveComponentSize()
                  .testTag("check_out_button"),
              ) {
                if (deskState.isRecordingCheckOut) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                  )
                } else {
                  Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = stringResource(R.string.check_out_button),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                  )
                }
              }
            }
            if (deskState.checkOutUtcSubtext.isNotBlank()) {
              Text(
                text = deskState.checkOutUtcSubtext,
                fontFamily = JetBrainsMonoFamily,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
              )
            }
          }

          OutlinedTextField(
            value = deskState.notes,
            onValueChange = onNotesChange,
            label = { Text("ملاحظات (اختياري)") },
            placeholder = { Text("مثال: وردية مسائية أو تكليف إضافي") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
              unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("notes_input"),
          )

          // Latest Dispatched Selfie Receipt Preview Banner
          AnimatedVisibility(visible = deskState.lastDispatchedRecord != null) {
            deskState.lastDispatchedRecord?.let { latest ->
              DispatchedReceiptBanner(
                record = latest,
                whatsappPhone = whatsappPhone,
                onResend = { onSendWhatsAppReceipt(latest, latest.isCheckedOut) },
              )
            }
          }
        }
      }
    }

    // 3. Today's Active & Recent Shifts Section
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "دوام اليوم والحضور المباشر (${todayRecords.size})",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
        )
        if (activeOnDuty.isNotEmpty()) {
          Surface(
            color = StatusActiveGreen.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, StatusActiveGreen.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(50),
          ) {
            Text(
              text = "${activeOnDuty.size} في المختبر",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = StatusActiveGreen,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
          }
        }
      }
    }

    if (todayRecords.isEmpty()) {
      item {
        Card(
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
          ),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(48.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Default.Info,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(24.dp),
                )
              }
            }
            Text(
              text = "لم يتم تسجيل حضور اليوم بعد",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "اختر الموظف واضغط على «حضور بالبصمة» للتحقق ببصمة الجهاز ثم التقاط السيلفي وإرساله عبر واتساب.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(todayRecords, key = { it.id }) { record ->
        TodayAttendanceCard(
          record = record,
          onQuickCheckOut = { onCheckOutClick(record) },
          onSendWhatsApp = {
            onSendWhatsAppReceipt(record, record.isCheckedOut)
          },
        )
      }
    }
  }
}

@Composable
private fun GlobalClockCommandCard(
  clockState: GlobalClockState,
  activeOnDutyCount: Int,
  onSyncClock: () -> Unit,
) {
  val stamp = clockState.currentDisplay
  val infiniteTransition = rememberInfiniteTransition(label = "live_beacon")
  val beaconAlpha by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 0.28f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "beacon_alpha",
  )

  Surface(
    shape = RoundedCornerShape(26.dp),
    border = BorderStroke(1.dp, ClockCyanGlow.copy(alpha = 0.35f)),
    shadowElevation = 4.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(
      modifier = Modifier
        .background(
          Brush.linearGradient(
            colors = listOf(ClockDeepStart, ClockDeepMid, ClockDeepEnd),
          )
        ),
    ) {
      // Decorative subtle orbital radar arcs
      Canvas(modifier = Modifier.matchParentSize()) {
        val ringCenter = Offset(size.width * 0.14f, size.height * 0.24f)
        drawCircle(
          color = ClockCyanGlow.copy(alpha = 0.08f),
          radius = size.minDimension * 0.55f,
          center = ringCenter,
          style = Stroke(width = 1.5.dp.toPx()),
        )
        drawCircle(
          color = ClockCyanGlow.copy(alpha = 0.05f),
          radius = size.minDimension * 0.85f,
          center = ringCenter,
          style = Stroke(width = 1.dp.toPx()),
        )
      }

      Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // Top Header Row: Live Beacon + Title + Sync Action
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Surface(
              color = Color.White.copy(alpha = 0.12f),
              border = BorderStroke(1.dp, ClockCyanGlow.copy(alpha = 0.35f)),
              shape = RoundedCornerShape(50),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .alpha(beaconAlpha)
                    .clip(CircleShape)
                    .background(Color(0xFF34D399))
                )
                Text(
                  text = "مباشر",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = ClockMintText,
                )
              }
            }

            Column {
              Text(
                text = stringResource(R.string.global_time_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
              )
              Text(
                text = if (activeOnDutyCount > 0) "$activeOnDutyCount في الدوام الآن" else "توقيت بغداد والعالمي",
                style = MaterialTheme.typography.labelSmall,
                color = ClockMintText.copy(alpha = 0.85f),
              )
            }
          }

          Surface(
            color = Color.White.copy(alpha = 0.12f),
            shape = CircleShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
          ) {
            IconButton(
              onClick = onSyncClock,
              enabled = !clockState.isSyncing,
              modifier = Modifier
                .size(48.dp)
                .testTag("sync_global_clock_button"),
            ) {
              if (clockState.isSyncing) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  color = ClockCyanGlow,
                  strokeWidth = 2.dp,
                )
              } else {
                Icon(
                  imageVector = Icons.Default.Refresh,
                  contentDescription = stringResource(R.string.global_time_sync),
                  tint = Color.White,
                )
              }
            }
          }
        }

        if (stamp != null) {
          // Giant Tabular Clock + Date Pill
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = stamp.displayTimeBaghdad,
                style = TextStyle(
                  fontFamily = JetBrainsMonoFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 34.sp,
                  lineHeight = 40.sp,
                  fontFeatureSettings = "tnum",
                  color = Color.White,
                ),
              )
              Text(
                text = "توقيت بغداد المعتمد (UTC+3)",
                style = MaterialTheme.typography.bodySmall,
                color = ClockMintText.copy(alpha = 0.85f),
              )
            }

            Surface(
              color = Color.White.copy(alpha = 0.13f),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
              shape = RoundedCornerShape(16.dp),
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.Event,
                    contentDescription = null,
                    tint = ClockCyanGlow,
                    modifier = Modifier.size(14.dp),
                  )
                  Text(
                    text = "التاريخ",
                    style = MaterialTheme.typography.labelSmall,
                    color = ClockCyanGlow,
                  )
                }
                Text(
                  text = stamp.workDateBaghdad,
                  fontFamily = JetBrainsMonoFamily,
                  fontWeight = FontWeight.Bold,
                  style = MaterialTheme.typography.titleSmall,
                  color = Color.White,
                )
              }
            }
          }

          // Dual Telemetry Glass Footer Strip
          Surface(
            color = Color.Black.copy(alpha = 0.22f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Default.VerifiedUser,
                  contentDescription = null,
                  tint = ClockCyanGlow,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "${stamp.sourceName} • مستقل عن الجهاز",
                  style = MaterialTheme.typography.bodySmall,
                  color = ClockMintText,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Surface(
                color = ClockCyanGlow.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = ClockCyanGlow,
                    modifier = Modifier.size(12.dp),
                  )
                  Text(
                    text = stamp.displayTimeUtc,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                  )
                }
              }
            }
          }
        } else if (clockState.errorMessage != null) {
          Text(
            text = clockState.errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFFDE68A),
          )
        } else {
          Text(
            text = "جاري المزامنة مع خادم الوقت العالمي...",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
          )
        }
      }
    }
  }
}

@Composable
private fun StaffSelectorCard(
  member: StaffMember,
  isSelected: Boolean,
  isOnShift: Boolean,
  onClick: () -> Unit,
) {
  val containerColor = if (isSelected) {
    MaterialTheme.colorScheme.primaryContainer
  } else {
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
  }
  val borderColor = if (isSelected) {
    MaterialTheme.colorScheme.primary
  } else {
    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
  }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = containerColor,
    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
    modifier = Modifier
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .testTag("staff_chip_${member.id}"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box {
        StaffInitialsAvatar(name = member.name, size = 30.dp)
        if (isOnShift) {
          Box(
            modifier = Modifier
              .size(10.dp)
              .align(Alignment.BottomEnd)
              .clip(CircleShape)
              .background(StatusActiveGreen)
              .border(1.5.dp, Color.White, CircleShape)
          )
        }
      }
      Column {
        Text(
          text = member.name,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
          color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (member.role.isNotBlank()) {
          Text(
            text = member.role,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun DispatchedReceiptBanner(
  record: AttendanceRecord,
  whatsappPhone: String,
  onResend: () -> Unit,
) {
  val context = LocalContext.current
  val receiptFile = AttendanceReceiptRenderer.getReceiptFile(context, record.id, record.isCheckedOut)
  val thumbBitmap by produceState<ImageBitmap?>(initialValue = null, record.id, record.isCheckedOut, record.checkOutEpochMs) {
    value = withContext(Dispatchers.IO) {
      if (receiptFile.exists()) {
        val opts = BitmapFactory.Options().apply { inSampleSize = 6 }
        BitmapFactory.decodeFile(receiptFile.absolutePath, opts)?.asImageBitmap()
      } else {
        null
      }
    }
  }

  val actionLabel = if (record.isCheckedOut) "انصراف" else "حضور"
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = WhatsAppGreen.copy(alpha = 0.10f),
    border = BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.35f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (thumbBitmap != null) {
        Image(
          bitmap = thumbBitmap!!,
          contentDescription = "معاينة بطاقة السيلفي",
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .size(width = 46.dp, height = 58.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, WhatsAppGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
        )
      } else {
        Icon(
          imageVector = Icons.Default.CheckCircle,
          contentDescription = null,
          tint = WhatsAppGreen,
          modifier = Modifier.size(24.dp),
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "تم تثبيت $actionLabel ${record.staffName}",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "بطاقة صورة جاهزة لواتساب ($whatsappPhone)",
          style = MaterialTheme.typography.bodySmall,
          color = WhatsAppGreen,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      OutlinedButton(
        onClick = onResend,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.5f)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("resend_whatsapp_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Send,
          contentDescription = null,
          tint = WhatsAppGreen,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("إرسال", color = WhatsAppGreen, style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

@Composable
private fun TodayAttendanceCard(
  record: AttendanceRecord,
  onQuickCheckOut: () -> Unit,
  onSendWhatsApp: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          StaffInitialsAvatar(name = record.staffName, size = 38.dp)
          Column {
            Text(
              text = record.staffName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            if (record.staffRole.isNotBlank()) {
              Text(
                text = record.staffRole,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        Surface(
          color = if (record.isCheckedOut) {
            MaterialTheme.colorScheme.surfaceVariant
          } else {
            StatusActiveGreen.copy(alpha = 0.14f)
          },
          shape = RoundedCornerShape(50),
        ) {
          Text(
            text = if (record.isCheckedOut) "منصرف" else "● حاضر الآن",
            color = if (record.isCheckedOut) MaterialTheme.colorScheme.onSurfaceVariant else StatusActiveGreen,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        TimePill(
          label = "الحضور",
          time = record.checkInDisplayTime,
          utc = record.checkInUtcIso.substringAfter("T").ifBlank { record.checkInUtcIso },
          accentColor = StatusActiveGreen,
          modifier = Modifier.weight(1f),
        )
        TimePill(
          label = "الانصراف",
          time = record.checkOutDisplayTime.ifBlank { "لم يسجل بعد" },
          utc = record.checkOutUtcIso.substringAfter("T").ifBlank { "--:--:--Z" },
          accentColor = if (record.isCheckedOut) StatusCheckedOutAmber else MaterialTheme.colorScheme.outline,
          modifier = Modifier.weight(1f),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
          modifier = Modifier.weight(1f),
        ) {
          if (record.checkInSource.contains("بصمة") || record.checkOutSource.contains("بصمة")) {
            Icon(
              imageVector = Icons.Default.Fingerprint,
              contentDescription = "موثق ببصمة الجهاز",
              tint = StatusActiveGreen,
              modifier = Modifier.size(15.dp),
            )
          }
          Text(
            text = "المصدر: ${record.checkInSource}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = onSendWhatsApp,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.45f)),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = "إرسال عبر واتساب",
              tint = WhatsAppGreen,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("واتساب", color = WhatsAppGreen, style = MaterialTheme.typography.labelMedium)
          }

          if (!record.isCheckedOut) {
            Button(
              onClick = onQuickCheckOut,
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = StatusCheckedOutAmber,
                contentColor = Color.White,
              ),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("card_checkout_${record.id}"),
            ) {
              Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("انصراف", style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BiometricReadinessChip(
  availability: BiometricAvailability,
  onOpenSettings: () -> Unit,
) {
  val (bg, border, fg, label, clickable) = when (availability) {
    BiometricAvailability.READY -> Tuple5(
      StatusActiveGreen.copy(alpha = 0.10f),
      StatusActiveGreen.copy(alpha = 0.30f),
      StatusActiveGreen,
      "بصمة الجهاز مفعّلة ومعتمدة لتسجيل الحضور والانصراف",
      false,
    )
    BiometricAvailability.NOT_ENROLLED -> Tuple5(
      StatusCheckedOutAmber.copy(alpha = 0.12f),
      StatusCheckedOutAmber.copy(alpha = 0.35f),
      StatusCheckedOutAmber,
      "لا توجد بصمة مسجلة — اضغط لإضافة بصمة في إعدادات الجهاز",
      true,
    )
    BiometricAvailability.UNAVAILABLE -> Tuple5(
      MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
      MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
      MaterialTheme.colorScheme.onSurfaceVariant,
      "مستشعر البصمة غير متاح — يتاح التحقق بقفل الجهاز أو السيلفي",
      false,
    )
  }

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = bg,
    border = BorderStroke(1.dp, border),
    modifier = Modifier
      .fillMaxWidth()
      .then(if (clickable) Modifier.clickable(onClick = onOpenSettings) else Modifier),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Default.Fingerprint,
        contentDescription = null,
        tint = fg,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = fg,
      )
    }
  }
}

private data class Tuple5<A, B, C, D, E>(
  val first: A,
  val second: B,
  val third: C,
  val fourth: D,
  val fifth: E,
)

@Composable
internal fun StaffInitialsAvatar(
  name: String,
  size: androidx.compose.ui.unit.Dp,
) {
  val initials = name.trim()
    .split(Regex("\\s+"))
    .filter { it.isNotBlank() }
    .take(2)
    .joinToString("") { it.first().toString() }
    .ifBlank { "م" }

  Surface(
    shape = CircleShape,
    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    modifier = Modifier.size(size),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = initials,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
private fun TimePill(
  label: String,
  time: String,
  utc: String,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = accentColor,
      )
      Text(
        text = time,
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = utc,
        fontFamily = JetBrainsMonoFamily,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

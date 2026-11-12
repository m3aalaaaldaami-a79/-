package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AttendanceRecord
import com.example.data.remote.AttendanceReceiptRenderer
import com.example.ui.UiState
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusCheckedOutAmber
import com.example.ui.theme.WhatsAppGreen

private enum class HistoryFilter(val label: String) {
  ALL("الكل"),
  ACTIVE("حاضر الآن"),
  COMPLETED("منصرف"),
}

@Composable
fun AttendanceHistoryTab(
  attendanceState: UiState<List<AttendanceRecord>>,
  onSendWhatsAppReceipt: (AttendanceRecord, Boolean) -> Unit,
  onDeleteRecord: (String) -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }
  var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
  var pendingDeleteRecord by remember { mutableStateOf<AttendanceRecord?>(null) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
  ) {
    Spacer(modifier = Modifier.padding(top = 12.dp))

    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      label = { Text("بحث باسم الموظف أو التاريخ") },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
      singleLine = true,
      shape = RoundedCornerShape(16.dp),
      colors = OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
      ),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("history_search_input"),
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      HistoryFilter.entries.forEach { filter ->
        FilterChip(
          selected = selectedFilter == filter,
          onClick = { selectedFilter = filter },
          label = { Text(filter.label) },
          shape = RoundedCornerShape(50),
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ),
        )
      }
    }

    when (attendanceState) {
      UiState.Loading -> {
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          CircularProgressIndicator()
        }
      }
      is UiState.Error -> {
        Text(
          text = attendanceState.message,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(16.dp),
        )
      }
      is UiState.Success -> {
        val filtered = attendanceState.data.filter { rec ->
          val matchesFilter = when (selectedFilter) {
            HistoryFilter.ALL -> true
            HistoryFilter.ACTIVE -> !rec.isCheckedOut
            HistoryFilter.COMPLETED -> rec.isCheckedOut
          }
          val q = searchQuery.trim()
          val matchesQuery = q.isEmpty() ||
            rec.staffName.contains(q, ignoreCase = true) ||
            rec.workDateUtc.contains(q, ignoreCase = true)
          matchesFilter && matchesQuery
        }

        if (filtered.isEmpty()) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(
              imageVector = Icons.Default.History,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(44.dp),
            )
            Spacer(modifier = Modifier.padding(6.dp))
            Text(
              text = "لا توجد سجلات مطابقة",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = "ستظهر هنا جميع سجلات الحضور والانصراف الموثقة بالوقت العالمي.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(filtered, key = { it.id }) { rec ->
              HistoryRecordCard(
                record = rec,
                onSendWhatsApp = {
                  onSendWhatsAppReceipt(rec, rec.isCheckedOut)
                },
                onRequestDelete = { pendingDeleteRecord = rec },
              )
            }
          }
        }
      }
    }
  }

  pendingDeleteRecord?.let { rec ->
    AlertDialog(
      onDismissRequest = { pendingDeleteRecord = null },
      title = { Text("حذف سجل الدوام") },
      text = { Text("هل تريد حذف سجل ${rec.staffName} بتاريخ ${rec.workDateUtc}؟") },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteRecord(rec.id)
            pendingDeleteRecord = null
          }
        ) {
          Text("حذف", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteRecord = null }) {
          Text("إلغاء")
        }
      },
    )
  }
}

@Composable
private fun HistoryRecordCard(
  record: AttendanceRecord,
  onSendWhatsApp: () -> Unit,
  onRequestDelete: () -> Unit,
) {
  val durationText = remember(record.checkInEpochMs, record.checkOutEpochMs) {
    if (record.isCheckedOut && record.checkOutEpochMs >= record.checkInEpochMs) {
      val diffMinutes = (record.checkOutEpochMs - record.checkInEpochMs) / 60_000L
      val hours = diffMinutes / 60L
      val mins = diffMinutes % 60L
      "${hours} س ${mins} د"
    } else {
      null
    }
  }
  val seal = remember(record.id, record.checkInEpochMs, record.checkOutEpochMs, record.isCheckedOut) {
    AttendanceReceiptRenderer.computeVerificationSeal(record, record.isCheckedOut)
  }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
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
            )
            Text(
              text = "${record.workDateUtc}  •  SEAL: $seal",
              fontFamily = JetBrainsMonoFamily,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        if (durationText != null) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(50),
          ) {
            Text(
              text = durationText,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
        } else {
          Surface(
            color = StatusActiveGreen.copy(alpha = 0.14f),
            shape = RoundedCornerShape(50),
          ) {
            Text(
              text = "● حاضر الآن",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = StatusActiveGreen,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
          modifier = Modifier.weight(1f),
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Text("الحضور", style = MaterialTheme.typography.labelMedium, color = StatusActiveGreen, fontWeight = FontWeight.Bold)
            Text(
              text = record.checkInDisplayTime,
              fontFamily = JetBrainsMonoFamily,
              fontWeight = FontWeight.SemiBold,
              style = MaterialTheme.typography.bodyMedium,
            )
            Text(
              text = record.checkInUtcIso,
              fontFamily = JetBrainsMonoFamily,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
          modifier = Modifier.weight(1f),
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Text(
              "الانصراف",
              style = MaterialTheme.typography.labelMedium,
              color = StatusCheckedOutAmber,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = record.checkOutDisplayTime.ifBlank { "—" },
              fontFamily = JetBrainsMonoFamily,
              fontWeight = FontWeight.SemiBold,
              style = MaterialTheme.typography.bodyMedium,
            )
            if (record.checkOutUtcIso.isNotBlank()) {
              Text(
                text = record.checkOutUtcIso,
                fontFamily = JetBrainsMonoFamily,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      if (record.notes.isNotBlank()) {
        Text(
          text = "ملاحظة: ${record.notes}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (record.checkInSource.contains("بصمة") || record.checkOutSource.contains("بصمة")) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Fingerprint,
            contentDescription = null,
            tint = StatusActiveGreen,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "موثق ببصمة جهاز الموبايل",
            style = MaterialTheme.typography.labelSmall,
            color = StatusActiveGreen,
            fontWeight = FontWeight.Medium,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = onSendWhatsApp,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.45f)),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = WhatsAppGreen,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "إرسال البطاقة عبر واتساب",
            color = WhatsAppGreen,
            style = MaterialTheme.typography.labelMedium,
          )
        }

        IconButton(
          onClick = onRequestDelete,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = Icons.Default.DeleteOutline,
            contentDescription = "حذف السجل",
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}

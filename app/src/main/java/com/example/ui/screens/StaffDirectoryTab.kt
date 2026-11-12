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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.example.data.model.StaffMember
import com.example.ui.UiState

@Composable
fun StaffDirectoryTab(
  staffState: UiState<List<StaffMember>>,
  onOpenAddStaffDialog: () -> Unit,
  onToggleActive: (StaffMember) -> Unit,
  onDeleteStaff: (String) -> Unit,
  onSelectForCheckIn: (StaffMember) -> Unit,
) {
  var pendingDelete by remember { mutableStateOf<StaffMember?>(null) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(
          text = "طاقم العمل في المختبر",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "إدارة أسماء العاملين وتفعيلهم لتسجيل الدوام",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Button(
        onClick = onOpenAddStaffDialog,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("directory_add_staff_button"),
      ) {
        Icon(
          imageVector = Icons.Default.PersonAdd,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("إضافة")
      }
    }

    when (staffState) {
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
          text = staffState.message,
          color = MaterialTheme.colorScheme.error,
        )
      }
      is UiState.Success -> {
        if (staffState.data.isEmpty()) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(
              imageVector = Icons.Default.Group,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.padding(6.dp))
            Text("لا يوجد موظفون مسجلون", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "أضف أسماء العاملين في مختبرك لكي تتمكن من تسجيل حضورهم وانصرافهم بالسيلفي.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            items(staffState.data, key = { it.id }) { member ->
              Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    StaffInitialsAvatar(name = member.name, size = 40.dp)
                    Column {
                      Text(
                        text = member.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                      )
                      Text(
                        text = member.role.ifBlank { "موظف مختبر" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }

                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    if (member.isActive) {
                      FilledTonalButton(
                        onClick = { onSelectForCheckIn(member) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.minimumInteractiveComponentSize(),
                      ) {
                        Icon(
                          imageVector = Icons.Default.Schedule,
                          contentDescription = null,
                          modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("دوام", style = MaterialTheme.typography.labelMedium)
                      }
                    }

                    Switch(
                      checked = member.isActive,
                      onCheckedChange = { onToggleActive(member) },
                    )

                    IconButton(
                      onClick = { pendingDelete = member },
                      modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                      Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "حذف الموظف",
                        tint = MaterialTheme.colorScheme.error,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  pendingDelete?.let { member ->
    AlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text("حذف الموظف") },
      text = { Text("هل تريد إزالة ${member.name} من قائمة المختبر؟") },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteStaff(member.id)
            pendingDelete = null
          }
        ) {
          Text("حذف", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDelete = null }) {
          Text("إلغاء")
        }
      },
    )
  }
}

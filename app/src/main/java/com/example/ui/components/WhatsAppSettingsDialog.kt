package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.WhatsAppConfigStore
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.WhatsAppGreen

@Composable
fun WhatsAppSettingsDialog(
  currentPhone: String,
  onSavePhone: (rawInput: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
  onResetDefault: (onSuccess: () -> Unit) -> Unit,
  onDismiss: () -> Unit,
) {
  var isUnlocked by remember { mutableStateOf(false) }
  var passwordInput by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var passwordError by remember { mutableStateOf<String?>(null) }

  var phoneInput by remember(currentPhone) { mutableStateOf(currentPhone) }
  var phoneError by remember { mutableStateOf<String?>(null) }
  var isSaving by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = { if (!isSaving) onDismiss() },
    icon = {
      Icon(
        imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
        contentDescription = null,
        tint = if (isUnlocked) WhatsAppGreen else MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(30.dp),
      )
    },
    title = {
      Text(
        text = if (isUnlocked) "تغيير رقم الواتساب المستلم" else "حماية إعدادات واتساب",
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      if (!isUnlocked) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "أدخل كلمة مرور التطبيق للوصول إلى تعديل رقم واتساب الإشعارات.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(
            value = passwordInput,
            onValueChange = {
              passwordInput = it
              passwordError = null
            },
            label = { Text("كلمة المرور") },
            placeholder = { Text("أدخل كلمة المرور") },
            singleLine = true,
            isError = passwordError != null,
            visualTransformation = if (passwordVisible) {
              VisualTransformation.None
            } else {
              PasswordVisualTransformation()
            },
            trailingIcon = {
              IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                  imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                  contentDescription = if (passwordVisible) "إخفاء" else "إظهار",
                )
              }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("whatsapp_admin_password_input"),
          )
          if (passwordError != null) {
            Text(
              text = passwordError!!,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Surface(
            color = WhatsAppGreen.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "الرقم الحالي:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = currentPhone,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                color = WhatsAppGreen,
              )
            }
          }

          OutlinedTextField(
            value = phoneInput,
            onValueChange = {
              phoneInput = it
              phoneError = null
            },
            label = { Text("رقم الواتساب الجديد (مع رمز الدولة)") },
            placeholder = { Text("+9647XXXXXXXXX") },
            leadingIcon = {
              Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                tint = WhatsAppGreen,
              )
            },
            singleLine = true,
            isError = phoneError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("whatsapp_new_phone_input"),
          )

          if (phoneError != null) {
            Text(
              text = phoneError!!,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }

          if (currentPhone != WhatsAppConfigStore.DEFAULT_PHONE_DISPLAY) {
            TextButton(
              onClick = {
                isSaving = true
                onResetDefault {
                  isSaving = false
                  onDismiss()
                }
              },
              enabled = !isSaving,
              modifier = Modifier.testTag("whatsapp_reset_default_button"),
            ) {
              Icon(
                imageVector = Icons.Default.RestartAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("استعادة الرقم الافتراضي (${WhatsAppConfigStore.DEFAULT_PHONE_DISPLAY})")
            }
          }
        }
      }
    },
    confirmButton = {
      if (!isUnlocked) {
        Button(
          onClick = {
            if (WhatsAppConfigStore.verifyPassword(passwordInput)) {
              passwordError = null
              isUnlocked = true
            } else {
              passwordError = "كلمة المرور غير صحيحة"
            }
          },
          enabled = passwordInput.isNotEmpty(),
          modifier = Modifier.testTag("whatsapp_unlock_button"),
        ) {
          Text("فتح الإعدادات")
        }
      } else {
        Button(
          onClick = {
            isSaving = true
            onSavePhone(
              phoneInput,
              {
                isSaving = false
                onDismiss()
              },
              { err ->
                isSaving = false
                phoneError = err
              },
            )
          },
          enabled = !isSaving && phoneInput.isNotBlank(),
          colors = ButtonDefaults.buttonColors(
            containerColor = WhatsAppGreen,
            contentColor = Color.White,
          ),
          modifier = Modifier.testTag("whatsapp_save_phone_button"),
        ) {
          Text(if (isSaving) "جاري الحفظ..." else "حفظ الرقم الجديد")
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !isSaving,
      ) {
        Text("إلغاء")
      }
    },
  )
}

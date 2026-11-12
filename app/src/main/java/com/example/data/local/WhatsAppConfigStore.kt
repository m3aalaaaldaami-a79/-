package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.labSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "lab_settings")

class WhatsAppConfigStore(private val context: Context) {

  val whatsappPhoneFlow: Flow<String> = context.labSettingsDataStore.data.map { prefs ->
    prefs[KEY_WHATSAPP_PHONE]?.takeIf { it.isNotBlank() } ?: DEFAULT_PHONE_DISPLAY
  }

  suspend fun saveWhatsappPhone(rawInput: String): Result<String> {
    val normalized = normalizePhoneNumber(rawInput).getOrElse { return Result.failure(it) }
    context.labSettingsDataStore.edit { prefs ->
      prefs[KEY_WHATSAPP_PHONE] = normalized
    }
    return Result.success(normalized)
  }

  suspend fun resetToDefault(): String {
    context.labSettingsDataStore.edit { prefs ->
      prefs[KEY_WHATSAPP_PHONE] = DEFAULT_PHONE_DISPLAY
    }
    return DEFAULT_PHONE_DISPLAY
  }

  companion object {
    const val DEFAULT_PHONE_DISPLAY = "+9647830697575"
    const val DEFAULT_PHONE_DIGITS = "9647830697575"
    private const val ADMIN_PASSWORD = "zaza"
    private val KEY_WHATSAPP_PHONE = stringPreferencesKey("whatsapp_phone_display")

    fun verifyPassword(input: String): Boolean = input == ADMIN_PASSWORD

    fun normalizePhoneNumber(rawInput: String): Result<String> {
      val trimmed = rawInput.trim()
        .replace(" ", "")
        .replace("-", "")
        .replace("(", "")
        .replace(")", "")
      if (trimmed.isEmpty()) {
        return Result.failure(IllegalArgumentException("يرجى إدخال رقم الواتساب"))
      }

      val withoutIntlPrefix = when {
        trimmed.startsWith("+") -> trimmed.drop(1)
        trimmed.startsWith("00") -> trimmed.drop(2)
        else -> trimmed
      }

      if (!withoutIntlPrefix.all { it.isDigit() }) {
        return Result.failure(IllegalArgumentException("يجب أن يحتوي الرقم على أرقام فقط مع رمز الدولة"))
      }

      if (withoutIntlPrefix.length !in 8..15) {
        return Result.failure(IllegalArgumentException("أدخل رقماً دولياً صحيحاً (مثال: +9647830697575)"))
      }

      return Result.success("+$withoutIntlPrefix")
    }

    fun toWhatsAppDigits(displayPhone: String): String {
      return displayPhone.filter { it.isDigit() }.ifBlank { DEFAULT_PHONE_DIGITS }
    }
  }
}

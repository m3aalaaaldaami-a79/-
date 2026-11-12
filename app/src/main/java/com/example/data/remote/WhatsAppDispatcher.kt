package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.local.WhatsAppConfigStore
import com.example.data.model.AttendanceRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object WhatsAppDispatcher {
  const val TARGET_PHONE_DISPLAY = WhatsAppConfigStore.DEFAULT_PHONE_DISPLAY

  fun buildCheckInMessage(record: AttendanceRecord): String = buildString {
    appendLine("🔬 *إشعار تثبيت حضور - المختبر*")
    appendLine("👤 الموظف: ${record.staffName}")
    if (record.staffRole.isNotBlank()) {
      appendLine("🧪 الصفة: ${record.staffRole}")
    }
    appendLine("📅 التاريخ: ${record.workDateUtc}")
    appendLine("🟢 وقت الحضور (بغداد): ${record.checkInDisplayTime}")
    appendLine("🌐 الوقت العالمي (UTC): ${record.checkInUtcIso}")
    appendLine("📡 المصدر المعتمد: ${record.checkInSource}")
    append("✅ ختم التحقق: ${AttendanceReceiptRenderer.computeVerificationSeal(record, isCheckOut = false)}")
  }

  fun buildCheckOutMessage(record: AttendanceRecord): String = buildString {
    appendLine("🔬 *إشعار تسجيل انصراف - المختبر*")
    appendLine("👤 الموظف: ${record.staffName}")
    if (record.staffRole.isNotBlank()) {
      appendLine("🧪 الصفة: ${record.staffRole}")
    }
    appendLine("📅 التاريخ: ${record.workDateUtc}")
    appendLine("🟢 وقت الحضور: ${record.checkInDisplayTime}")
    appendLine("🔴 وقت الانصراف (بغداد): ${record.checkOutDisplayTime}")
    appendLine("🌐 انصراف UTC: ${record.checkOutUtcIso}")
    appendLine("📡 المصدر المعتمد: ${record.checkOutSource}")
    append("🏁 ختم التحقق: ${AttendanceReceiptRenderer.computeVerificationSeal(record, isCheckOut = true)}")
  }

  suspend fun dispatchReceiptToWhatsApp(
    context: Context,
    record: AttendanceRecord,
    isCheckOut: Boolean,
    selfieFile: File? = null,
    targetPhoneDisplay: String = TARGET_PHONE_DISPLAY,
  ): Boolean {
    val imageUri: Uri = withContext(Dispatchers.IO) {
      runCatching {
        AttendanceReceiptRenderer.renderReceiptToUri(
          context = context,
          record = record,
          isCheckOut = isCheckOut,
          selfieFile = selfieFile,
          targetPhoneDisplay = targetPhoneDisplay,
        )
      }.getOrNull()
    } ?: return false

    return launchWhatsAppImageShare(context, imageUri, targetPhoneDisplay)
  }

  fun openWhatsAppWithReceiptImage(
    context: Context,
    record: AttendanceRecord,
    isCheckOut: Boolean,
    selfieFile: File? = null,
    targetPhoneDisplay: String = TARGET_PHONE_DISPLAY,
  ): Boolean {
    val imageUri: Uri = runCatching {
      AttendanceReceiptRenderer.renderReceiptToUri(
        context = context,
        record = record,
        isCheckOut = isCheckOut,
        selfieFile = selfieFile,
        targetPhoneDisplay = targetPhoneDisplay,
      )
    }.getOrNull() ?: return false

    return launchWhatsAppImageShare(context, imageUri, targetPhoneDisplay)
  }

  private fun launchWhatsAppImageShare(
    context: Context,
    imageUri: Uri,
    targetPhoneDisplay: String,
  ): Boolean {
    val digits = WhatsAppConfigStore.toWhatsAppDigits(targetPhoneDisplay)
    val candidatePackages = listOf("com.whatsapp", "com.whatsapp.w4b")
    for (pkg in candidatePackages) {
      val targetedIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        putExtra("jid", "$digits@s.whatsapp.net")
        setPackage(pkg)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      if (runCatching { context.startActivity(targetedIntent) }.isSuccess) {
        return true
      }
    }

    val chooserIntent = Intent.createChooser(
      Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      },
      "إرسال صورة الدوام عبر واتساب",
    ).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching { context.startActivity(chooserIntent) }.isSuccess
  }
}

package com.example.ui.auth

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricAvailability {
  READY,
  NOT_ENROLLED,
  UNAVAILABLE,
}

object BiometricAuthenticator {

  private const val BIOMETRIC_TYPES = BIOMETRIC_STRONG or BIOMETRIC_WEAK

  fun checkAvailability(context: Context): BiometricAvailability {
    val manager = BiometricManager.from(context)
    return when (manager.canAuthenticate(BIOMETRIC_TYPES)) {
      BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.READY
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
      else -> BiometricAvailability.UNAVAILABLE
    }
  }

  fun isDeviceSecure(context: Context): Boolean {
    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    return km?.isDeviceSecure == true
  }

  fun openBiometricEnrollSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
        putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BIOMETRIC_TYPES)
      }
    } else {
      Intent(Settings.ACTION_SECURITY_SETTINGS)
    }.apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val started = runCatching { context.startActivity(intent) }.isSuccess
    if (!started) {
      runCatching {
        context.startActivity(
          Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
      }
    }
  }

  fun authenticateFingerprint(
    context: Context,
    staffName: String,
    isCheckOut: Boolean,
    onSucceeded: (methodLabel: String) -> Unit,
    onCancelled: () -> Unit,
    onError: (String) -> Unit,
  ) {
    val activity = context.findFragmentActivity()
    if (activity == null) {
      onError("تعذر ربط نافذة التحقق بالبصمة")
      return
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
      activity,
      executor,
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          super.onAuthenticationSucceeded(result)
          onSucceeded("بصمة الجهاز")
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          super.onAuthenticationError(errorCode, errString)
          when (errorCode) {
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_CANCELED -> onCancelled()
            else -> onError(errString.toString().ifBlank { "فشل التحقق من بصمة الجهاز" })
          }
        }
      },
    )

    val title = if (isCheckOut) "بصمة تسجيل الانصراف" else "بصمة تثبيت الحضور"
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle(title)
      .setSubtitle("الموظف: $staffName")
      .setDescription("ضع إصبعك على مستشعر بصمة الجهاز لاعتماد الوقت العالمي والتصوير")
      .setAllowedAuthenticators(BIOMETRIC_TYPES)
      .setNegativeButtonText("إلغاء")
      .setConfirmationRequired(false)
      .build()

    prompt.authenticate(promptInfo)
  }

  fun authenticateWithDeviceCredential(
    context: Context,
    staffName: String,
    isCheckOut: Boolean,
    onSucceeded: (methodLabel: String) -> Unit,
    onCancelled: () -> Unit,
    onError: (String) -> Unit,
  ) {
    val activity = context.findFragmentActivity()
    if (activity == null) {
      onError("تعذر ربط نافذة التحقق")
      return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val manager = BiometricManager.from(activity)
      if (manager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) {
        onError("لا يوجد قفل أمان مفعّل على الجهاز")
        return
      }
      val executor = ContextCompat.getMainExecutor(activity)
      val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            onSucceeded("قفل الجهاز")
          }

          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
              errorCode == BiometricPrompt.ERROR_CANCELED
            ) {
              onCancelled()
            } else {
              onError(errString.toString())
            }
          }
        },
      )
      val title = if (isCheckOut) "تأكيد قفل الجهاز للانصراف" else "تأكيد قفل الجهاز للحضور"
      val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle("الموظف: $staffName")
        .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        .build()
      prompt.authenticate(promptInfo)
    } else {
      onError("يرجى تفعيل بصمة الإصبع من إعدادات الجهاز")
    }
  }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
  is FragmentActivity -> this
  is ContextWrapper -> baseContext.findFragmentActivity()
  else -> null
}

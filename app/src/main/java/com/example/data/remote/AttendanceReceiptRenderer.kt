package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.model.AttendanceRecord
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.max

object AttendanceReceiptRenderer {

  private const val CARD_WIDTH = 1080
  private const val CARD_HEIGHT = 1280
  private const val PHOTO_WIDTH = 1080
  private const val PHOTO_HEIGHT = 1440

  fun computeVerificationSeal(record: AttendanceRecord, isCheckOut: Boolean): String {
    val raw = buildString {
      append(record.id)
      append('|').append(record.staffId)
      append('|').append(record.checkInEpochMs)
      append('|').append(if (isCheckOut) record.checkOutEpochMs else 0L)
      append('|').append(record.workDateUtc)
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    val hex = digest.take(6).joinToString("") { "%02X".format(it) }
    return "${hex.substring(0, 4)}-${hex.substring(4, 8)}-${hex.substring(8, 12)}"
  }

  fun getReceiptFile(context: Context, recordId: String, isCheckOut: Boolean): File {
    val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val suffix = if (isCheckOut) "out" else "in"
    return File(receiptsDir, "lab_receipt_${recordId.ifBlank { "tmp" }}_$suffix.png")
  }

  fun renderReceiptToUri(
    context: Context,
    record: AttendanceRecord,
    isCheckOut: Boolean,
    selfieFile: File? = null,
    targetPhoneDisplay: String = WhatsAppDispatcher.TARGET_PHONE_DISPLAY,
  ): Uri {
    val outFile = getReceiptFile(context, record.id, isCheckOut)

    // If resending without a new camera file and a stamped receipt already exists on disk, reuse it
    if (selfieFile == null && outFile.exists() && outFile.length() > 0L) {
      return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }

    val decodedSelfie = selfieFile?.takeIf { it.exists() }?.let { decodeCorrectedSelfie(it) }
    val bitmap = if (decodedSelfie != null) {
      try {
        renderSelfieStampedBitmap(context, record, isCheckOut, decodedSelfie, targetPhoneDisplay)
      } finally {
        decodedSelfie.recycle()
      }
    } else {
      renderReceiptBitmap(context, record, isCheckOut, targetPhoneDisplay)
    }

    try {
      FileOutputStream(outFile).use { stream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 95, stream)
      }
    } finally {
      bitmap.recycle()
    }

    // Remove temporary raw camera capture after compositing into the sealed PNG
    if (selfieFile != null && selfieFile.exists() && selfieFile.name.startsWith("raw_capture_")) {
      runCatching { selfieFile.delete() }
    }

    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
  }

  private fun renderSelfieStampedBitmap(
    context: Context,
    record: AttendanceRecord,
    isCheckOut: Boolean,
    selfieBitmap: Bitmap,
    targetPhoneDisplay: String = WhatsAppDispatcher.TARGET_PHONE_DISPLAY,
  ): Bitmap {
    val bitmap = Bitmap.createBitmap(PHOTO_WIDTH, PHOTO_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cairoTypeface = runCatching { ResourcesCompat.getFont(context, R.font.cairo) }.getOrNull()
      ?: Typeface.DEFAULT
    val monoTypeface = runCatching { ResourcesCompat.getFont(context, R.font.jetbrains_mono) }.getOrNull()
      ?: Typeface.MONOSPACE

    val frameBg = Color.parseColor("#051C1B")
    val tealBright = Color.parseColor("#5EEAD4")
    val actionAccent = if (isCheckOut) Color.parseColor("#F59E0B") else Color.parseColor("#10B981")
    val actionBadgeBg = if (isCheckOut) Color.parseColor("#E6451A03") else Color.parseColor("#E6022C22")

    canvas.drawColor(frameBg)

    // Rounded photo viewport
    val photoMargin = 24f
    val photoRect = RectF(photoMargin, photoMargin, PHOTO_WIDTH - photoMargin, PHOTO_HEIGHT - photoMargin)
    val clipPath = Path().apply {
      addRoundRect(photoRect, 36f, 36f, Path.Direction.CW)
    }

    canvas.save()
    canvas.clipPath(clipPath)

    // Center-crop selfie into photoRect
    val scale = max(
      photoRect.width() / selfieBitmap.width.toFloat(),
      photoRect.height() / selfieBitmap.height.toFloat(),
    )
    val scaledW = selfieBitmap.width * scale
    val scaledH = selfieBitmap.height * scale
    val dx = photoRect.left + (photoRect.width() - scaledW) / 2f
    val dy = photoRect.top + (photoRect.height() - scaledH) / 2f
    val drawMatrix = Matrix().apply {
      setScale(scale, scale)
      postTranslate(dx, dy)
    }
    val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(selfieBitmap, drawMatrix, photoPaint)

    // Top subtle dark gradient for header contrast
    val topScrim = Paint().apply {
      shader = LinearGradient(
        0f, photoRect.top, 0f, photoRect.top + 250f,
        Color.argb(210, 4, 24, 23),
        Color.TRANSPARENT,
        Shader.TileMode.CLAMP,
      )
    }
    canvas.drawRect(photoRect.left, photoRect.top, photoRect.right, photoRect.top + 250f, topScrim)

    // Bottom deep gradient scrim for high-contrast timestamp & date stamp
    val scrimTop = PHOTO_HEIGHT * 0.50f
    val bottomScrim = Paint().apply {
      shader = LinearGradient(
        0f, scrimTop, 0f, photoRect.bottom,
        intArrayOf(
          Color.TRANSPARENT,
          Color.argb(175, 4, 22, 21),
          Color.argb(242, 4, 20, 19),
        ),
        floatArrayOf(0f, 0.35f, 1f),
        Shader.TileMode.CLAMP,
      )
    }
    canvas.drawRect(photoRect.left, scrimTop, photoRect.right, photoRect.bottom, bottomScrim)

    // Top Header Bar inside photo
    val topTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = 36f
      typeface = Typeface.create(cairoTypeface, Typeface.BOLD)
    }
    val topSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = tealBright
      textSize = 24f
      typeface = cairoTypeface
    }
    drawRtlText(canvas, "حضور المختبر • توثيق بالصورة", 64f, 60f, PHOTO_WIDTH - 420, topTitlePaint)
    drawRtlText(canvas, "صورة فورية غير قابلة للتعديل", 64f, 114f, PHOTO_WIDTH - 420, topSubPaint)

    // Top-left status badge
    val statusPillRect = RectF(PHOTO_WIDTH - 340f, 62f, PHOTO_WIDTH - 58f, 134f)
    val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = actionBadgeBg }
    val pillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2.5f
      color = actionAccent
    }
    canvas.drawRoundRect(statusPillRect, 36f, 36f, pillPaint)
    canvas.drawRoundRect(statusPillRect, 36f, 36f, pillBorder)
    val statusTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = actionAccent
      textSize = 28f
      typeface = Typeface.create(cairoTypeface, Typeface.BOLD)
    }
    val statusText = if (isCheckOut) "● انصراف رسمي" else "● حضور رسمي"
    drawCenteredText(canvas, statusText, statusPillRect, statusTextPaint)

    // Bottom Stamped Details
    val actionTimeDisplay = if (isCheckOut && record.checkOutDisplayTime.isNotBlank()) {
      record.checkOutDisplayTime
    } else {
      record.checkInDisplayTime
    }
    val actionUtcIso = if (isCheckOut && record.checkOutUtcIso.isNotBlank()) {
      record.checkOutUtcIso
    } else {
      record.checkInUtcIso
    }
    val actionSource = if (isCheckOut && record.checkOutSource.isNotBlank()) {
      record.checkOutSource
    } else {
      record.checkInSource
    }

    // Employee Name & Role above timestamp panel
    val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = 50f
      typeface = Typeface.create(cairoTypeface, Typeface.BOLD)
    }
    val rolePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#CCFBF1")
      textSize = 28f
      typeface = cairoTypeface
    }
    drawRtlText(canvas, record.staffName, 64f, 945f, PHOTO_WIDTH - 128, namePaint)
    drawRtlText(
      canvas,
      record.staffRole.ifBlank { "كادر المختبر الطبي" },
      64f,
      1018f,
      PHOTO_WIDTH - 128,
      rolePaint,
    )

    // Glassmorphic Stamp Panel with explicit Time & Date written on the photo
    val panelRect = RectF(56f, 1074f, PHOTO_WIDTH - 56f, 1296f)
    val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(170, 9, 44, 41)
    }
    val panelBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2.5f
      color = Color.argb(130, 94, 234, 212)
    }
    canvas.drawRoundRect(panelRect, 26f, 26f, panelPaint)
    canvas.drawRoundRect(panelRect, 26f, 26f, panelBorder)

    val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = tealBright
      textSize = 24f
      typeface = cairoTypeface
    }
    val bigMonoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = 44f
      typeface = Typeface.create(monoTypeface, Typeface.BOLD)
    }
    val dateMonoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = 38f
      typeface = Typeface.create(monoTypeface, Typeface.BOLD)
    }
    val smallMonoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#D1FAE5")
      textSize = 23f
      typeface = monoTypeface
    }

    // Right half: Time (RTL visual right), Left half: Date
    val rightColX = panelRect.left + 28f
    val colWidth = (panelRect.width() / 2f - 42f).toInt()
    val leftColX = panelRect.left + panelRect.width() / 2f + 14f

    drawRtlText(
      canvas,
      if (isCheckOut) "وقت الانصراف (بغداد)" else "وقت الحضور (بغداد)",
      leftColX,
      panelRect.top + 18f,
      colWidth,
      labelPaint,
    )
    drawRtlText(
      canvas,
      actionTimeDisplay,
      leftColX,
      panelRect.top + 56f,
      colWidth,
      bigMonoPaint,
    )

    drawRtlText(
      canvas,
      "التاريخ المعتمد",
      rightColX,
      panelRect.top + 18f,
      colWidth,
      labelPaint,
    )
    drawRtlText(
      canvas,
      record.workDateUtc,
      rightColX,
      panelRect.top + 60f,
      colWidth,
      dateMonoPaint,
    )

    // Divider line inside stamp panel
    val divPaint = Paint().apply {
      color = Color.argb(70, 255, 255, 255)
      strokeWidth = 1.5f
    }
    canvas.drawLine(
      panelRect.left + 28f,
      panelRect.top + 132f,
      panelRect.right - 28f,
      panelRect.top + 132f,
      divPaint,
    )

    val subLine = if (isCheckOut) {
      "حضور: ${record.checkInDisplayTime}  •  UTC: $actionUtcIso"
    } else {
      "UTC: $actionUtcIso  •  $actionSource"
    }
    drawRtlText(
      canvas,
      subLine,
      panelRect.left + 28f,
      panelRect.top + 148f,
      (panelRect.width() - 56f).toInt(),
      smallMonoPaint,
    )
    val biometricBadge = when {
      actionSource.contains("بصمة") -> "✓ موثق ببصمة الجهاز"
      actionSource.contains("قفل الجهاز") -> "✓ موثق بقفل الجهاز"
      else -> "موثق بالخادم العالمي"
    }
    if (isCheckOut) {
      drawRtlText(
        canvas,
        "$biometricBadge  •  $actionSource",
        panelRect.left + 28f,
        panelRect.top + 182f,
        (panelRect.width() - 56f).toInt(),
        labelPaint.apply { textSize = 22f },
      )
    } else {
      drawRtlText(
        canvas,
        "$biometricBadge — مستقل عن ساعة الهاتف",
        panelRect.left + 28f,
        panelRect.top + 182f,
        (panelRect.width() - 56f).toInt(),
        labelPaint.apply { textSize = 22f },
      )
    }

    // Verification seal bar at very bottom
    val seal = computeVerificationSeal(record, isCheckOut)
    val sealPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = tealBright
      textSize = 24f
      typeface = Typeface.create(monoTypeface, Typeface.BOLD)
    }
    drawCenteredText(
      canvas,
      "SEAL: $seal  •  TO: $targetPhoneDisplay",
      RectF(photoRect.left + 32f, 1318f, photoRect.right - 32f, 1396f),
      sealPaint,
    )

    canvas.restore()

    // Crisp outer border around rounded photo card
    val outerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 3f
      color = Color.argb(140, 94, 234, 212)
    }
    canvas.drawRoundRect(photoRect, 36f, 36f, outerBorder)

    return bitmap
  }

  private fun decodeCorrectedSelfie(file: File): Bitmap? {
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
    if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

    var sampleSize = 1
    while (boundsOpts.outWidth / sampleSize > 1600 || boundsOpts.outHeight / sampleSize > 1600) {
      sampleSize *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val rawBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

    val orientation = runCatching {
      ExifInterface(file.absolutePath).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL,
      )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.postRotate(90f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.postRotate(270f)
        matrix.postScale(-1f, 1f)
      }
      else -> return rawBitmap
    }

    val transformed = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
    if (transformed !== rawBitmap) {
      rawBitmap.recycle()
    }
    return transformed
  }

  fun renderReceiptBitmap(
    context: Context,
    record: AttendanceRecord,
    isCheckOut: Boolean,
    targetPhoneDisplay: String = WhatsAppDispatcher.TARGET_PHONE_DISPLAY,
  ): Bitmap {
    val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cairoTypeface = runCatching { ResourcesCompat.getFont(context, R.font.cairo) }.getOrNull()
      ?: Typeface.DEFAULT
    val monoTypeface = runCatching { ResourcesCompat.getFont(context, R.font.jetbrains_mono) }.getOrNull()
      ?: Typeface.MONOSPACE

    val tealDark = Color.parseColor("#042423")
    val tealMid = Color.parseColor("#0E6D65")
    val accentColor = if (isCheckOut) Color.parseColor("#D97706") else Color.parseColor("#059669")
    val accentBg = if (isCheckOut) Color.parseColor("#FEF3C7") else Color.parseColor("#D1FAE5")
    val bgColor = Color.parseColor("#F1F6F5")
    val cardBg = Color.WHITE
    val textPrimary = Color.parseColor("#0C1E1C")
    val textSecondary = Color.parseColor("#475F5B")
    val borderColor = Color.parseColor("#D0E0DD")

    canvas.drawColor(bgColor)

    val cardRect = RectF(48f, 48f, CARD_WIDTH - 48f, CARD_HEIGHT - 48f)
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBg }
    canvas.drawRoundRect(cardRect, 40f, 40f, cardPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 3f
      color = borderColor
    }
    canvas.drawRoundRect(cardRect, 40f, 40f, borderPaint)

    val headerRect = RectF(48f, 48f, CARD_WIDTH - 48f, 270f)
    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(
        48f, 48f, (CARD_WIDTH - 48).toFloat(), 270f,
        tealDark, tealMid, Shader.TileMode.CLAMP,
      )
    }
    canvas.save()
    val clipPath = Path().apply {
      addRoundRect(cardRect, 40f, 40f, Path.Direction.CW)
    }
    canvas.clipPath(clipPath)
    canvas.drawRect(headerRect, headerPaint)
    canvas.restore()

    val headerTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = 46f
      typeface = Typeface.create(cairoTypeface, Typeface.BOLD)
    }
    val headerSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#99F6E4")
      textSize = 28f
      typeface = cairoTypeface
    }
    drawRtlText(canvas, "بطاقة دوام رسمية — المختبر", 96f, 88f, CARD_WIDTH - 192, headerTitlePaint)
    drawRtlText(canvas, "موثقة بالوقت العالمي المعتمد • وثيقة غير قابلة للتعديل", 96f, 174f, CARD_WIDTH - 192, headerSubPaint)

    val badgeRect = RectF(96f, 304f, CARD_WIDTH - 96f, 386f)
    val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentBg }
    canvas.drawRoundRect(badgeRect, 24f, 24f, badgeBgPaint)
    val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = accentColor
      textSize = 34f
      typeface = Typeface.create(cairoTypeface, Typeface.BOLD)
    }
    val statusText = if (isCheckOut) "●  إشعار تسجيل انصراف رسمي" else "●  إشعار تثبيت حضور رسمي"
    drawCenteredText(canvas, statusText, badgeRect, badgeTextPaint)

    val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = textSecondary
      textSize = 26f
      typeface = cairoTypeface
    }
    val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = textPrimary
      textSize = 36f
      typeface = Typeface.create(cairoTypeface, Typeface.BOLD)
    }
    val monoValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = textPrimary
      textSize = 34f
      typeface = Typeface.create(monoTypeface, Typeface.BOLD)
    }
    val monoSmallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = textSecondary
      textSize = 24f
      typeface = monoTypeface
    }

    var cursorY = 424f
    cursorY = drawInfoRow(
      canvas = canvas,
      topY = cursorY,
      label = "اسم الموظف",
      value = record.staffName,
      subValue = record.staffRole.ifBlank { "كادر المختبر" },
      labelPaint = labelPaint,
      valuePaint = valuePaint,
      subPaint = labelPaint,
    )
    cursorY = drawInfoRow(
      canvas = canvas,
      topY = cursorY,
      label = "تاريخ الدوام (بغداد)",
      value = record.workDateUtc,
      subValue = "",
      labelPaint = labelPaint,
      valuePaint = monoValuePaint,
      subPaint = monoSmallPaint,
    )
    cursorY = drawInfoRow(
      canvas = canvas,
      topY = cursorY,
      label = "وقت الحضور المعتمد",
      value = record.checkInDisplayTime,
      subValue = "UTC: ${record.checkInUtcIso}  |  ${record.checkInSource}",
      labelPaint = labelPaint,
      valuePaint = monoValuePaint,
      subPaint = monoSmallPaint,
    )

    val checkOutVal = if (isCheckOut && record.checkOutDisplayTime.isNotBlank()) {
      record.checkOutDisplayTime
    } else {
      "لم يسجل الانصراف بعد"
    }
    val checkOutSub = if (isCheckOut && record.checkOutUtcIso.isNotBlank()) {
      "UTC: ${record.checkOutUtcIso}  |  ${record.checkOutSource}"
    } else {
      ""
    }
    drawInfoRow(
      canvas = canvas,
      topY = cursorY,
      label = "وقت الانصراف المعتمد",
      value = checkOutVal,
      subValue = checkOutSub,
      labelPaint = labelPaint,
      valuePaint = if (isCheckOut) monoValuePaint else valuePaint,
      subPaint = monoSmallPaint,
    )

    val seal = computeVerificationSeal(record, isCheckOut)
    val footerRect = RectF(96f, CARD_HEIGHT - 220f, CARD_WIDTH - 96f, CARD_HEIGHT - 84f)
    val footerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EBF5F3") }
    canvas.drawRoundRect(footerRect, 20f, 20f, footerBgPaint)

    val sealPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = tealDark
      textSize = 26f
      typeface = Typeface.create(monoTypeface, Typeface.BOLD)
    }
    val footerNotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = textSecondary
      textSize = 23f
      typeface = cairoTypeface
    }
    drawCenteredText(
      canvas,
      "SEAL: $seal  •  TO: $targetPhoneDisplay",
      RectF(footerRect.left, footerRect.top + 14f, footerRect.right, footerRect.top + 68f),
      sealPaint,
    )
    drawCenteredText(
      canvas,
      "ختم تحقق رقمي محفوظ في السحابة — أي تعديل خارجي غير معترف به",
      RectF(footerRect.left, footerRect.top + 68f, footerRect.right, footerRect.bottom - 12f),
      footerNotePaint,
    )

    return bitmap
  }

  private fun drawInfoRow(
    canvas: Canvas,
    topY: Float,
    label: String,
    value: String,
    subValue: String,
    labelPaint: TextPaint,
    valuePaint: TextPaint,
    subPaint: TextPaint,
  ): Float {
    val boxHeight = if (subValue.isNotBlank()) 142f else 112f
    val rect = RectF(96f, topY, CARD_WIDTH - 96f, topY + boxHeight)
    val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FBFA") }
    val boxStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2f
      color = Color.parseColor("#DCEAE7")
    }
    canvas.drawRoundRect(rect, 20f, 20f, boxPaint)
    canvas.drawRoundRect(rect, 20f, 20f, boxStroke)

    val innerWidth = (rect.width() - 48f).toInt()
    drawRtlText(canvas, label, rect.left + 24f, rect.top + 14f, innerWidth, labelPaint)
    drawRtlText(canvas, value, rect.left + 24f, rect.top + 48f, innerWidth, valuePaint)
    if (subValue.isNotBlank()) {
      drawRtlText(canvas, subValue, rect.left + 24f, rect.top + 98f, innerWidth, subPaint)
    }
    return topY + boxHeight + 18f
  }

  private fun drawRtlText(
    canvas: Canvas,
    text: String,
    x: Float,
    y: Float,
    width: Int,
    paint: TextPaint,
  ) {
    val safeWidth = width.coerceAtLeast(1)
    val layout = StaticLayout.Builder
      .obtain(text, 0, text.length, paint, safeWidth)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setTextDirection(TextDirectionHeuristics.RTL)
      .setMaxLines(2)
      .build()
    canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restore()
  }

  private fun drawCenteredText(
    canvas: Canvas,
    text: String,
    bounds: RectF,
    paint: TextPaint,
  ) {
    val width = bounds.width().toInt().coerceAtLeast(1)
    val layout = StaticLayout.Builder
      .obtain(text, 0, text.length, paint, width)
      .setAlignment(Layout.Alignment.ALIGN_CENTER)
      .setTextDirection(TextDirectionHeuristics.ANYRTL_LTR)
      .setMaxLines(2)
      .build()
    val dy = (bounds.height() - layout.height) / 2f
    canvas.save()
    canvas.translate(bounds.left, bounds.top + dy.coerceAtLeast(0f))
    layout.draw(canvas)
    canvas.restore()
  }
}

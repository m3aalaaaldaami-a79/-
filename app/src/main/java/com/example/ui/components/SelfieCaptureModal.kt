package com.example.ui.components

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.GlobalClockState
import com.example.ui.theme.ClockCyanGlow
import com.example.ui.theme.ClockDeepMid
import com.example.ui.theme.ClockDeepStart
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusCheckedOutAmber
import java.io.File

@Composable
fun SelfieCaptureModal(
  staffName: String,
  isCheckOut: Boolean,
  clockState: GlobalClockState,
  onPhotoCaptured: (File?) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

  var useFrontCamera by remember { mutableStateOf(true) }
  var hasBackCamera by remember { mutableStateOf(false) }
  var hasFrontCamera by remember { mutableStateOf(true) }
  var cameraError by remember { mutableStateOf<String?>(null) }
  var isCapturing by remember { mutableStateOf(false) }
  var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

  val previewView = remember(context) {
    PreviewView(context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
      scaleType = PreviewView.ScaleType.FILL_CENTER
    }
  }

  LaunchedEffect(useFrontCamera) {
    cameraError = null
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(
      {
        runCatching {
          val cameraProvider = cameraProviderFuture.get()
          val frontAvailable = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
          val backAvailable = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
          hasFrontCamera = frontAvailable
          hasBackCamera = backAvailable

          val selector = when {
            useFrontCamera && frontAvailable -> CameraSelector.DEFAULT_FRONT_CAMERA
            !useFrontCamera && backAvailable -> CameraSelector.DEFAULT_BACK_CAMERA
            frontAvailable -> CameraSelector.DEFAULT_FRONT_CAMERA
            backAvailable -> CameraSelector.DEFAULT_BACK_CAMERA
            else -> throw IllegalStateException("لا توجد كاميرا متوفرة على هذا الجهاز")
          }

          val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
          }

          val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
          val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(rotation)
            .build()

          cameraProvider.unbindAll()
          cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
          imageCapture = capture
        }.onFailure { err ->
          cameraError = err.localizedMessage ?: "تعذر تشغيل الكاميرا لالتقاط السيلفي"
        }
      },
      mainExecutor,
    )
  }

  DisposableEffect(Unit) {
    onDispose {
      runCatching {
        ProcessCameraProvider.getInstance(context).get().unbindAll()
      }
    }
  }

  val accentColor = if (isCheckOut) StatusCheckedOutAmber else StatusActiveGreen
  val actionTitle = if (isCheckOut) "سيلفي تسجيل الانصراف" else "سيلفي تثبيت الحضور"

  Dialog(
    onDismissRequest = { if (!isCapturing) onDismiss() },
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnBackPress = !isCapturing,
      dismissOnClickOutside = false,
    ),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(ClockDeepStart, Color(0xFF041312)),
          )
        )
        .windowInsetsPadding(WindowInsets.safeDrawing),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 560.dp)
          .fillMaxSize()
          .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        // Top Header Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = onDismiss,
            enabled = !isCapturing,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .background(Color.White.copy(alpha = 0.10f), CircleShape)
              .testTag("selfie_close_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "إغلاق الكاميرا",
              tint = Color.White,
            )
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
              color = accentColor.copy(alpha = 0.22f),
              border = BorderStroke(1.dp, accentColor.copy(alpha = 0.7f)),
              shape = RoundedCornerShape(50),
            ) {
              Text(
                text = actionTitle,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = staffName,
              style = MaterialTheme.typography.titleMedium,
              color = Color.White,
              fontWeight = FontWeight.Bold,
            )
          }

          if (hasFrontCamera && hasBackCamera) {
            IconButton(
              onClick = { useFrontCamera = !useFrontCamera },
              enabled = !isCapturing,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
                .testTag("selfie_flip_camera_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "تبديل الكاميرا",
                tint = Color.White,
              )
            }
          } else {
            Spacer(modifier = Modifier.size(48.dp))
          }
        }

        // Camera Viewfinder Container (3:4 Aspect)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .padding(vertical = 12.dp)
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF0A201E))
            .border(2.dp, ClockCyanGlow.copy(alpha = 0.55f), RoundedCornerShape(28.dp)),
          contentAlignment = Alignment.Center,
        ) {
          if (cameraError == null) {
            AndroidView(
              factory = { previewView },
              modifier = Modifier.fillMaxSize(),
            )

            // Face alignment guide ring overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
              val stroke = Stroke(
                width = 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f),
              )
              val guideW = size.width * 0.58f
              val guideH = size.height * 0.48f
              drawRoundRect(
                color = Color.White.copy(alpha = 0.45f),
                topLeft = Offset((size.width - guideW) / 2f, size.height * 0.15f),
                size = Size(guideW, guideH),
                cornerRadius = CornerRadius(guideW / 2f, guideW / 2f),
                style = stroke,
              )
            }
          } else {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = StatusCheckedOutAmber,
                modifier = Modifier.size(44.dp),
              )
              Text(
                text = cameraError ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
              )
              OutlinedButton(
                onClick = { onPhotoCaptured(null) },
                border = BorderStroke(1.dp, ClockCyanGlow),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ClockCyanGlow),
              ) {
                Text("المتابعة ببطاقة رسمية بدون كاميرا")
              }
            }
          }

          // Live Global Timestamp & Date preview overlay inside viewfinder
          clockState.currentDisplay?.let { live ->
            Surface(
              color = Color.Black.copy(alpha = 0.68f),
              border = BorderStroke(1.dp, ClockCyanGlow.copy(alpha = 0.45f)),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = ClockCyanGlow,
                    modifier = Modifier.size(18.dp),
                  )
                  Column {
                    Text(
                      text = live.displayTimeBaghdad,
                      fontFamily = JetBrainsMonoFamily,
                      fontWeight = FontWeight.Bold,
                      style = MaterialTheme.typography.titleMedium,
                      color = Color.White,
                    )
                    Text(
                      text = "${live.workDateBaghdad} • ${live.displayTimeUtc}",
                      fontFamily = JetBrainsMonoFamily,
                      style = MaterialTheme.typography.bodySmall,
                      color = ClockCyanGlow,
                    )
                  }
                }
                Text(
                  text = "سيُطبع على الصورة",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White.copy(alpha = 0.85f),
                )
              }
            }
          }
        }

        // Bottom Capture Controls
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "ضع وجهك داخل الإطار واضغط للتصوير وإرسال الصورة عبر واتساب",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.82f),
            textAlign = TextAlign.Center,
          )

          Button(
            onClick = {
              val capture = imageCapture ?: return@Button
              if (isCapturing) return@Button
              isCapturing = true

              val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
              val rawFile = File(receiptsDir, "raw_capture_${System.currentTimeMillis()}.jpg")
              val outputOpts = ImageCapture.OutputFileOptions.Builder(rawFile).build()

              capture.takePicture(
                outputOpts,
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                  override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    isCapturing = false
                    onPhotoCaptured(rawFile)
                  }

                  override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    cameraError = "تعذر التقاط الصورة: ${exception.localizedMessage ?: "خطأ غير متوقع"}"
                  }
                },
              )
            },
            enabled = !isCapturing && imageCapture != null && cameraError == null,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
              containerColor = accentColor,
              contentColor = Color.White,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(58.dp)
              .minimumInteractiveComponentSize()
              .testTag("capture_selfie_button"),
          ) {
            if (isCapturing) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.5.dp,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text("جاري التقاط السيلفي وتثبيت الوقت...", style = MaterialTheme.typography.titleSmall)
            } else {
              Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = if (isCheckOut) "التقاط سيلفي وتثبيت الانصراف" else "التقاط سيلفي وتثبيت الحضور",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }
    }
  }
}

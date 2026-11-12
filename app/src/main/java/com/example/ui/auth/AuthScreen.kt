package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.WhatsAppConfigStore
import com.example.ui.theme.ClockDeepEnd
import com.example.ui.theme.ClockDeepStart
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.WhatsAppGreen

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val configStore = remember(context) { WhatsAppConfigStore(context.applicationContext) }
  val whatsappPhone by configStore.whatsappPhoneFlow.collectAsStateWithLifecycle(
    initialValue = WhatsAppConfigStore.DEFAULT_PHONE_DISPLAY
  )

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = scope,
    )
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.background,
          )
        )
      )
      .windowInsetsPadding(WindowInsets.safeDrawing),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 520.dp)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box {
          Image(
            painter = painterResource(id = R.drawable.img_lab_hero_1794509809229),
            contentDescription = stringResource(R.string.sign_in_title),
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxWidth()
              .height(210.dp),
          )
          Box(
            modifier = Modifier
              .matchParentSize()
              .background(
                Brush.verticalGradient(
                  colors = listOf(Color.Transparent, ClockDeepStart.copy(alpha = 0.65f))
                )
              )
          )
          Surface(
            color = ClockDeepStart.copy(alpha = 0.88f),
            shape = RoundedCornerShape(50),
            modifier = Modifier
              .align(Alignment.TopStart)
              .padding(14.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Science,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "مختبر التحاليل الذكي",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
              )
            }
          }
        }
      }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = stringResource(R.string.sign_in_title),
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center,
        )
        Text(
          text = stringResource(R.string.sign_in_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }

      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          FeatureBullet(
            icon = Icons.Default.Public,
            title = "وقت عالمي شبكي موثوق",
            subtitle = "يُسجل الحضور والانصراف مباشرة من خوادم الوقت العالمية دون الاعتماد على ساعة الجهاز.",
          )
          FeatureBullet(
            icon = Icons.Default.CameraAlt,
            title = "بصمة الجهاز + سيلفي موثق عبر واتساب",
            subtitle = "يتحقق التطبيق من بصمة الجهاز ثم يلتقط صورة سيلفي يُطبع عليها الوقت والتاريخ وترسل إلى:",
            highlightMono = whatsappPhone,
          )
          FeatureBullet(
            icon = Icons.Default.CloudSync,
            title = "حفظ سحابي آمن",
            subtitle = "حفظ قائمة العاملين وسجل الدوام اليومي بشكل دائم في السحابة.",
          )
        }
      }

      if (errorMessage != null) {
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = errorMessage!!,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(14.dp),
          )
        }
      }

      Button(
        onClick = {
          errorMessage = null
          isLoading = true
          onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
              isLoading = false
              onAuthSuccess()
            },
            onAuthError = { msg ->
              isLoading = false
              errorMessage = msg
            },
            scope = scope,
            onAuthCancelled = {
              isLoading = false
            },
          )
        },
        enabled = !isLoading,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = ClockDeepEnd,
          contentColor = Color.White,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp)
          .minimumInteractiveComponentSize()
          .testTag("google_sign_in_button"),
      ) {
        if (isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = Color.White,
            strokeWidth = 2.5.dp,
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "جاري التحقق...",
            style = MaterialTheme.typography.titleMedium,
          )
        } else {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Login,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = stringResource(R.string.sign_in_with_google),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      Surface(
        color = WhatsAppGreen.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(50),
        modifier = Modifier.clip(RoundedCornerShape(50)),
      ) {
        Text(
          text = "رقم واتساب الإشعارات: $whatsappPhone",
          fontFamily = JetBrainsMonoFamily,
          fontWeight = FontWeight.Medium,
          style = MaterialTheme.typography.bodySmall,
          color = WhatsAppGreen,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }
    }
  }
}

@Composable
private fun FeatureBullet(
  icon: ImageVector,
  title: String,
  subtitle: String,
  highlightMono: String? = null,
) {
  Row(
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(14.dp),
      color = MaterialTheme.colorScheme.primaryContainer,
      modifier = Modifier.size(42.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
      }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (highlightMono != null) {
        Text(
          text = highlightMono,
          fontFamily = JetBrainsMonoFamily,
          style = MaterialTheme.typography.labelMedium,
          color = WhatsAppGreen,
        )
      }
    }
  }
}

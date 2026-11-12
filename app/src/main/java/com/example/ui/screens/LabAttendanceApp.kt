package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccessTimeFilled
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.local.WhatsAppConfigStore
import com.example.data.model.AttendanceRecord
import com.example.data.model.StaffMember
import com.example.data.remote.WhatsAppDispatcher
import com.example.data.remote.WorldTimeService
import com.example.data.repository.LabAttendanceRepository
import com.example.ui.AttendanceEvent
import com.example.ui.LabAttendanceViewModel
import com.example.ui.auth.BiometricAuthenticator
import com.example.ui.auth.BiometricAvailability
import com.example.ui.auth.signOutUser
import com.example.ui.components.SelfieCaptureModal
import com.example.ui.components.WhatsAppSettingsDialog
import com.example.ui.theme.ClockDeepEnd
import com.example.ui.theme.ClockDeepStart
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.WhatsAppGreen
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class LabTab(
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  DESK(R.string.tab_desk, Icons.Filled.AccessTimeFilled, Icons.Outlined.AccessTime),
  HISTORY(R.string.tab_history, Icons.Filled.History, Icons.Outlined.History),
  STAFF(R.string.tab_staff, Icons.Filled.Group, Icons.Outlined.Group),
}

private sealed interface SelfieCaptureRequest {
  val biometricMethod: String

  data class CheckIn(
    val staff: StaffMember,
    val staffList: List<StaffMember>,
    override val biometricMethod: String,
  ) : SelfieCaptureRequest

  data class CheckOut(
    val record: AttendanceRecord,
    override val biometricMethod: String,
  ) : SelfieCaptureRequest
}

private sealed interface PendingBiometricAction {
  val staffName: String
  val isCheckOut: Boolean

  data class CheckIn(val staff: StaffMember, val staffList: List<StaffMember>) : PendingBiometricAction {
    override val staffName: String get() = staff.name
    override val isCheckOut: Boolean get() = false
  }

  data class CheckOut(val record: AttendanceRecord) : PendingBiometricAction {
    override val staffName: String get() = record.staffName
    override val isCheckOut: Boolean get() = true
  }

  fun toCaptureRequest(biometricMethod: String): SelfieCaptureRequest = when (this) {
    is CheckIn -> SelfieCaptureRequest.CheckIn(staff, staffList, biometricMethod)
    is CheckOut -> SelfieCaptureRequest.CheckOut(record, biometricMethod)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabAttendanceApp(currentUserId: String) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: LabAttendanceViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY])
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        LabAttendanceViewModel(
          repository = LabAttendanceRepository(db),
          worldTimeService = WorldTimeService(),
          configStore = WhatsAppConfigStore(app.applicationContext),
          currentUserId = currentUserId,
        )
      }
    },
  )

  val staffState by viewModel.staffState.collectAsStateWithLifecycle()
  val attendanceState by viewModel.attendanceState.collectAsStateWithLifecycle()
  val clockState by viewModel.clockState.collectAsStateWithLifecycle()
  val deskState by viewModel.deskState.collectAsStateWithLifecycle()
  val whatsappPhone by viewModel.whatsappPhone.collectAsStateWithLifecycle()

  var activeTab by remember { mutableStateOf(LabTab.DESK) }
  var showAddStaffDialog by remember { mutableStateOf(false) }
  var showWhatsAppSettingsDialog by remember { mutableStateOf(false) }
  var pendingPermissionAction by remember { mutableStateOf<SelfieCaptureRequest?>(null) }
  var activeSelfieCapture by remember { mutableStateOf<SelfieCaptureRequest?>(null) }
  var showCameraDeniedDialog by remember { mutableStateOf(false) }
  var biometricPromptFallbackAction by remember { mutableStateOf<PendingBiometricAction?>(null) }
  var biometricAvailability by remember {
    mutableStateOf(BiometricAuthenticator.checkAvailability(context))
  }
  val snackbarHostState = remember { SnackbarHostState() }

  // Re-check biometric availability when returning from system Settings
  DisposableEffect(lifecycleOwner, context) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        biometricAvailability = BiometricAuthenticator.checkAvailability(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    val pending = pendingPermissionAction
    pendingPermissionAction = null
    if (granted && pending != null) {
      activeSelfieCapture = pending
    } else if (!granted) {
      showCameraDeniedDialog = true
    }
  }

  fun initiateSelfieCapture(request: SelfieCaptureRequest) {
    val hasPermission = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED
    if (hasPermission) {
      activeSelfieCapture = request
    } else {
      pendingPermissionAction = request
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  fun startBiometricThenSelfie(action: PendingBiometricAction) {
    val currentAvailability = BiometricAuthenticator.checkAvailability(context)
    biometricAvailability = currentAvailability
    when (currentAvailability) {
      BiometricAvailability.READY -> {
        BiometricAuthenticator.authenticateFingerprint(
          context = context,
          staffName = action.staffName,
          isCheckOut = action.isCheckOut,
          onSucceeded = { methodLabel ->
            initiateSelfieCapture(action.toCaptureRequest(methodLabel))
          },
          onCancelled = {
            scope.launch {
              snackbarHostState.showSnackbar("تم إلغاء التحقق ببصمة الجهاز")
            }
          },
          onError = { errMsg ->
            scope.launch {
              snackbarHostState.showSnackbar(errMsg)
            }
          },
        )
      }
      BiometricAvailability.NOT_ENROLLED,
      BiometricAvailability.UNAVAILABLE -> {
        biometricPromptFallbackAction = action
      }
    }
  }

  BackHandler(enabled = activeTab != LabTab.DESK) {
    activeTab = LabTab.DESK
  }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is AttendanceEvent.LaunchWhatsAppReceipt -> {
          val launched = WhatsAppDispatcher.dispatchReceiptToWhatsApp(
            context = context,
            record = event.record,
            isCheckOut = event.isCheckOut,
            selfieFile = event.selfieFile,
            targetPhoneDisplay = event.targetPhoneDisplay,
          )
          if (!launched) {
            snackbarHostState.showSnackbar("تعذر فتح واتساب لمشاركة صورة الدوام")
          }
        }
        is AttendanceEvent.ShowBanner -> {
          snackbarHostState.showSnackbar(event.message)
        }
      }
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 700.dp

    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      topBar = {
        TopAppBar(
          title = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(
                    Brush.linearGradient(listOf(ClockDeepStart, ClockDeepEnd))
                  ),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Science,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(22.dp),
                )
              }
              Column {
                Text(
                  text = stringResource(R.string.app_name),
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                )
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showWhatsAppSettingsDialog = true }
                    .padding(vertical = 2.dp),
                ) {
                  Text(
                    text = "واتساب: $whatsappPhone",
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = WhatsAppGreen,
                  )
                  Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "تغيير رقم الواتساب",
                    tint = WhatsAppGreen,
                    modifier = Modifier.size(12.dp),
                  )
                }
              }
            }
          },
          actions = {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(end = 8.dp),
            ) {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
              ) {
                IconButton(
                  onClick = { showWhatsAppSettingsDialog = true },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("whatsapp_settings_button"),
                ) {
                  Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "إعدادات رقم الواتساب",
                    tint = WhatsAppGreen,
                  )
                }
              }

              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
              ) {
                IconButton(
                  onClick = {
                    signOutUser(
                      credentialManager = credentialManager,
                      scope = scope,
                    )
                  },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("sign_out_button"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = stringResource(R.string.sign_out),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
          ),
        )
      },
      bottomBar = {
        if (!useRail) {
          Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
          ) {
            NavigationBar(
              containerColor = MaterialTheme.colorScheme.surface,
              windowInsets = NavigationBarDefaults.windowInsets,
            ) {
              LabTab.entries.forEach { tab ->
                val selected = activeTab == tab
                NavigationBarItem(
                  selected = selected,
                  onClick = { activeTab = tab },
                  icon = {
                    Icon(
                      imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                      contentDescription = stringResource(tab.labelRes),
                    )
                  },
                  label = {
                    Text(
                      text = stringResource(tab.labelRes),
                      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                  },
                  colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                  ),
                  modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
                )
              }
            }
          }
        }
      },
      snackbarHost = {
        SnackbarHost(hostState = snackbarHostState) { data ->
          Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background,
          )
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (useRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight(),
          ) {
            LabTab.entries.forEach { tab ->
              val selected = activeTab == tab
              NavigationRailItem(
                selected = selected,
                onClick = { activeTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.labelRes),
                  )
                },
                label = { Text(stringResource(tab.labelRes)) },
              )
            }
          }
        }

        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.TopCenter,
        ) {
          Box(
            modifier = Modifier
              .widthIn(max = 780.dp)
              .fillMaxWidth(),
          ) {
            when (activeTab) {
              LabTab.DESK -> AttendanceDeskTab(
                clockState = clockState,
                deskState = deskState,
                staffState = staffState,
                attendanceState = attendanceState,
                biometricAvailability = biometricAvailability,
                whatsappPhone = whatsappPhone,
                onOpenWhatsAppSettings = { showWhatsAppSettingsDialog = true },
                onOpenBiometricSettings = {
                  BiometricAuthenticator.openBiometricEnrollSettings(context)
                },
                onSyncClock = viewModel::syncGlobalClock,
                onSelectStaff = viewModel::selectStaff,
                onOpenAddStaffDialog = { showAddStaffDialog = true },
                onNotesChange = viewModel::updateNotes,
                onCheckInClick = { staffList ->
                  val selected = staffList.firstOrNull { it.id == deskState.selectedStaffId }
                  if (selected == null) {
                    scope.launch {
                      snackbarHostState.showSnackbar("يرجى اختيار اسم الموظف أو إضافته أولاً")
                    }
                  } else {
                    startBiometricThenSelfie(PendingBiometricAction.CheckIn(selected, staffList))
                  }
                },
                onCheckOutClick = { record ->
                  if (record == null) {
                    scope.launch {
                      snackbarHostState.showSnackbar("لا يوجد تسجيل حضور مفتوح لهذا الموظف")
                    }
                  } else {
                    startBiometricThenSelfie(PendingBiometricAction.CheckOut(record))
                  }
                },
                onSendWhatsAppReceipt = { record, isCheckOut ->
                  scope.launch {
                    val launched = WhatsAppDispatcher.dispatchReceiptToWhatsApp(
                      context = context,
                      record = record,
                      isCheckOut = isCheckOut,
                      targetPhoneDisplay = whatsappPhone,
                    )
                    if (!launched) {
                      snackbarHostState.showSnackbar("تعذر فتح واتساب لمشاركة بطاقة الدوام")
                    }
                  }
                },
              )
              LabTab.HISTORY -> AttendanceHistoryTab(
                attendanceState = attendanceState,
                onSendWhatsAppReceipt = { record, isCheckOut ->
                  scope.launch {
                    val launched = WhatsAppDispatcher.dispatchReceiptToWhatsApp(
                      context = context,
                      record = record,
                      isCheckOut = isCheckOut,
                      targetPhoneDisplay = whatsappPhone,
                    )
                    if (!launched) {
                      snackbarHostState.showSnackbar("تعذر فتح واتساب لمشاركة بطاقة الدوام")
                    }
                  }
                },
                onDeleteRecord = viewModel::deleteRecord,
              )
              LabTab.STAFF -> StaffDirectoryTab(
                staffState = staffState,
                onOpenAddStaffDialog = { showAddStaffDialog = true },
                onToggleActive = viewModel::toggleStaffActive,
                onDeleteStaff = viewModel::deleteStaffMember,
                onSelectForCheckIn = { member ->
                  viewModel.selectStaff(member.id)
                  activeTab = LabTab.DESK
                },
              )
            }
          }
        }
      }
    }
  }

  activeSelfieCapture?.let { request ->
    val staffName = when (request) {
      is SelfieCaptureRequest.CheckIn -> request.staff.name
      is SelfieCaptureRequest.CheckOut -> request.record.staffName
    }
    val isCheckOut = request is SelfieCaptureRequest.CheckOut
    SelfieCaptureModal(
      staffName = staffName,
      isCheckOut = isCheckOut,
      clockState = clockState,
      onPhotoCaptured = { capturedFile ->
        activeSelfieCapture = null
        when (request) {
          is SelfieCaptureRequest.CheckIn -> {
            viewModel.stampGlobalCheckIn(
              staffList = request.staffList,
              selfieFile = capturedFile,
              biometricMethod = request.biometricMethod,
            )
          }
          is SelfieCaptureRequest.CheckOut -> {
            viewModel.stampGlobalCheckOut(
              activeRecord = request.record,
              selfieFile = capturedFile,
              biometricMethod = request.biometricMethod,
            )
          }
        }
      },
      onDismiss = { activeSelfieCapture = null },
    )
  }

  biometricPromptFallbackAction?.let { pendingAction ->
    val isNotEnrolled = biometricAvailability == BiometricAvailability.NOT_ENROLLED
    val deviceSecure = remember(context, pendingAction) {
      BiometricAuthenticator.isDeviceSecure(context)
    }

    AlertDialog(
      onDismissRequest = { biometricPromptFallbackAction = null },
      icon = {
        Icon(
          imageVector = Icons.Default.Fingerprint,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp),
        )
      },
      title = {
        Text(
          if (isNotEnrolled) {
            "لم يتم تسجيل بصمة على هذا الجهاز"
          } else {
            "مستشعر البصمة غير متوفر"
          }
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = if (isNotEnrolled) {
              "يعتمد تسجيل الحضور والانصراف على بصمة جهاز الموبايل. يرجى إضافة بصمة في إعدادات الأمان أولاً، أو استخدام رمز قفل الجهاز إن وُجد."
            } else {
              "لا يتوفر مستشعر بصمة جاهز على هذا الجهاز حالياً. يمكنك استخدام قفل أمان الجهاز أو المتابعة بالتوثيق بالسيلفي."
            }
          )
        }
      },
      confirmButton = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (isNotEnrolled) {
            Button(
              onClick = {
                biometricPromptFallbackAction = null
                BiometricAuthenticator.openBiometricEnrollSettings(context)
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("إضافة بصمة في الإعدادات")
            }
          }
          if (deviceSecure) {
            OutlinedButton(
              onClick = {
                biometricPromptFallbackAction = null
                BiometricAuthenticator.authenticateWithDeviceCredential(
                  context = context,
                  staffName = pendingAction.staffName,
                  isCheckOut = pendingAction.isCheckOut,
                  onSucceeded = { methodLabel ->
                    initiateSelfieCapture(pendingAction.toCaptureRequest(methodLabel))
                  },
                  onCancelled = {
                    scope.launch { snackbarHostState.showSnackbar("تم إلغاء التحقق") }
                  },
                  onError = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                  },
                )
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("التحقق بقفل الجهاز (PIN / النمط)")
            }
          } else if (!isNotEnrolled) {
            Button(
              onClick = {
                biometricPromptFallbackAction = null
                initiateSelfieCapture(pendingAction.toCaptureRequest("سيلفي بدون مستشعر بصمة"))
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("المتابعة بالتقاط السيلفي")
            }
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { biometricPromptFallbackAction = null }) {
          Text("إلغاء")
        }
      },
    )
  }

  if (showCameraDeniedDialog) {
    AlertDialog(
      onDismissRequest = { showCameraDeniedDialog = false },
      icon = {
        Icon(
          imageVector = Icons.Default.CameraAlt,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
      },
      title = { Text("إذن الكاميرا مطلوب للسيلفي") },
      text = {
        Text("يتطلب تسجيل الحضور والانصراف التقاط صورة سيلفي فورية لطباعة الوقت والتاريخ عليها وإرسالها عبر واتساب.")
      },
      confirmButton = {
        Button(
          onClick = {
            showCameraDeniedDialog = false
            val intent = Intent(
              Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
              Uri.fromParts("package", context.packageName, null),
            )
            runCatching { context.startActivity(intent) }
          }
        ) {
          Text("فتح الإعدادات")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCameraDeniedDialog = false }) {
          Text("إلغاء")
        }
      },
    )
  }

  if (showAddStaffDialog) {
    AddStaffDialog(
      isSaving = deskState.isAddingStaff,
      onDismiss = { showAddStaffDialog = false },
      onConfirm = { name, role ->
        viewModel.addStaffMember(name, role) {
          showAddStaffDialog = false
        }
      },
    )
  }

  if (showWhatsAppSettingsDialog) {
    WhatsAppSettingsDialog(
      currentPhone = whatsappPhone,
      onSavePhone = { rawInput, onSuccess, onError ->
        viewModel.updateWhatsAppPhone(rawInput, onSuccess, onError)
      },
      onResetDefault = { onSuccess ->
        viewModel.resetWhatsAppPhone(onSuccess)
      },
      onDismiss = { showWhatsAppSettingsDialog = false },
    )
  }
}

@Composable
private fun AddStaffDialog(
  isSaving: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (name: String, role: String) -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var role by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = { if (!isSaving) onDismiss() },
    title = { Text("إضافة موظف مختبر") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("اسم الموظف الكامل") },
          placeholder = { Text("مثال: د. علي حسن") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("add_staff_name_input"),
        )
        OutlinedTextField(
          value = role,
          onValueChange = { role = it },
          label = { Text("التخصص / الصفة (اختياري)") },
          placeholder = { Text("مثال: أخصائي تحاليل مرضية") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("add_staff_role_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(name, role) },
        enabled = name.trim().isNotEmpty() && !isSaving,
        modifier = Modifier.testTag("confirm_add_staff_button"),
      ) {
        Text(if (isSaving) "جاري الحفظ..." else "حفظ")
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

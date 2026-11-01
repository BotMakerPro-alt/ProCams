package com.procam.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.procam.camera.camera.CameraController
import com.procam.camera.database.VaultMedia
import com.procam.camera.launcher.AppRepository
import com.procam.camera.security.PinManager
import com.procam.camera.settings.ProCamPreferences
import com.procam.camera.vault.VaultRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Ink = Color(0xFF090A0D)
private val Panel = Color(0xFF17191F)
private val Gold = Color(0xFFD8B56A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9,10,13)
        window.navigationBarColor = android.graphics.Color.rgb(9,10,13)
        setContent { ProCamApp(this) }
    }
    fun authenticateBiometric(onResult: (Boolean) -> Unit) {
        val manager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) { onResult(false); return }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onResult(true) }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onResult(false) }
            override fun onAuthenticationFailed() { }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock ProCam Secret Space").setSubtitle("Verify your identity").setAllowedAuthenticators(authenticators).build())
    }
}

@Composable
private fun ProCamApp(activity: MainActivity) {
    val context = LocalContext.current
    val prefs = remember { ProCamPreferences(context) }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("camera") }
    var authenticated by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf(!PinManager(context).isConfigured()) }
    var biometricEnabled by remember { mutableStateOf(false) }
    var autoLockSeconds by remember { mutableIntStateOf(30) }
    var holdSeconds by remember { mutableIntStateOf(5) }
    var grid by remember { mutableStateOf(false) }
    var haptics by remember { mutableStateOf(true) }
    var theme by remember { mutableStateOf("dark") }
    var secretPage by remember { mutableStateOf("home") }
    LaunchedEffect(Unit) {
        prefs.biometric.collect { biometricEnabled = it }
    }
    LaunchedEffect(Unit) { prefs.autoLock.collect { autoLockSeconds = it } }
    LaunchedEffect(Unit) { prefs.hold.collect { holdSeconds = it } }
    LaunchedEffect(Unit) { prefs.grid.collect { grid = it } }
    LaunchedEffect(Unit) { prefs.haptics.collect { haptics = it } }
    LaunchedEffect(Unit) { prefs.theme.collect { theme = it } }
    DisposableEffect(activity, page, authenticated, autoLockSeconds) {
        var wasBackgrounded = false
        var lockJob: kotlinx.coroutines.Job? = null
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && authenticated && page == "secret") {
                wasBackgrounded = true
                lockJob?.cancel()
                lockJob = scope.launch {
                    if (autoLockSeconds > 0) delay(autoLockSeconds * 1000L)
                    if (wasBackgrounded && page == "secret") { authenticated = false; page = "camera"; activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
                }
            } else if (event == Lifecycle.Event.ON_START && wasBackgrounded) {
                wasBackgrounded = false; lockJob?.cancel()
                if (authenticated && page == "secret") { authenticated = false; page = "camera"; activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { lockJob?.cancel(); activity.lifecycle.removeObserver(observer) }
    }
    SideEffect {
        if (page == "secret" && authenticated) activity.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    MaterialTheme(colorScheme = if (theme == "light") lightColorScheme(primary = Gold) else darkColorScheme(primary = Gold, background = Ink, surface = Panel)) {
        when (page) {
            "camera" -> CameraScreen(holdSeconds, grid, haptics, onSecret = { page = "auth" }, onSettings = { page = "settings" })
            "auth" -> PinScreen(setup, biometricEnabled, activity, onSuccess = { authenticated = true; setup = false; secretPage = "home"; page = "secret" }, onCancel = { page = "camera" })
            "secret" -> if (authenticated) SecretDashboard(secretPage, { secretPage = it }, { authenticated = false; page = "camera" }, biometricEnabled) else CameraScreen(holdSeconds, grid, haptics, { page = "auth" }, { page = "settings" })
            "settings" -> SettingsScreen(biometricEnabled, autoLockSeconds, holdSeconds, grid, haptics, theme, onBack = { page = "camera" }, onBiometric = { scope.launch { prefs.setBiometric(it) } }, onAutoLock = { scope.launch { prefs.setAutoLock(it) } }, onHold = { scope.launch { prefs.setHold(it) } }, onGrid = { scope.launch { prefs.setGrid(it) } }, onHaptics = { scope.launch { prefs.setHaptics(it) } }, onTheme = { scope.launch { prefs.setTheme(it) } })
        }
    }
}

@Composable
private fun CameraScreen(holdSeconds: Int, grid: Boolean, haptics: Boolean, onSecret: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { CameraController(context) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var message by remember { mutableStateOf("") }
    var cameraReady by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    val permissions = remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissions.value = it }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (!granted) Toast.makeText(context, "Video will be recorded without audio", Toast.LENGTH_SHORT).show() }
    LaunchedEffect(Unit) { if (!permissions.value) permissionLauncher.launch(Manifest.permission.CAMERA) }
    fun bind() {
        val view = previewView ?: return
        val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
        controller.bind(lifecycleOwner, preview, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA) { cameraReady = it != null }
    }
    LaunchedEffect(previewView, front, permissions.value) { if (permissions.value && previewView != null) bind() }
    Box(Modifier.fillMaxSize().background(Ink)) {
        if (permissions.value) AndroidView(factory = { ctx -> PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; previewView = this } }, modifier = Modifier.fillMaxSize())
        else Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.CameraAlt, null, tint = Gold, modifier = Modifier.size(54.dp)); Text("Camera permission required", color = Color.White); Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Grant camera access") } }
        if (grid) CanvasGrid(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("PROCAM", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Row { IconButton(onClick = { flash = !flash; controller.setFlash(flash) }) { Icon(if (flash) Icons.Default.FlashOn else Icons.Default.FlashOff, "Flash", tint = Color.White) }; IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) } }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (message.isNotBlank()) Text(message, color = Gold, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { front = !front }) { Icon(Icons.Default.Cameraswitch, "Switch camera", tint = Color.White, modifier = Modifier.size(30.dp)) }
                    Box(Modifier.size(88.dp).background(Color.White.copy(alpha = .13f), CircleShape).border(3.dp, if (isRecording) Color.Red else Color.White, CircleShape).pointerInput(isRecording, holdSeconds) {
                        detectTapGestures(onPress = {
                            var triggered = false
                            val job = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.coroutineContext).launch {
                                val total = (holdSeconds * 10).coerceAtLeast(10)
                                for (i in 1..total) { delay(100); holdProgress = i.toFloat() / total }
                                triggered = true; holdProgress = 0f
                                if (haptics) { val v = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator; if (android.os.Build.VERSION.SDK_INT >= 26) v?.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)) }
                                onSecret()
                            }
                            tryAwaitRelease()
                            if (!triggered) { job.cancel(); holdProgress = 0f; if (!isRecording) controller.takePhoto { ok -> message = if (ok) "Photo saved" else "Capture failed" } }
                        })
                    }, contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { holdProgress }, modifier = Modifier.size(82.dp), color = Gold, strokeWidth = 3.dp)
                        Box(Modifier.size(68.dp).background(if (isRecording) Color.Red else Color.White, CircleShape))
                    }
                    IconButton(onClick = { zoom = if (zoom < 2f) 2f else 1f; controller.setZoom(zoom) }) { Text("${zoom.toInt()}×", color = Color.White, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("PHOTO", color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 28.dp))
                    TextButton(onClick = {
                        val audio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (!audio) { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO); return@TextButton }
                        controller.toggleRecording { active, error -> isRecording = active; message = error ?: if (active) "Recording…" else "Video saved" }
                    }) { Text(if (isRecording) "STOP VIDEO" else "VIDEO", color = if (isRecording) Color.Red else Color.White) }
                }
                Text("Hold shutter $holdSeconds seconds for Secret Space", color = Color.White.copy(alpha = .55f), fontSize = 10.sp)
            }
        }
    }
}

@Composable private fun CanvasGrid(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) { val color = Color.White.copy(alpha = .24f); for (i in 1..2) { val x = size.width * i / 3; val y = size.height * i / 3; drawLine(color, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1f); drawLine(color, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f) } }
}

@Composable
private fun PinScreen(setup: Boolean, biometricEnabled: Boolean, activity: MainActivity, onSuccess: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current; val manager = remember { PinManager(context) }
    var pin by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }; var attempts by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Ink).padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Lock, null, tint = Gold, modifier = Modifier.size(48.dp)); Spacer(Modifier.height(20.dp))
        Text(if (setup) "Create Secret Space PIN" else "Unlock Secret Space", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(22.dp)); OutlinedTextField(pin, { if (it.length <= 12 && it.all(Char::isDigit)) pin = it }, label = { Text("${if (setup) "New " else ""}PIN (4–12 digits)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        if (setup) { Spacer(Modifier.height(10.dp)); OutlinedTextField(confirm, { if (it.length <= 12 && it.all(Char::isDigit)) confirm = it }, label = { Text("Confirm PIN") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) }
        Text(error, color = Color(0xFFFF7777), modifier = Modifier.padding(8.dp))
        Button(onClick = { try { if (setup) { if (pin != confirm || pin.length < 4) error = "PINs must match and contain at least 4 digits" else { manager.setPin(pin.toCharArray()); onSuccess() } } else if (attempts >= 5) error = "Wait 30 seconds before trying again" else if (manager.verify(pin.toCharArray())) onSuccess() else { attempts++; error = "Incorrect PIN (${attempts}/5)" } } catch (e: Exception) { error = e.message ?: "Could not save PIN" } }, modifier = Modifier.fillMaxWidth()) { Text(if (setup) "Create secure space" else "Unlock") }
        if (!setup && biometricEnabled) TextButton(onClick = { activity.authenticateBiometric { ok -> if (ok) onSuccess() else error = "Biometric authentication unavailable or cancelled" } }) { Text("Use fingerprint / device credential", color = Gold) }
        TextButton(onClick = onCancel) { Text("Cancel", color = Color.White.copy(alpha = .7f)) }
    }
    LaunchedEffect(attempts) { if (attempts >= 5) { delay(30_000); attempts = 0; error = "You can try again" } }
}

@Composable
private fun SecretDashboard(section: String, onSection: (String) -> Unit, onLock: () -> Unit, biometric: Boolean) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val vault = remember { VaultRepository(context) }; val apps = remember { AppRepository(context) }
    val media by vault.media().collectAsState(initial = emptyList()); val trash by vault.trash().collectAsState(initial = emptyList()); val used by vault.usedBytes().collectAsState(initial = 0L)
    val selectedApps by apps.observeSelected().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }; var appCatalog by remember { mutableStateOf(emptyList<com.procam.camera.launcher.LaunchableApp>()) }; var error by remember { mutableStateOf("") }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> uris.forEach { uri -> scope.launch { runCatching { val name = queryDisplayName(context, uri); vault.importMedia(uri, name, context.contentResolver.getType(uri) ?: "application/octet-stream") }.onFailure { error = it.message ?: "Import failed" } } } }
    var exportTarget by remember { mutableStateOf<VaultMedia?>(null) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> val item = exportTarget; if (uri != null && item != null) scope.launch { runCatching { vault.export(item.id, uri) }.onFailure { error = it.message ?: "Export failed" } } }
    Column(Modifier.fillMaxSize().background(Ink).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("SECRET SPACE", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp); IconButton(onClick = onLock) { Icon(Icons.Default.Lock, "Lock", tint = Gold) } }
        if (section == "home") {
            Text("Encrypted private area", color = Color.White.copy(alpha = .55f)); Spacer(Modifier.height(16.dp))
            DashboardTile("Private Photos & Videos", Icons.Default.Photo, Modifier.fillMaxWidth()) { onSection("media") }
            Spacer(Modifier.height(8.dp)); DashboardTile("Private App Launcher", Icons.Default.Apps, Modifier.fillMaxWidth()) { appCatalog = apps.installedLaunchableApps(); onSection("apps") }
            Spacer(Modifier.height(8.dp)); DashboardTile("Trash", Icons.Default.Delete, Modifier.fillMaxWidth()) { onSection("trash") }
            Spacer(Modifier.height(8.dp)); DashboardTile("Vault Settings", Icons.Default.Settings, Modifier.fillMaxWidth()) { onSection("vaultsettings") }
            Spacer(Modifier.height(20.dp)); Text("Encrypted storage: ${used / (1024 * 1024)} MB", color = Gold)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { onSection("home") }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }; Text(when (section) { "apps" -> "Private Apps"; "trash" -> "Recently Deleted"; "vaultsettings" -> "Vault Settings"; "changepin" -> "Change PIN"; else -> "Private Media" }, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            when (section) {
                "apps" -> {
                    OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search installed apps") }, singleLine = true)
                    Text("Selected apps are stored persistently. Android's launcher and Settings may still show them.", color = Color.White.copy(alpha = .55f), fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Your private launcher", color = Gold); TextButton(onClick = { appCatalog = apps.installedLaunchableApps() }) { Text("Refresh list") } }
                    LazyColumn(Modifier.weight(1f)) {
                        items(selectedApps, key = { it.packageName }) { saved ->
                            val app = appCatalog.firstOrNull { it.packageName == saved.packageName }
                            Row(Modifier.fillMaxWidth().clickable { if (!apps.launch(saved.packageName)) error = "App is uninstalled, disabled or cannot be launched" }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(saved.customName ?: app?.label ?: saved.packageName, color = Color.White); Text(saved.packageName, color = Color.Gray, fontSize = 10.sp) }
                                TextButton(onClick = { if (!apps.launch(saved.packageName)) error = "App unavailable" }) { Text("OPEN") }
                                IconButton(onClick = { scope.launch { apps.remove(saved.packageName) } }) { Icon(Icons.Default.RemoveCircleOutline, null, tint = Color.Red) }
                            }; HorizontalDivider(color = Color.White.copy(alpha = .08f))
                        }
                    }
                    Text("Add installed apps", color = Gold, fontWeight = FontWeight.Bold)
                    LazyColumn(Modifier.weight(1f)) { items(appCatalog.filter { a -> a.label.contains(query, true) && selectedApps.none { it.packageName == a.packageName } }, key = { it.packageName }) { app -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(app.label, color = Color.White, modifier = Modifier.weight(1f)); TextButton(onClick = { scope.launch { apps.add(app.packageName) } }) { Text("ADD") } } } }
                }
                "media" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { importPicker.launch(arrayOf("image/*")) }) { Text("Import photos") }; Button(onClick = { importPicker.launch(arrayOf("video/*")) }) { Text("Import videos") } }
                    Text("Items are encrypted in app-private storage. Export creates a normal copy.", color = Color.White.copy(alpha = .55f), fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                    LazyColumn(Modifier.weight(1f)) { items(media, key = { it.id }) { item -> MediaRow(item, onExport = { exportTarget = item; exportPicker.launch(item.displayName) }, onTrash = { scope.launch { vault.moveToTrash(item.id) } }, onRename = { name -> scope.launch { runCatching { vault.rename(item.id, name) }.onFailure { error = it.message ?: "Rename failed" } } }, onView = { scope.launch { runCatching { val file = vault.restoreToTemp(item.id); val mime = item.mimeType; val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file), mime).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION); context.startActivity(intent) }.onFailure { error = it.message ?: "Cannot preview" } } }) } }
                }
                "trash" -> LazyColumn { items(trash, key = { it.id }) { item -> Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Text(item.displayName, color = Color.White, modifier = Modifier.weight(1f)); TextButton(onClick = { scope.launch { vault.restore(item.id) } }) { Text("RESTORE") }; TextButton(onClick = { scope.launch { vault.permanentlyDelete(item.id) } }) { Text("DELETE") } } } }
                "changepin" -> ChangePinScreen(onDone = { onSection("vaultsettings") })
                "vaultsettings" -> { TextButton(onClick = { onSection("changepin") }) { Text("Change PIN", color = Gold) }; Text("Private files: ${media.size}", color = Color.White); Text("Encrypted storage used: ${used / (1024 * 1024)} MB", color = Color.White); Text("Biometric unlock: ${if (biometric) "Enabled" else "Disabled in app settings"}", color = Color.White); Text("Deletion is best-effort; flash storage may retain physical remnants.", color = Gold, fontSize = 12.sp) }
            }
        }
        if (error.isNotBlank()) Text(error, color = Color(0xFFFF7777), modifier = Modifier.clickable { error = "" }.padding(8.dp))
    }
}

@Composable private fun MediaRow(item: VaultMedia, onExport: () -> Unit, onTrash: () -> Unit, onRename: (String) -> Unit, onView: () -> Unit) {
    var renaming by remember { mutableStateOf(false) }; var name by remember(item.id) { mutableStateOf(item.displayName) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (renaming) { OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), singleLine = true); TextButton(onClick = { onRename(name); renaming = false }) { Text("SAVE NAME") } }
        else Text(item.displayName, color = Color.White, fontWeight = FontWeight.Medium)
        Text("${item.mimeType} · ${item.sizeBytes / 1024} KB", color = Color.Gray, fontSize = 10.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { TextButton(onClick = onView) { Text("VIEW") }; TextButton(onClick = onExport) { Text("EXPORT") }; TextButton(onClick = { renaming = true }) { Text("RENAME") }; TextButton(onClick = onTrash) { Text("TRASH") } }
        HorizontalDivider(color = Color.White.copy(alpha = .08f))
    }
}

@Composable private fun DashboardTile(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Gold, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(14.dp)); Text(title, color = Color.White, fontWeight = FontWeight.Medium) } }
}

@Composable
private fun SettingsScreen(biometric: Boolean, autoLock: Int, hold: Int, grid: Boolean, haptics: Boolean, theme: String, onBack: () -> Unit, onBiometric: (Boolean) -> Unit, onAutoLock: (Int) -> Unit, onHold: (Int) -> Unit, onGrid: (Boolean) -> Unit, onHaptics: (Boolean) -> Unit, onTheme: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }; Text("Settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Text("CAMERA", color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        SettingSwitch("Camera grid", grid, onGrid); SettingSwitch("Haptic feedback", haptics, onHaptics)
        Text("SECRET SPACE", color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        SettingSwitch("Biometric unlock", biometric, onBiometric)
        Text("Auto-lock when app leaves foreground", color = Color.White); Text("Configured delay: ${if (autoLock == 0) "Immediate" else "$autoLock seconds"}", color = Color.Gray, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(0, 15, 30, 60).forEach { v -> FilterChip(selected = autoLock == v, onClick = { onAutoLock(v) }, label = { Text(if (v == 0) "Now" else "${v}s") }) } }
        Text("Secret shutter hold duration: ${hold}s", color = Color.White, modifier = Modifier.padding(top = 12.dp))
        Slider(value = hold.toFloat(), onValueChange = { onHold(it.toInt().coerceIn(3, 10)) }, valueRange = 3f..10f, steps = 6)
        Text("APPEARANCE", color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("dark", "light", "system").forEach { v -> FilterChip(selected = theme == v, onClick = { onTheme(v) }, label = { Text(v.replaceFirstChar(Char::uppercase)) }) } }
        Text("Security", color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp)); Text("PIN changes are available from the Secret Space authentication flow in this build. Biometric unlock uses Android BiometricPrompt with device credential fallback.", color = Color.White.copy(alpha = .65f), fontSize = 12.sp)
    }
}
@Composable private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, color = Color.White); Switch(checked, onChange) } }

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    var name = "private_media_${System.currentTimeMillis()}"
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
    return name
}

@Composable private fun ChangePinScreen(onDone: () -> Unit) {
    val context = LocalContext.current; val manager = remember { PinManager(context) }
    var old by remember { mutableStateOf("") }; var fresh by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(old, { if (it.length <= 12 && it.all(Char::isDigit)) old = it }, label = { Text("Current PIN") }, visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(fresh, { if (it.length <= 12 && it.all(Char::isDigit)) fresh = it }, label = { Text("New PIN") }, visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(confirm, { if (it.length <= 12 && it.all(Char::isDigit)) confirm = it }, label = { Text("Confirm new PIN") }, visualTransformation = PasswordVisualTransformation())
        Text(error, color = Color.Red)
        Button(onClick = { if (!manager.verify(old.toCharArray())) error = "Current PIN is incorrect" else if (fresh.length < 4 || fresh != confirm) error = "New PINs must match and contain at least 4 digits" else { manager.setPin(fresh.toCharArray()); onDone() } }) { Text("Update PIN") }
    }
}

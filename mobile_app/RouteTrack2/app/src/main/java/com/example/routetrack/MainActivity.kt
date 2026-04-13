package com.example.routetrack

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.launch

// ============= THEME =============

object RouteTrackColors {
    val PrimaryGreen = Color(0xFF2DA53D)
    val DarkGreen    = Color(0xFF1F3A3F)
    val LightGray    = Color(0xFFF5F5F5)
    val TextGray     = Color(0xFF666666)
    val Border       = Color(0xFFE0E0E0)
    val Warning      = Color(0xFFFFC107)
    val Success      = Color(0xFF4CAF50)
    val Error        = Color(0xFFE74C3C)
}

object AppDimensions {
    val LogoSmall    = 80.dp
    val LogoMedium   = 240.dp
    val LogoLarge    = 450.dp
    val ButtonHeight = 52.dp
    val IconSize     = 20.dp
    val XSmall       = 4.dp
    val Small        = 8.dp
    val Medium       = 12.dp
    val Large        = 16.dp
    val XLarge       = 24.dp
    val XXLarge      = 32.dp
    val CornerSmall  = 8.dp
    val CornerMedium = 12.dp
    val CornerLarge  = 16.dp
}

// ============= SHARED PREFERENCES HELPER =====================
// Persists login state so users don't have to re-enter credentials
// every time they open the app.

private const val PREFS_NAME   = "routetrack_prefs"
private const val KEY_TOKEN    = "auth_token"
private const val KEY_USERNAME = "username"
private const val KEY_EMAIL    = "user_email"
private const val KEY_ROLE     = "user_role"

fun saveSession(context: Context, token: String, username: String, email: String, role: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
        putString(KEY_TOKEN,    token)
        putString(KEY_USERNAME, username)
        putString(KEY_EMAIL,    email)
        putString(KEY_ROLE,     role)
        apply()
    }
}

fun clearSession(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
}

data class SavedSession(
    val token: String,
    val username: String,
    val email: String,
    val role: String
)

fun loadSession(context: Context): SavedSession? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val token = prefs.getString(KEY_TOKEN, null) ?: return null
    return SavedSession(
        token    = token,
        username = prefs.getString(KEY_USERNAME, "") ?: "",
        email    = prefs.getString(KEY_EMAIL,    "") ?: "",
        role     = prefs.getString(KEY_ROLE,     "") ?: ""
    )
}

// ============= MAIN ACTIVITY =============

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()

        setContent {
            // ── Restore session from SharedPreferences ──────────────────────
            val ctx             = LocalContext.current
            val savedSession    = remember { loadSession(ctx) }

            var screen        by remember { mutableStateOf(
                if (savedSession != null) {
                    if (savedSession.role == "student") "studentHome" else "driverHome"
                } else "role"
            )}
            var role          by remember { mutableStateOf(savedSession?.role ?: "") }
            var username      by remember { mutableStateOf(savedSession?.username ?: "") }
            var userEmail     by remember { mutableStateOf(savedSession?.email ?: "") }

            var tripActive        by remember { mutableStateOf(false) }
            var tripStartTime     by remember { mutableStateOf("") }
            var tripStopTime      by remember { mutableStateOf("") }
            var isBroadcasting    by remember { mutableStateOf(false) }
            var broadcastBusNumber by remember { mutableStateOf("") }

            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                RetrofitClient.warmUp()
            }

            val locationPermLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { perms ->
                val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                if (!granted) Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show()
            }

            LaunchedEffect(Unit) {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    locationPermLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }

            val navigateBack: () -> Unit = {
                when (screen) {
                    "login"                           -> screen = "role"
                    "signup", "forgotPassword"        -> screen = "login"
                    "busDetails"                      -> screen = "studentHome"
                    "liveLocation", "tripUpdate",
                    "profile"                         -> screen = if (role == "student") "studentHome" else "driverHome"
                    "driverLocation", "driverUpdates" -> screen = "driverHome"
                    "studentHome", "driverHome"       -> screen = "role"
                    else                              -> finish()
                }
            }

            BackHandler { if (screen == "role") finish() else navigateBack() }

            when (screen) {
                "role" -> RoleSelectionScreen(
                    onStudent = { role = "student"; screen = "login" },
                    onDriver  = { role = "driver";  screen = "login" },
                    onBack    = { finish() }
                )

                "login" -> LoginScreen(
                    role = role,
                    onLogin = { u, p ->
                        scope.launch {
                            try {
                                val res = RetrofitClient.instance.loginJson(
                                    LoginRequest(u.trim().lowercase(), p, role)
                                )
                                if (res.success) {
                                    username  = res.user?.username ?: res.username ?: u
                                    userEmail = res.user?.email ?: u
                                    // ── Persist session ──────────────────
                                    saveSession(ctx, res.access_token ?: "", username, userEmail, role)
                                    screen = if (role == "student") "studentHome" else "driverHome"
                                    Toast.makeText(this@MainActivity, "Logged in successfully", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@MainActivity, res.message ?: "Login failed", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Log.e("Login", "error", e)
                                Toast.makeText(this@MainActivity, "Connection error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                        true
                    },
                    onSignUp         = { screen = "signup" },
                    onForgotPassword = { screen = "forgotPassword" },
                    onBack           = navigateBack
                )

                "signup" -> SignUpScreen(
                    role     = role,
                    onSignUp = { name, email, pass, phone ->
                        scope.launch {
                            try {
                                val res = RetrofitClient.instance.signup(
                                    SignupRequest(
                                        username     = name.trim(),
                                        email        = email.trim().lowercase(),
                                        password     = pass,
                                        role         = role,
                                        phone_number = phone.takeIf { it.isNotBlank() }
                                    )
                                )
                                if (res.success) {
                                    username  = res.user?.username ?: name.trim()
                                    userEmail = email.trim().lowercase()
                                    // ── Persist session ──────────────────
                                    saveSession(ctx, res.access_token ?: "", username, userEmail, role)
                                    screen = if (role == "student") "studentHome" else "driverHome"
                                    Toast.makeText(this@MainActivity, "Account created", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@MainActivity, res.message ?: "Signup failed", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Log.e("Signup", "error", e)
                                Toast.makeText(this@MainActivity, "Connection error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                        true
                    },
                    onBack = navigateBack
                )

                "forgotPassword" -> ForgotPasswordScreen(
                    onReset = { email ->
                        scope.launch {
                            Toast.makeText(this@MainActivity, "Reset initiated for $email", Toast.LENGTH_SHORT).show()
                        }
                        true
                    },
                    onBack = navigateBack
                )

                "studentHome"    -> StudentHomeScreen(username = username, onNavigate = { screen = it }, onBack = navigateBack)
                "busDetails"     -> BusDetailsScreen(onBack = navigateBack)
                "liveLocation"   -> LiveLocationScreen(onNavigate = { screen = it }, onBack = navigateBack)
                "tripUpdate"     -> TripUpdateScreen(onNavigate = { screen = it }, onBack = navigateBack)

                "profile" -> ProfileScreen(
                    username   = username,
                    email      = userEmail,
                    role       = role,
                    onNavigate = { screen = it },
                    onLogout   = {
                        clearSession(ctx)           // ── Wipe saved session ──
                        screen = "role"; username = ""; userEmail = ""
                        isBroadcasting = false; broadcastBusNumber = ""
                        tripActive = false; tripStartTime = ""; tripStopTime = ""
                    },
                    onBack = navigateBack
                )

                "driverHome" -> DriverHomeScreen(
                    username       = username,
                    isBroadcasting = isBroadcasting,
                    tripStartTime  = tripStartTime,
                    onLogout       = {
                        clearSession(ctx)           // ── Wipe saved session ──
                        screen = "role"; username = ""
                        isBroadcasting = false; broadcastBusNumber = ""
                        tripActive = false; tripStartTime = ""; tripStopTime = ""
                    },
                    onNavigate = { screen = it },
                    onBack     = navigateBack
                )

                "driverLocation" -> DriverLocationScreen(
                    tripActive         = tripActive,
                    tripStartTime      = tripStartTime,
                    isBroadcasting     = isBroadcasting,
                    broadcastBusNumber = broadcastBusNumber,
                    onTripChanged      = { active, startTime, stopTime, busNum ->
                        tripActive         = active
                        tripStartTime      = startTime
                        tripStopTime       = stopTime
                        isBroadcasting     = active
                        broadcastBusNumber = busNum
                    },
                    onNavigate = { screen = it },
                    onBack     = navigateBack
                )

                "driverUpdates" -> DriverUpdatesScreen(
                    tripActive    = tripActive,
                    tripStartTime = tripStartTime,
                    tripStopTime  = tripStopTime,
                    isBroadcasting = isBroadcasting,
                    onNavigate    = { screen = it },
                    onBack        = navigateBack
                )
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                "bus_notification", "Bus Notifications", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Real-time bus tracking alerts"; enableVibration(true) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun showBusNotification(message: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val n = NotificationCompat.Builder(this, "bus_notification")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("RouteTrack Alert")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .build()
        nm.notify(1, n)
    }
}

// ============= REUSABLE COMPONENTS =============

@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = AppDimensions.LogoMedium) {
    Image(
        painter = painterResource(id = R.drawable.logo),
        contentDescription = "RouteTrack Logo",
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    subtitle: String = "",
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo(size = AppDimensions.LogoSmall)
                Spacer(Modifier.width(AppDimensions.Medium))
                Column {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen)
                    if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 12.sp, color = RouteTrackColors.TextGray)
                }
            }
        },
        navigationIcon = if (onBackClick != null) {
            { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = RouteTrackColors.DarkGreen) } }
        } else { {} },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
    )
}

@Composable
fun ModernButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = RouteTrackColors.PrimaryGreen,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(AppDimensions.ButtonHeight),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(AppDimensions.CornerMedium),
        enabled = enabled
    ) {
        if (icon != null) { icon(); Spacer(Modifier.width(AppDimensions.Small)) }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ModernCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(AppDimensions.XSmall),
        shape = RoundedCornerShape(AppDimensions.CornerLarge),
        elevation = CardDefaults.cardElevation(3.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, RouteTrackColors.Border)
    ) {
        Column(modifier = Modifier.padding(AppDimensions.XLarge), content = content)
    }
}

@Composable
fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onPasswordVisibilityToggle: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = RouteTrackColors.PrimaryGreen) },
        trailingIcon = if (isPassword && onPasswordVisibilityToggle != null) {
            {
                IconButton(onClick = onPasswordVisibilityToggle) {
                    Icon(
                        if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        null, tint = RouteTrackColors.TextGray
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !isPasswordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(AppDimensions.CornerMedium),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = RouteTrackColors.PrimaryGreen,
            unfocusedBorderColor = RouteTrackColors.Border
        )
    )
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = RouteTrackColors.DarkGreen) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(AppDimensions.XSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = RouteTrackColors.TextGray, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
fun StatusBadge(text: String, color: Color = RouteTrackColors.Warning) {
    Surface(color = color, shape = RoundedCornerShape(20.dp)) {
        Text(
            text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

data class BottomNavItem(val label: String, val icon: ImageVector)

@Composable
fun AppBottomNav(items: List<BottomNavItem>, selectedIndex: Int, onItemSelected: (Int) -> Unit) {
    NavigationBar(containerColor = Color.White, modifier = Modifier.fillMaxWidth(), tonalElevation = 8.dp) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                icon     = { Icon(item.icon, item.label) },
                label    = { Text(item.label, fontSize = 11.sp) },
                selected = selectedIndex == index,
                onClick  = { onItemSelected(index) },
                colors   = NavigationBarItemDefaults.colors(
                    selectedIconColor   = RouteTrackColors.PrimaryGreen,
                    selectedTextColor   = RouteTrackColors.PrimaryGreen,
                    unselectedIconColor = RouteTrackColors.TextGray,
                    unselectedTextColor = RouteTrackColors.TextGray
                )
            )
        }
    }
}

// ============= SCREENS =============

@Composable
fun RoleSelectionScreen(onStudent: () -> Unit, onDriver: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(AppDimensions.XLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = RouteTrackColors.DarkGreen)
        }
        AppLogo(size = AppDimensions.LogoLarge)
        Text("Smart Bus Tracking System", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = RouteTrackColors.TextGray)
        Spacer(Modifier.height(AppDimensions.XXLarge))
        ModernButton("Continue as Student or Staff", onStudent)
        Spacer(Modifier.height(AppDimensions.Medium))
        ModernButton(
            "Continue as Driver", onDriver,
            backgroundColor = RouteTrackColors.DarkGreen,
            icon = { Icon(Icons.Default.DirectionsCar, null, modifier = Modifier.size(AppDimensions.IconSize)) }
        )
        Spacer(Modifier.height(AppDimensions.XXLarge))
        Text("Your trusted companion for real-time bus tracking", fontSize = 12.sp, color = RouteTrackColors.TextGray)
    }
}

@Composable
fun LoginScreen(
    role: String,
    onLogin: (String, String) -> Boolean,
    onSignUp: () -> Unit,
    onForgotPassword: () -> Unit,
    onBack: () -> Unit
) {
    var username        by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading       by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.White)
            .verticalScroll(rememberScrollState()).padding(AppDimensions.XLarge),
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = RouteTrackColors.DarkGreen)
        }
        AppLogo(size = AppDimensions.LogoMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(AppDimensions.XXLarge))
        Text("Welcome Back", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("Login to your ${if (role == "student") "Student" else "Driver"} Account",
            fontSize = 14.sp, color = RouteTrackColors.TextGray,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(AppDimensions.XXLarge))
        ModernTextField(username, { username = it }, "Username or Email", Icons.Default.Person)
        Spacer(Modifier.height(AppDimensions.Medium))
        ModernTextField(
            password, { password = it }, "Password", Icons.Default.Lock,
            isPassword = true, isPasswordVisible = passwordVisible,
            onPasswordVisibilityToggle = { passwordVisible = !passwordVisible }
        )
        Spacer(Modifier.height(AppDimensions.Small))
        Text("Forgot password?", color = RouteTrackColors.PrimaryGreen, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.End).clickable { onForgotPassword() })
        Spacer(Modifier.height(AppDimensions.XXLarge))
        ModernButton(
            if (isLoading) "Logging in..." else "Login",
            onClick = { isLoading = true; onLogin(username, password); isLoading = false },
            enabled = username.isNotEmpty() && password.isNotEmpty() && !isLoading
        )
        Spacer(Modifier.height(AppDimensions.Medium))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Don't have an account? ", fontSize = 12.sp, color = RouteTrackColors.TextGray)
            Text("Sign Up", fontSize = 12.sp, color = RouteTrackColors.PrimaryGreen,
                fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onSignUp() })
        }
    }
}

// ── UPDATED SignUpScreen: driver must provide phone; validated against admin records ──
@Composable
fun SignUpScreen(
    role: String,
    onSignUp: (name: String, email: String, password: String, phone: String) -> Boolean,
    onBack: () -> Unit
) {
    val scope           = rememberCoroutineScope()
    var username        by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf("") }
    var phone           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading       by remember { mutableStateOf(false) }
    var errorMessage    by remember { mutableStateOf("") }

    // Driver phone-check state
    var phoneCheckStatus by remember { mutableStateOf("") }   // "ok" | "not_found" | "has_account" | ""
    var phoneCheckName   by remember { mutableStateOf("") }
    var isCheckingPhone  by remember { mutableStateOf(false) }

    val isDriver = role == "driver"

    // Debounced phone-number validation for driver signup
    LaunchedEffect(phone) {
        if (!isDriver) return@LaunchedEffect
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 10) { phoneCheckStatus = ""; phoneCheckName = ""; return@LaunchedEffect }
        isCheckingPhone = true
        kotlinx.coroutines.delay(600) // debounce
        try {
            val result = RetrofitClient.instance.checkDriverPhone(phone.trim())
            when {
                !result.registered        -> { phoneCheckStatus = "not_found"; phoneCheckName = "" }
                result.already_has_account -> { phoneCheckStatus = "has_account"; phoneCheckName = result.driver_name ?: "" }
                else                      -> { phoneCheckStatus = "ok";        phoneCheckName = result.driver_name ?: "" }
            }
        } catch (e: Exception) {
            phoneCheckStatus = ""   // silently ignore network errors during check
        } finally {
            isCheckingPhone = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.White)
            .verticalScroll(rememberScrollState()).padding(AppDimensions.XLarge),
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = RouteTrackColors.DarkGreen)
        }
        AppLogo(size = AppDimensions.LogoMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(AppDimensions.Large))
        Text("Create Account", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("Join as a ${if (isDriver) "Driver" else "Student"}",
            fontSize = 14.sp, color = RouteTrackColors.TextGray,
            modifier = Modifier.align(Alignment.CenterHorizontally))

        // ── Driver info banner ──────────────────────────────────────────────
        if (isDriver) {
            Spacer(Modifier.height(AppDimensions.Medium))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                shape  = RoundedCornerShape(AppDimensions.CornerMedium),
                border = BorderStroke(1.dp, RouteTrackColors.Warning),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(AppDimensions.Medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = RouteTrackColors.Warning, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(AppDimensions.Small))
                    Text(
                        "Your phone number must be registered by the admin before you can create an account.",
                        fontSize = 12.sp, color = Color(0xFF856404)
                    )
                }
            }
        }

        Spacer(Modifier.height(AppDimensions.XLarge))

        // ── Phone field (drivers) / email field (students) ──────────────────
        if (isDriver) {
            // Phone number field — required and validated for drivers
            OutlinedTextField(
                value       = phone,
                onValueChange = { phone = it; errorMessage = "" },
                label       = { Text("Registered Phone Number *") },
                leadingIcon = { Icon(Icons.Default.Phone, null, tint = RouteTrackColors.PrimaryGreen) },
                trailingIcon = when {
                    isCheckingPhone              -> { { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = RouteTrackColors.TextGray) } }
                    phoneCheckStatus == "ok"     -> { { Icon(Icons.Default.CheckCircle, null, tint = RouteTrackColors.Success) } }
                    phoneCheckStatus == "not_found" || phoneCheckStatus == "has_account" -> { { Icon(Icons.Default.Cancel, null, tint = RouteTrackColors.Error) } }
                    else -> null
                },
                isError    = phoneCheckStatus == "not_found" || phoneCheckStatus == "has_account",
                supportingText = when (phoneCheckStatus) {
                    "ok"          -> { { Text("✓ Verified: ${phoneCheckName}", color = RouteTrackColors.Success, fontSize = 12.sp) } }
                    "not_found"   -> { { Text("✗ Not registered. Contact admin.", color = RouteTrackColors.Error, fontSize = 12.sp) } }
                    "has_account" -> { { Text("✗ Account already exists for this number. Please login.", color = RouteTrackColors.Error, fontSize = 12.sp) } }
                    else -> null
                },
                modifier   = Modifier.fillMaxWidth(),
                shape      = RoundedCornerShape(AppDimensions.CornerMedium),
                singleLine = true,
                colors     = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = RouteTrackColors.PrimaryGreen,
                    unfocusedBorderColor = RouteTrackColors.Border
                )
            )
            Spacer(Modifier.height(AppDimensions.Medium))
        }

        ModernTextField(username, { username = it; errorMessage = "" }, "Full Name", Icons.Default.Person)
        Spacer(Modifier.height(AppDimensions.Medium))

        if (!isDriver) {
            // Students must use their college email
            ModernTextField(email, { email = it; errorMessage = "" }, "College Email Address", Icons.Default.Email)
            Spacer(Modifier.height(AppDimensions.Medium))
        } else {
            // Drivers: email is optional
            ModernTextField(email, { email = it; errorMessage = "" }, "Email Address (optional)", Icons.Default.Email)
            Spacer(Modifier.height(AppDimensions.Medium))
        }

        ModernTextField(
            password, { password = it; errorMessage = "" }, "Password", Icons.Default.Lock,
            isPassword = true, isPasswordVisible = passwordVisible,
            onPasswordVisibilityToggle = { passwordVisible = !passwordVisible }
        )
        Spacer(Modifier.height(AppDimensions.Medium))
        ModernTextField(
            confirmPassword, { confirmPassword = it; errorMessage = "" },
            "Confirm Password", Icons.Default.Lock,
            isPassword = true, isPasswordVisible = passwordVisible,
            onPasswordVisibilityToggle = { passwordVisible = !passwordVisible }
        )
        if (errorMessage.isNotEmpty()) {
            Text(errorMessage, color = RouteTrackColors.Error, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(AppDimensions.XXLarge))

        val canSubmit = username.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty() &&
                !isLoading &&
                (!isDriver || phoneCheckStatus == "ok") &&
                (isDriver || email.isNotEmpty())

        ModernButton(
            if (isLoading) "Creating Account..." else "Sign Up",
            onClick = {
                when {
                    isDriver && phoneCheckStatus != "ok" ->
                        errorMessage = "Please enter your registered phone number"
                    password != confirmPassword ->
                        errorMessage = "Passwords do not match"
                    password.length < 6 ->
                        errorMessage = "Password must be at least 6 characters"
                    else -> {
                        isLoading = true
                        // Use verified driver name as fallback username hint
                        val finalEmail = if (isDriver && email.isBlank()) "$username@driver.routetrack" else email
                        onSignUp(username, finalEmail, password, phone)
                        isLoading = false
                    }
                }
            },
            enabled = canSubmit
        )
        Spacer(Modifier.height(AppDimensions.Medium))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Already have an account? ", fontSize = 12.sp, color = RouteTrackColors.TextGray)
            Text("Login", fontSize = 12.sp, color = RouteTrackColors.PrimaryGreen,
                fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onBack() })
        }
    }
}

@Composable
fun ForgotPasswordScreen(onReset: (String) -> Boolean, onBack: () -> Unit) {
    var email     by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(AppDimensions.XLarge),
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = RouteTrackColors.DarkGreen)
        }
        AppLogo(size = AppDimensions.LogoMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(AppDimensions.XXLarge))
        Text("Forgot Password", fontSize = 28.sp, fontWeight = FontWeight.Bold,
            color = RouteTrackColors.DarkGreen, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("Enter your email to receive reset instructions",
            fontSize = 14.sp, color = RouteTrackColors.TextGray,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(AppDimensions.XXLarge))
        if (!isSuccess) {
            ModernTextField(email, { email = it }, "Email Address", Icons.Default.Email)
            Spacer(Modifier.height(AppDimensions.XLarge))
            ModernButton(
                if (isLoading) "Sending..." else "Send Reset Link",
                onClick = { isLoading = true; onReset(email); isLoading = false; isSuccess = true },
                enabled = email.isNotEmpty() && !isLoading
            )
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = RouteTrackColors.LightGray),
                modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AppDimensions.Large), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, tint = RouteTrackColors.Success, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(AppDimensions.Medium))
                    Text("Reset instructions sent!", fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen)
                }
            }
            Spacer(Modifier.height(AppDimensions.XLarge))
            ModernButton("Back to Login", onClick = onBack)
        }
    }
}

// ============= STUDENT HOME — shows driver phone number in bus cards =============

@Composable
fun StudentHomeScreen(username: String, onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val context          = LocalContext.current
    var stopName         by remember { mutableStateOf("") }
    var busNumberSearch  by remember { mutableStateOf("") }
    var selectedNavIndex by remember { mutableIntStateOf(0) }
    var busDetails       by remember { mutableStateOf<List<BusDetail>>(emptyList()) }
    var filteredBuses    by remember { mutableStateOf<List<BusDetail>>(emptyList()) }
    var isLoading        by remember { mutableStateOf(true) }
    var errorMsg         by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val response = RetrofitClient.instance.getBuses()
            if (response.success) { busDetails = response.buses; filteredBuses = response.buses }
            else errorMsg = "Failed to load buses"
        } catch (e: Exception) {
            Log.e("Buses", "error", e); errorMsg = "Connection error: ${e.message}"
        } finally { isLoading = false }
    }

    val navItems = listOf(
        BottomNavItem("Home", Icons.Default.Home),
        BottomNavItem("Location", Icons.Default.LocationOn),
        BottomNavItem("Updates", Icons.Default.Info),
        BottomNavItem("Profile", Icons.Default.Person)
    )

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        AppTopBar(title = "RouteTrack", subtitle = "Student", onBackClick = onBack, actions = {
            IconButton(onClick = {}) {
                Icon(Icons.Default.Notifications, null, tint = RouteTrackColors.PrimaryGreen)
            }
        })
        Column(modifier = Modifier.weight(1f).padding(AppDimensions.Large).verticalScroll(rememberScrollState())) {
            Text("Hi, ${username.replaceFirstChar { it.uppercase() }}!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen)
            Spacer(Modifier.height(AppDimensions.Small))
            Text("Find and track your bus in real-time", fontSize = 13.sp, color = RouteTrackColors.TextGray)
            Spacer(Modifier.height(AppDimensions.XXLarge))
            Text("Find Your Bus", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RouteTrackColors.DarkGreen)
            Spacer(Modifier.height(AppDimensions.Medium))
            ModernTextField(stopName, { stopName = it }, "Enter Stop", Icons.Default.Place)
            Spacer(Modifier.height(AppDimensions.Medium))
            Text("OR", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold, color = RouteTrackColors.TextGray, fontSize = 12.sp)
            Spacer(Modifier.height(AppDimensions.Medium))
            ModernTextField(busNumberSearch, { busNumberSearch = it }, "Search by Bus Number", Icons.Default.DirectionsCar)
            Spacer(Modifier.height(AppDimensions.XLarge))
            ModernButton("Search", onClick = {
                filteredBuses = busDetails.filter {
                    (stopName.isEmpty() || it.displayRouteName.contains(stopName, ignoreCase = true)) &&
                            (busNumberSearch.isEmpty() || it.bus_number.contains(busNumberSearch, ignoreCase = true))
                }
            }, icon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(AppDimensions.IconSize)) })
            Spacer(Modifier.height(AppDimensions.XXLarge))
            Text("Live Bus Tracking", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RouteTrackColors.DarkGreen)
            Spacer(Modifier.height(AppDimensions.Medium))
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = RouteTrackColors.PrimaryGreen)
                errorMsg.isNotEmpty() -> Text(errorMsg, fontSize = 14.sp, color = RouteTrackColors.Error)
                filteredBuses.isEmpty() -> Text("No active trips found.", fontSize = 14.sp, color = RouteTrackColors.TextGray)
                else -> filteredBuses.forEach { bus ->
                    ModernCard {
                        // ── Header row ──────────────────────────────────────
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(24.dp), tint = RouteTrackColors.PrimaryGreen)
                            Spacer(Modifier.width(AppDimensions.Medium))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bus.displayRouteName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen)
                                Text(bus.displayDescription, fontSize = 12.sp, color = RouteTrackColors.TextGray)
                            }
                            StatusBadge(bus.status, when (bus.statusColor) { "Success" -> RouteTrackColors.Success; "Error" -> RouteTrackColors.Error; else -> RouteTrackColors.Warning })
                        }

                        Spacer(Modifier.height(AppDimensions.Medium))
                        HorizontalDivider(color = RouteTrackColors.Border, thickness = 1.dp)
                        Spacer(Modifier.height(AppDimensions.Medium))

                        // ── Driver info row (name + phone) ──────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp), tint = RouteTrackColors.TextGray)
                            Spacer(Modifier.width(AppDimensions.Small))
                            Text(
                                "Driver: ${bus.driver_name}",
                                fontSize = 13.sp,
                                color = RouteTrackColors.DarkGreen,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            // Tap phone number to call
                            if (bus.displayDriverPhone.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable {
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_DIAL,
                                                "tel:${bus.displayDriverPhone}".toUri()
                                            )
                                            context.startActivity(intent)
                                        }
                                        .background(
                                            RouteTrackColors.PrimaryGreen.copy(alpha = 0.1f),
                                            RoundedCornerShape(AppDimensions.CornerSmall)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Call, null, modifier = Modifier.size(14.dp), tint = RouteTrackColors.PrimaryGreen)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        bus.displayDriverPhone,
                                        fontSize = 12.sp,
                                        color = RouteTrackColors.PrimaryGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(AppDimensions.Medium))

                        // ── Scheduled / Expected ────────────────────────────
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Scheduled", fontSize = 11.sp, color = RouteTrackColors.TextGray)
                                Text(bus.displayScheduled, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Expected", fontSize = 11.sp, color = RouteTrackColors.TextGray)
                                Text(bus.displayExpected, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen)
                            }
                        }
                        Spacer(Modifier.height(AppDimensions.Medium))

                        // ── Bottom action row ───────────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth().background(RouteTrackColors.LightGray, RoundedCornerShape(8.dp)).padding(AppDimensions.Medium),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DirectionsCar, null, modifier = Modifier.size(16.dp), tint = RouteTrackColors.PrimaryGreen)
                                Spacer(Modifier.width(AppDimensions.Small))
                                Text("Bus No.: ${bus.bus_number}", fontSize = 12.sp, color = RouteTrackColors.TextGray, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = { (context as? MainActivity)?.showBusNotification("Bus ${bus.bus_number} is 2 minutes away!") },
                                modifier = Modifier.wrapContentWidth().height(32.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RouteTrackColors.PrimaryGreen),
                                shape = RoundedCornerShape(AppDimensions.CornerSmall),
                                contentPadding = PaddingValues(8.dp)
                            ) {
                                Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(AppDimensions.Small))
                                Text("Notify", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(AppDimensions.Medium))
                }
            }
        }
        AppBottomNav(navItems, selectedNavIndex) { i ->
            selectedNavIndex = i
            when (i) { 0 -> onNavigate("studentHome"); 1 -> onNavigate("liveLocation"); 2 -> onNavigate("tripUpdate"); 3 -> onNavigate("profile") }
        }
    }
}

// ============= LIVE LOCATION (STUDENT) =============

@Composable
fun LiveLocationScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    var selectedNavIndex by remember { mutableIntStateOf(1) }
    val navItems = listOf(
        BottomNavItem("Home", Icons.Default.Home),
        BottomNavItem("Location", Icons.Default.LocationOn),
        BottomNavItem("Updates", Icons.Default.Info),
        BottomNavItem("Profile", Icons.Default.Person)
    )
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var studentLat     by remember { mutableStateOf<Double?>(null) }
    var studentLon     by remember { mutableStateOf<Double?>(null) }
    var locationStatus by remember { mutableStateOf("Acquiring GPS...") }

    var buses          by remember { mutableStateOf<List<BusDetail>>(emptyList()) }
    var isLoadingBuses by remember { mutableStateOf(true) }
    var errorMsg       by remember { mutableStateOf("") }
    var selectedBus    by remember { mutableStateOf<BusDetail?>(null) }

    var showMap        by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val response = RetrofitClient.instance.getBuses()
            if (response.success) {
                buses = response.buses
                if (response.buses.isNotEmpty()) selectedBus = response.buses.first()
            } else {
                errorMsg = "Failed to load buses"
            }
        } catch (e: Exception) {
            Log.e("LiveLocation", "Bus fetch error", e)
            errorMsg = "Connection error: ${e.message}"
        } finally {
            isLoadingBuses = false
        }
    }

    DisposableEffect(Unit) {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE)
                as android.location.LocationManager
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(loc: android.location.Location) {
                studentLat = loc.latitude; studentLon = loc.longitude; locationStatus = "Live"
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
            override fun onProviderEnabled(p: String)  { locationStatus = "GPS Enabled" }
            override fun onProviderDisabled(p: String) { locationStatus = "GPS Disabled" }
        }
        try {
            val hasPerm = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 3000L, 1f, listener)
                if (lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER))
                    lm.requestLocationUpdates(android.location.LocationManager.NETWORK_PROVIDER, 3000L, 1f, listener)
                lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)?.let {
                    studentLat = it.latitude; studentLon = it.longitude; locationStatus = "Last Known"
                }
                if (studentLat == null)
                    lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)?.let {
                        studentLat = it.latitude; studentLon = it.longitude; locationStatus = "Last Known"
                    }
            } else { locationStatus = "Permission Denied" }
        } catch (e: SecurityException) { locationStatus = "Permission Denied" }
        onDispose { lm.removeUpdates(listener) }
    }

    if (showMap && studentLat != null && studentLon != null && selectedBus != null) {
        OSMMapScreen(
            studentLat = studentLat!!,
            studentLon = studentLon!!,
            busLat     = selectedBus!!.current_latitude.takeIf { it != 0.0 },
            busLon     = selectedBus!!.current_longitude.takeIf { it != 0.0 },
            busName    = selectedBus!!.displayRouteName,
            busEta     = selectedBus!!.displayExpected,
            busStatus  = selectedBus!!.status,
            onBack     = { showMap = false }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        AppTopBar(title = "Live Location", subtitle = "Real-time Tracking", onBackClick = onBack)
        Column(modifier = Modifier.weight(1f).padding(AppDimensions.Large).verticalScroll(rememberScrollState())) {

            ModernCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = RouteTrackColors.PrimaryGreen.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(AppDimensions.CornerSmall), modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.MyLocation, null, modifier = Modifier.padding(8.dp), tint = RouteTrackColors.PrimaryGreen)
                    }
                    Spacer(Modifier.width(AppDimensions.Medium))
                    Column {
                        Text("Your Location", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen)
                        Text(locationStatus, fontSize = 12.sp, color = when (locationStatus) {
                            "Live" -> RouteTrackColors.Success; "Last Known" -> RouteTrackColors.Warning; else -> RouteTrackColors.TextGray
                        })
                    }
                }
                if (studentLat != null && studentLon != null) {
                    Spacer(Modifier.height(AppDimensions.Medium))
                    HorizontalDivider(color = RouteTrackColors.Border, thickness = 1.dp)
                    Spacer(Modifier.height(AppDimensions.Medium))
                    InfoRow("Latitude",  "%.6f".format(studentLat))
                    Spacer(Modifier.height(AppDimensions.Small))
                    InfoRow("Longitude", "%.6f".format(studentLon))
                } else {
                    Spacer(Modifier.height(AppDimensions.Medium))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = RouteTrackColors.PrimaryGreen, strokeWidth = 2.dp)
                        Spacer(Modifier.width(AppDimensions.Small))
                        Text("Acquiring GPS signal…", fontSize = 13.sp, color = RouteTrackColors.TextGray)
                    }
                }
            }

            Spacer(Modifier.height(AppDimensions.Large))
            Text("Select Bus to Track", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RouteTrackColors.DarkGreen)
            Spacer(Modifier.height(AppDimensions.Medium))

            when {
                isLoadingBuses -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = RouteTrackColors.PrimaryGreen)
                errorMsg.isNotEmpty() -> Text(errorMsg, fontSize = 14.sp, color = RouteTrackColors.Error)
                buses.isEmpty() -> Text("No active buses found.", fontSize = 14.sp, color = RouteTrackColors.TextGray)
                else -> buses.forEach { bus ->
                    val isSelected = selectedBus?.bus_id == bus.bus_id
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = AppDimensions.XSmall).clickable { selectedBus = bus },
                        shape = RoundedCornerShape(AppDimensions.CornerLarge),
                        elevation = CardDefaults.cardElevation(if (isSelected) 6.dp else 2.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) RouteTrackColors.PrimaryGreen.copy(alpha = 0.08f) else Color.White),
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) RouteTrackColors.PrimaryGreen else RouteTrackColors.Border)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(AppDimensions.Large), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DirectionsBus, null, modifier = Modifier.size(28.dp), tint = if (isSelected) RouteTrackColors.PrimaryGreen else RouteTrackColors.TextGray)
                            Spacer(Modifier.width(AppDimensions.Medium))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bus ${bus.bus_number}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen)
                                Text(bus.displayRouteName, fontSize = 12.sp, color = RouteTrackColors.TextGray)
                            }
                            StatusBadge(bus.status, when (bus.statusColor) { "Success" -> RouteTrackColors.Success; "Error" -> RouteTrackColors.Error; else -> RouteTrackColors.Warning })
                        }
                    }
                    Spacer(Modifier.height(AppDimensions.Small))
                }
            }

            selectedBus?.let { bus ->
                Spacer(Modifier.height(AppDimensions.Large))
                Text("Bus Details", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RouteTrackColors.DarkGreen)
                Spacer(Modifier.height(AppDimensions.Medium))
                ModernCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Bus ${bus.bus_number}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen)
                        StatusBadge(bus.status, when (bus.statusColor) { "Success" -> RouteTrackColors.Success; "Error" -> RouteTrackColors.Error; else -> RouteTrackColors.Warning })
                    }
                    Spacer(Modifier.height(AppDimensions.Medium))
                    HorizontalDivider(color = RouteTrackColors.Border, thickness = 1.dp)
                    Spacer(Modifier.height(AppDimensions.Medium))
                    InfoRow("Route", bus.displayRouteName)
                    Spacer(Modifier.height(AppDimensions.Small))
                    InfoRow("Driver", bus.driver_name)
                    // ── Driver phone in detail card ──────────────────────────
                    if (bus.displayDriverPhone.isNotEmpty()) {
                        Spacer(Modifier.height(AppDimensions.Small))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(AppDimensions.XSmall),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Driver Phone", fontSize = 13.sp, color = RouteTrackColors.TextGray, fontWeight = FontWeight.SemiBold)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_DIAL,
                                            "tel:${bus.displayDriverPhone}".toUri()
                                        )
                                        context.startActivity(intent)
                                    }
                                    .background(RouteTrackColors.PrimaryGreen.copy(alpha = 0.1f), RoundedCornerShape(AppDimensions.CornerSmall))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Call, null, modifier = Modifier.size(14.dp), tint = RouteTrackColors.PrimaryGreen)
                                Spacer(Modifier.width(4.dp))
                                Text(bus.displayDriverPhone, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.PrimaryGreen)
                            }
                        }
                    }
                    Spacer(Modifier.height(AppDimensions.Small))
                    InfoRow("Scheduled", bus.displayScheduled)
                    Spacer(Modifier.height(AppDimensions.Small))
                    InfoRow("Expected", bus.displayExpected)
                    Spacer(Modifier.height(AppDimensions.Small))
                    InfoRow("Delay", if (bus.delay_minutes == 0) "On Time" else "${bus.delay_minutes} min${if (bus.delay_minutes > 1) "s" else ""} late",
                        if (bus.delay_minutes == 0) RouteTrackColors.Success else RouteTrackColors.Error)
                    if (bus.current_latitude != 0.0 && bus.current_longitude != 0.0) {
                        Spacer(Modifier.height(AppDimensions.Small))
                        HorizontalDivider(color = RouteTrackColors.Border, thickness = 1.dp)
                        Spacer(Modifier.height(AppDimensions.Small))
                        InfoRow("Bus Lat", "%.6f".format(bus.current_latitude))
                        Spacer(Modifier.height(AppDimensions.Small))
                        InfoRow("Bus Lon", "%.6f".format(bus.current_longitude))
                    }
                    Spacer(Modifier.height(AppDimensions.Large))
                    ModernButton("View on Map", onClick = {
                        if (studentLat != null && studentLon != null) showMap = true
                        else Toast.makeText(context, "Waiting for your GPS location…", Toast.LENGTH_SHORT).show()
                    }, icon = { Icon(Icons.Default.Map, null, modifier = Modifier.size(AppDimensions.IconSize)) })
                    if (bus.current_latitude == 0.0 || bus.current_longitude == 0.0) {
                        Spacer(Modifier.height(AppDimensions.Small))
                        Text("⚠ Bus location not yet available — driver may not be broadcasting", fontSize = 11.sp, color = RouteTrackColors.Warning, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(AppDimensions.Large))
        }
        AppBottomNav(navItems, selectedNavIndex) { i ->
            selectedNavIndex = i
            when (i) { 0 -> onNavigate("studentHome"); 1 -> onNavigate("liveLocation"); 2 -> onNavigate("tripUpdate"); 3 -> onNavigate("profile") }
        }
    }
}

// ============= TRIP UPDATE (STUDENT) =============

@Composable
fun TripUpdateScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    var selectedNavIndex by remember { mutableIntStateOf(2) }
    val navItems = listOf(BottomNavItem("Home", Icons.Default.Home), BottomNavItem("Location", Icons.Default.LocationOn), BottomNavItem("Updates", Icons.Default.Info), BottomNavItem("Profile", Icons.Default.Person))
    var buses     by remember { mutableStateOf<List<BusDetail>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg  by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val response = RetrofitClient.instance.getBuses()
            if (response.success) buses = response.buses else errorMsg = "Failed to load trips"
        } catch (e: Exception) { errorMsg = "Connection error: ${e.message}" }
        finally { isLoading = false }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        AppTopBar(title = "Trip Updates", subtitle = "Real-time Information", onBackClick = onBack)
        Column(modifier = Modifier.weight(1f).padding(AppDimensions.Large).verticalScroll(rememberScrollState())) {
            Text("Active Trips", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RouteTrackColors.DarkGreen)
            Spacer(Modifier.height(AppDimensions.Medium))
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = RouteTrackColors.PrimaryGreen)
                errorMsg.isNotEmpty() -> Text(errorMsg, fontSize = 14.sp, color = RouteTrackColors.Error)
                buses.isEmpty() -> Text("No active trips found.", fontSize = 14.sp, color = RouteTrackColors.TextGray)
                else -> buses.forEach { bus ->
                    ModernCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bus ${bus.bus_number}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen)
                                Text("Route: ${bus.displayRouteName}", fontSize = 12.sp, color = RouteTrackColors.TextGray)
                                if (bus.displayDriverPhone.isNotEmpty()) {
                                    Text("Driver: ${bus.driver_name} · ${bus.displayDriverPhone}", fontSize = 12.sp, color = RouteTrackColors.TextGray)
                                }
                            }
                            StatusBadge(bus.status, when (bus.statusColor) { "Success" -> RouteTrackColors.Success; "Error" -> RouteTrackColors.Error; else -> RouteTrackColors.Warning })
                        }
                        Spacer(Modifier.height(AppDimensions.Medium))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("Scheduled", fontSize = 11.sp, color = RouteTrackColors.TextGray); Text(bus.displayScheduled, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen) }
                            Column(horizontalAlignment = Alignment.End) { Text("Expected", fontSize = 11.sp, color = RouteTrackColors.TextGray); Text(bus.displayExpected, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen) }
                        }
                    }
                    Spacer(Modifier.height(AppDimensions.Medium))
                }
            }
        }
        AppBottomNav(navItems, selectedNavIndex) { i ->
            selectedNavIndex = i
            when (i) { 0 -> onNavigate("studentHome"); 1 -> onNavigate("liveLocation"); 2 -> onNavigate("tripUpdate"); 3 -> onNavigate("profile") }
        }
    }
}

// ============= PROFILE =============

@Composable
fun ProfileScreen(username: String, email: String, role: String, onNavigate: (String) -> Unit, onLogout: () -> Unit, onBack: () -> Unit) {
    var selectedNavIndex by remember { mutableIntStateOf(3) }
    val navItems = listOf(BottomNavItem("Home", Icons.Default.Home), BottomNavItem("Location", Icons.Default.LocationOn), BottomNavItem("Updates", Icons.Default.Info), BottomNavItem("Profile", Icons.Default.Person))
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        AppTopBar(title = "Profile", subtitle = "Account Settings", onBackClick = onBack)
        Column(modifier = Modifier.weight(1f).padding(AppDimensions.XLarge).verticalScroll(rememberScrollState())) {
            Surface(color = RouteTrackColors.LightGray, shape = RoundedCornerShape(AppDimensions.CornerMedium),
                modifier = Modifier.size(120.dp).align(Alignment.CenterHorizontally)) {
                Icon(Icons.Default.Person, null, modifier = Modifier.fillMaxSize().padding(AppDimensions.XLarge), tint = RouteTrackColors.PrimaryGreen)
            }
            Spacer(Modifier.height(AppDimensions.XLarge))
            Text(username.replaceFirstChar { it.uppercase() }, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = RouteTrackColors.DarkGreen, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(if (role == "student") "Student Account" else "Driver Account", fontSize = 13.sp,
                color = RouteTrackColors.TextGray, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(AppDimensions.XXLarge))
            ModernCard {
                InfoRow("Name", username)
                HorizontalDivider(color = RouteTrackColors.Border, thickness = 1.dp, modifier = Modifier.padding(vertical = AppDimensions.Medium))
                InfoRow("Email", if (email.isNotEmpty()) email else "Not Provided")
                HorizontalDivider(color = RouteTrackColors.Border, thickness = 1.dp, modifier = Modifier.padding(vertical = AppDimensions.Medium))
                InfoRow("Role", if (role == "student") "Student" else "Driver")
            }
            Spacer(Modifier.height(AppDimensions.XXLarge))
            ModernButton("Logout", onLogout, backgroundColor = RouteTrackColors.Error,
                icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(AppDimensions.IconSize)) })
        }
        AppBottomNav(navItems, selectedNavIndex) { i ->
            selectedNavIndex = i
            when (i) {
                0 -> onNavigate(if (role == "student") "studentHome" else "driverHome")
                1 -> onNavigate(if (role == "student") "liveLocation" else "driverLocation")
                2 -> onNavigate(if (role == "student") "tripUpdate" else "driverUpdates")
                3 -> onNavigate("profile")
            }
        }
    }
}

// ============= BUS DETAILS =============

@Composable
fun BusDetailsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        AppTopBar(title = "Bus Details", subtitle = "Detailed Information", onBackClick = onBack)
        Column(modifier = Modifier.fillMaxSize().padding(AppDimensions.Large).verticalScroll(rememberScrollState())) {
            ModernCard {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("RouteTrack 1", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen)
                    Spacer(Modifier.height(AppDimensions.Small))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp), tint = RouteTrackColors.PrimaryGreen)
                        Spacer(Modifier.width(AppDimensions.Small))
                        Text("Campus to City Center", fontSize = 13.sp, color = RouteTrackColors.TextGray, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(AppDimensions.Medium)); StatusBadge("10 mins late")
                }
            }
            Spacer(Modifier.height(AppDimensions.XLarge))
            ModernCard {
                InfoRow("Bus Number", "KL15AB1234"); Spacer(Modifier.height(AppDimensions.Medium)); HorizontalDivider(color = RouteTrackColors.Border)
                Spacer(Modifier.height(AppDimensions.Medium)); InfoRow("Scheduled Time", "08:28 AM"); Spacer(Modifier.height(AppDimensions.Medium)); HorizontalDivider(color = RouteTrackColors.Border)
                Spacer(Modifier.height(AppDimensions.Medium)); InfoRow("Driver Name", "Anil Kumar"); Spacer(Modifier.height(AppDimensions.Medium)); HorizontalDivider(color = RouteTrackColors.Border)
                Spacer(Modifier.height(AppDimensions.Medium)); InfoRow("Driver Phone", "9876543210")
            }
            Spacer(Modifier.height(AppDimensions.XLarge))
            ModernButton("Call Driver", onClick = { val i = android.content.Intent(android.content.Intent.ACTION_DIAL); i.data = "tel:9876543210".toUri(); context.startActivity(i) },
                icon = { Icon(Icons.Default.Call, null, modifier = Modifier.size(AppDimensions.IconSize)) })
        }
    }
}

// ============= DRIVER HOME =============

@Composable
fun DriverHomeScreen(
    username: String,
    isBroadcasting: Boolean,
    tripStartTime: String,
    onLogout: () -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    var selectedNavIndex by remember { mutableIntStateOf(0) }
    val navItems = listOf(BottomNavItem("Home", Icons.Default.Home), BottomNavItem("Location", Icons.Default.LocationOn), BottomNavItem("Updates", Icons.Default.Info), BottomNavItem("Profile", Icons.Default.Person))

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        AppTopBar(title = "Driver Dashboard", subtitle = "Welcome ${username.replaceFirstChar { it.uppercase() }}", onBackClick = onBack)
        Column(modifier = Modifier.weight(1f).padding(AppDimensions.Large).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.Medium)) {

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppDimensions.CornerLarge),
                colors = CardDefaults.cardColors(containerColor = if (isBroadcasting) RouteTrackColors.PrimaryGreen else RouteTrackColors.LightGray),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(modifier = Modifier.padding(AppDimensions.XLarge)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp).background(if (isBroadcasting) Color.White else RouteTrackColors.TextGray, RoundedCornerShape(50)))
                        Spacer(Modifier.width(AppDimensions.Small))
                        Text(if (isBroadcasting) "Broadcasting Live" else "Not Broadcasting", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isBroadcasting) Color.White else RouteTrackColors.DarkGreen)
                    }
                    if (isBroadcasting && tripStartTime.isNotEmpty()) {
                        Spacer(Modifier.height(AppDimensions.Small))
                        Text("Trip started at $tripStartTime", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                    } else if (!isBroadcasting) {
                        Spacer(Modifier.height(AppDimensions.Small))
                        Text("Go to Location tab to start broadcasting", fontSize = 13.sp, color = RouteTrackColors.TextGray)
                    }
                }
            }

            ModernCard {
                Text("Broadcast Your Location", fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen)
                Spacer(Modifier.height(AppDimensions.Small))
                Text("Students can see your bus in real-time", fontSize = 12.sp, color = RouteTrackColors.TextGray)
                Spacer(Modifier.height(10.dp))
                ModernButton(
                    if (isBroadcasting) "Manage Broadcasting" else "Start Broadcasting",
                    onClick = { onNavigate("driverLocation") },
                    backgroundColor = if (isBroadcasting) RouteTrackColors.PrimaryGreen else RouteTrackColors.DarkGreen,
                    icon = { Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(AppDimensions.IconSize)) }
                )
            }

            ModernButton("Logout", onLogout, backgroundColor = RouteTrackColors.Error,
                icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) })
        }
        AppBottomNav(navItems, selectedNavIndex) { i ->
            selectedNavIndex = i
            when (i) { 0 -> onNavigate("driverHome"); 1 -> onNavigate("driverLocation"); 2 -> onNavigate("driverUpdates"); 3 -> onNavigate("profile") }
        }
    }
}

// ============= DRIVER LOCATION =============

@Composable
fun DriverLocationScreen(
    tripActive: Boolean,
    tripStartTime: String,
    isBroadcasting: Boolean,
    broadcastBusNumber: String,
    onTripChanged: (Boolean, String, String, String) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    var selectedNavIndex by remember { mutableIntStateOf(1) }
    val navItems = listOf(BottomNavItem("Home", Icons.Default.Home), BottomNavItem("Location", Icons.Default.LocationOn), BottomNavItem("Updates", Icons.Default.Info), BottomNavItem("Profile", Icons.Default.Person))
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var latitude       by remember { mutableStateOf<Double?>(null) }
    var longitude      by remember { mutableStateOf<Double?>(null) }
    var accuracy       by remember { mutableStateOf<Float?>(null) }
    var speed          by remember { mutableStateOf<Float?>(null) }
    var locationStatus by remember { mutableStateOf("Acquiring GPS...") }
    var isLocating     by remember { mutableStateOf(true) }
    var currentTime    by remember { mutableStateOf("") }
    var lastUpdated    by remember { mutableStateOf("Never") }

    var busNumber      by remember { mutableStateOf(broadcastBusNumber) }
    var busNumberError by remember { mutableStateOf("") }

    fun getCurrentTimeString(): String {
        val cal  = java.util.Calendar.getInstance()
        val h    = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val m    = cal.get(java.util.Calendar.MINUTE)
        val s    = cal.get(java.util.Calendar.SECOND)
        val ampm = if (h < 12) "AM" else "PM"
        val hh   = if (h % 12 == 0) 12 else h % 12
        return "%02d:%02d:%02d %s".format(hh, m, s, ampm)
    }

    LaunchedEffect(Unit) { while (true) { currentTime = getCurrentTimeString(); kotlinx.coroutines.delay(1000) } }

    DisposableEffect(Unit) {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(loc: android.location.Location) {
                latitude = loc.latitude; longitude = loc.longitude; accuracy = loc.accuracy
                speed = if (loc.hasSpeed()) loc.speed * 3.6f else null
                locationStatus = "Live"; isLocating = false
                if (isBroadcasting && busNumber.isNotBlank()) {
                    scope.launch {
                        try {
                            RetrofitClient.instance.updateDriverLocation(LocationUpdateRequest(bus_number = busNumber.trim().uppercase(), latitude = loc.latitude, longitude = loc.longitude, is_trip_active = true))
                        } catch (_: Exception) {}
                    }
                    lastUpdated = getCurrentTimeString()
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
            override fun onProviderEnabled(p: String)  { locationStatus = "GPS Enabled" }
            override fun onProviderDisabled(p: String) { locationStatus = "GPS Disabled" }
        }
        try {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 2000L, 1f, listener)
                if (lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER))
                    lm.requestLocationUpdates(android.location.LocationManager.NETWORK_PROVIDER, 2000L, 1f, listener)
                lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)?.let {
                    latitude = it.latitude; longitude = it.longitude; accuracy = it.accuracy; locationStatus = "Last Known"; isLocating = false
                }
            } else { locationStatus = "Permission Denied"; isLocating = false }
        } catch (e: SecurityException) { locationStatus = "Permission Denied"; isLocating = false }
        onDispose { lm.removeUpdates(listener) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        AppTopBar(title = "Live Location", subtitle = "Broadcast Your Position", onBackClick = onBack)
        Column(modifier = Modifier.weight(1f).padding(AppDimensions.Large).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (isBroadcasting) RouteTrackColors.PrimaryGreen else RouteTrackColors.DarkGreen), shape = RoundedCornerShape(AppDimensions.CornerLarge)) {
                Column(modifier = Modifier.padding(AppDimensions.XLarge), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Current Time", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    Text(currentTime, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Surface(color = if (isBroadcasting) Color.White.copy(alpha = 0.2f) else RouteTrackColors.Warning.copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isBroadcasting) Box(modifier = Modifier.size(8.dp).background(RouteTrackColors.Success, RoundedCornerShape(50)))
                            if (isBroadcasting) Spacer(Modifier.width(6.dp))
                            Text(if (isBroadcasting) "BROADCASTING LIVE" else "NOT BROADCASTING", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (isBroadcasting) {
                        Spacer(Modifier.height(4.dp))
                        Text("Last sent: $lastUpdated", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                        if (tripStartTime.isNotEmpty()) Text("Started at $tripStartTime", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }

            Spacer(Modifier.height(AppDimensions.Large))
            ModernCard {
                Text("Your Bus Number", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen)
                Spacer(Modifier.height(AppDimensions.Medium))
                OutlinedTextField(
                    value = busNumber, onValueChange = { busNumber = it.uppercase(); busNumberError = "" },
                    label = { Text("Bus Number (e.g. KL15AB1234)") },
                    leadingIcon = { Icon(Icons.Default.DirectionsBus, null, tint = RouteTrackColors.PrimaryGreen) },
                    isError = busNumberError.isNotEmpty(),
                    supportingText = if (busNumberError.isNotEmpty()) { { Text(busNumberError, color = RouteTrackColors.Error) } } else null,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(AppDimensions.CornerMedium),
                    singleLine = true, enabled = !isBroadcasting,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RouteTrackColors.PrimaryGreen, unfocusedBorderColor = RouteTrackColors.Border)
                )
                if (busNumber.isNotBlank()) { Spacer(Modifier.height(AppDimensions.Small)); Text("Broadcasting for bus: ${busNumber.trim().uppercase()}", fontSize = 11.sp, color = RouteTrackColors.TextGray) }
            }

            Spacer(Modifier.height(AppDimensions.Large))
            ModernCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = RouteTrackColors.PrimaryGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(AppDimensions.CornerSmall), modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.GpsFixed, null, modifier = Modifier.padding(8.dp), tint = RouteTrackColors.PrimaryGreen)
                    }
                    Spacer(Modifier.width(AppDimensions.Medium))
                    Column {
                        Text("Current Position", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen)
                        Surface(color = when (locationStatus) { "Live" -> RouteTrackColors.Success; "Last Known" -> RouteTrackColors.Warning; else -> RouteTrackColors.Error }, shape = RoundedCornerShape(20.dp)) {
                            Text(locationStatus, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.height(AppDimensions.Medium)); HorizontalDivider(color = RouteTrackColors.Border, thickness = 1.dp); Spacer(Modifier.height(AppDimensions.Medium))
                if (latitude != null && longitude != null) {
                    InfoRow("Latitude", "%.6f".format(latitude))
                    Spacer(Modifier.height(AppDimensions.Small)); InfoRow("Longitude", "%.6f".format(longitude))
                    if (accuracy != null) { Spacer(Modifier.height(AppDimensions.Small)); InfoRow("Accuracy", "±%.1f m".format(accuracy), if ((accuracy ?: 99f) < 20f) RouteTrackColors.Success else RouteTrackColors.Warning) }
                    if (speed != null) { Spacer(Modifier.height(AppDimensions.Small)); InfoRow("Speed", "%.1f km/h".format(speed), RouteTrackColors.PrimaryGreen) }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        if (isLocating) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = RouteTrackColors.PrimaryGreen, strokeWidth = 2.dp)
                        Spacer(Modifier.width(AppDimensions.Medium))
                        Text("Acquiring GPS signal...", fontSize = 13.sp, color = RouteTrackColors.TextGray)
                    }
                }
            }

            Spacer(Modifier.height(AppDimensions.Large))
            Button(
                onClick = {
                    if (!isBroadcasting) {
                        if (busNumber.isBlank()) busNumberError = "Please enter your bus number first"
                        else { val startTime = getCurrentTimeString(); onTripChanged(true, startTime, "", busNumber.trim().uppercase()) }
                    } else {
                        val stopTime = getCurrentTimeString()
                        scope.launch {
                            try { latitude?.let { lat -> longitude?.let { lon -> RetrofitClient.instance.updateDriverLocation(LocationUpdateRequest(bus_number = busNumber.trim().uppercase(), latitude = lat, longitude = lon, is_trip_active = false)) } } } catch (_: Exception) {}
                        }
                        onTripChanged(false, tripStartTime, stopTime, busNumber.trim().uppercase())
                    }
                },
                modifier = Modifier.fillMaxWidth().height(AppDimensions.ButtonHeight),
                colors = ButtonDefaults.buttonColors(containerColor = if (isBroadcasting) RouteTrackColors.Error else RouteTrackColors.PrimaryGreen),
                shape = RoundedCornerShape(AppDimensions.CornerMedium),
                enabled = latitude != null || isBroadcasting
            ) {
                Icon(if (isBroadcasting) Icons.Default.Stop else Icons.Default.PlayArrow, null, modifier = Modifier.size(AppDimensions.IconSize))
                Spacer(Modifier.width(AppDimensions.Small))
                Text(if (isBroadcasting) "Stop Broadcasting" else "Start Broadcasting", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (latitude == null && !isBroadcasting) { Spacer(Modifier.height(AppDimensions.Small)); Text("Waiting for GPS fix before broadcasting...", fontSize = 11.sp, color = RouteTrackColors.TextGray, textAlign = TextAlign.Center) }
        }
        AppBottomNav(navItems, selectedNavIndex) { i ->
            selectedNavIndex = i
            when (i) { 0 -> onNavigate("driverHome"); 1 -> onNavigate("driverLocation"); 2 -> onNavigate("driverUpdates"); 3 -> onNavigate("profile") }
        }
    }
}

// ============= DRIVER UPDATES =============

@Composable
fun DriverUpdatesScreen(tripActive: Boolean, tripStartTime: String, tripStopTime: String, isBroadcasting: Boolean, onNavigate: (String) -> Unit, onBack: () -> Unit) {
    var selectedNavIndex by remember { mutableIntStateOf(2) }
    val navItems = listOf(BottomNavItem("Home", Icons.Default.Home), BottomNavItem("Location", Icons.Default.LocationOn), BottomNavItem("Updates", Icons.Default.Info), BottomNavItem("Profile", Icons.Default.Person))
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        AppTopBar(title = "Trip Updates", subtitle = "Journey Progress", onBackClick = onBack)
        Column(modifier = Modifier.weight(1f).padding(AppDimensions.Large).verticalScroll(rememberScrollState())) {
            Text("Current Trip", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RouteTrackColors.DarkGreen)
            Spacer(Modifier.height(AppDimensions.Medium))
            if (!tripActive && tripStartTime.isEmpty()) {
                ModernCard {
                    Column(modifier = Modifier.fillMaxWidth().padding(AppDimensions.XLarge), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DirectionsBus, null, tint = RouteTrackColors.TextGray, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(AppDimensions.Medium))
                        Text("No active trip", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen)
                        Spacer(Modifier.height(AppDimensions.Small))
                        Text("Go to the Location tab and tap 'Start Broadcasting'", fontSize = 12.sp, color = RouteTrackColors.TextGray, textAlign = TextAlign.Center)
                    }
                }
            } else {
                ModernCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Current Trip", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen)
                            if (tripStartTime.isNotEmpty()) Text("Started at $tripStartTime", fontSize = 12.sp, color = RouteTrackColors.TextGray)
                        }
                        StatusBadge(if (isBroadcasting) "LIVE" else "Stopped", if (isBroadcasting) RouteTrackColors.Success else RouteTrackColors.Error)
                    }
                    Spacer(Modifier.height(AppDimensions.Medium)); HorizontalDivider(color = RouteTrackColors.Border, thickness = 1.dp); Spacer(Modifier.height(AppDimensions.Medium))
                    Row(modifier = Modifier.fillMaxWidth().background(RouteTrackColors.LightGray, RoundedCornerShape(AppDimensions.CornerSmall)).padding(AppDimensions.Medium), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Broadcasting:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RouteTrackColors.TextGray)
                        Text(if (isBroadcasting) "Active — students can see you" else "Stopped", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isBroadcasting) RouteTrackColors.Success else RouteTrackColors.Error)
                    }
                }
                Spacer(Modifier.height(AppDimensions.XLarge))
                Text("Trip Timeline", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RouteTrackColors.DarkGreen)
                Spacer(Modifier.height(AppDimensions.Medium))
                ModernCard {
                    if (tripStartTime.isNotEmpty()) DriverEventRow(tripStartTime, "Broadcasting Started — students can now see your bus", Icons.Default.PlayArrow)
                    if (!isBroadcasting && tripStopTime.isNotEmpty()) {
                        Spacer(Modifier.height(AppDimensions.Medium)); HorizontalDivider(color = RouteTrackColors.Border, thickness = 1.dp); Spacer(Modifier.height(AppDimensions.Medium))
                        DriverEventRow(tripStopTime, "Broadcasting Stopped", Icons.Default.Stop)
                    }
                }
            }
        }
        AppBottomNav(navItems, selectedNavIndex) { i ->
            selectedNavIndex = i
            when (i) { 0 -> onNavigate("driverHome"); 1 -> onNavigate("driverLocation"); 2 -> onNavigate("driverUpdates"); 3 -> onNavigate("profile") }
        }
    }
}

@Composable
fun DriverEventRow(time: String, event: String, icon: ImageVector) {
    Row(modifier = Modifier.fillMaxWidth().padding(AppDimensions.XSmall), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = RouteTrackColors.LightGray, shape = RoundedCornerShape(AppDimensions.CornerSmall), modifier = Modifier.size(32.dp)) {
            Icon(icon, null, modifier = Modifier.fillMaxSize().padding(AppDimensions.Small), tint = RouteTrackColors.PrimaryGreen)
        }
        Spacer(Modifier.width(AppDimensions.Medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(event, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RouteTrackColors.DarkGreen)
            Text(time, fontSize = 10.sp, color = RouteTrackColors.TextGray)
        }
    }
}
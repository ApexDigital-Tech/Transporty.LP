package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainTransitApp()
            }
        }
    }
}

enum class NavigationMode {
    WELCOME, PASSENGER, DRIVER, SHIFT_DETAILS, ROUTE_DETAIL, ADMIN
}

@Composable
fun MainTransitApp() {
    var activeMode by remember { mutableStateOf(NavigationMode.WELCOME) }
    var selectedRouteCode by remember { mutableStateOf("201") }
    var registeredPhoneNumber by remember { mutableStateOf("") }
    
    // GPS Simulation states
    var busProgress by remember { mutableFloatStateOf(0.4f) }
    var pointsEarned by remember { mutableIntStateOf(15) }
    var accumulatedEarnings by remember { mutableIntStateOf(450) }
    var userRole by remember { mutableStateOf("") } // "pasajero", "chofer", "admin"

    // Context & Room Database Repository Setup
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { TransitDatabase.getDatabase(context) }
    val repository = remember { TransitRepository(database.driverDao(), database.shiftDao()) }

    // Seed data initially in a non-blocking context
    LaunchedEffect(Unit) {
        try {
            repository.seedSampleDataIfEmpty(scope)
        } catch (e: Exception) {
            android.util.Log.e("MainTransitApp", "Error executing seed: ${e.message}", e)
        }
    }

    // Set tenant and start sync when phone number is set
    LaunchedEffect(registeredPhoneNumber) {
        if (registeredPhoneNumber.isNotEmpty()) {
            try {
                repository.setTenant(registeredPhoneNumber)
                repository.syncFromSupabase()
                repository.subscribeToRealtime(scope)
            } catch (e: Exception) {
                android.util.Log.e("MainTransitApp", "Error during repository setTenant/sync: ${e.message}", e)
            }
        }
    }

    // Sync Driver's points/earnings with database when logging in or switching views
    LaunchedEffect(registeredPhoneNumber, activeMode, userRole) {
        if (registeredPhoneNumber.isNotEmpty() && userRole == "chofer") {
            try {
                val driver = repository.getDriver(registeredPhoneNumber)
                if (driver == null) {
                    // Register driver in local database
                    val newDriver = DriverEntity(
                        phone = registeredPhoneNumber,
                        name = "Chofer " + registeredPhoneNumber.takeLast(4),
                        licensePlate = "LPZ-" + (1000..9999).random(),
                        status = "En Ruta",
                        currentRoute = "201 AUTOPISTA",
                        earnings = 0,
                        points = 0
                    )
                    repository.createOrUpdateDriver(newDriver)
                    pointsEarned = 0
                    accumulatedEarnings = 0
                } else {
                    pointsEarned = driver.points
                    if (driver.earnings > 0) {
                        accumulatedEarnings = driver.earnings
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainTransitApp", "Error syncing driver profile: ${e.message}", e)
            }
        }
    }
    
    // Auto-progress animation for simulation representation
    LaunchedEffect(registeredPhoneNumber, activeMode, userRole) {
        while (true) {
            delay(3500)
            if (activeMode == NavigationMode.DRIVER) {
                busProgress = (busProgress + 0.05f)
                if (busProgress > 1f) busProgress = 0.1f
                
                // Randomly increase score for nice interactives
                if (Math.random() > 0.7) {
                    pointsEarned += 1
                    accumulatedEarnings += 5
                    
                    // Direct live SQLite database persistence update
                    if (registeredPhoneNumber.isNotEmpty() && userRole == "chofer") {
                        try {
                            repository.updateDriverEarnings(registeredPhoneNumber, accumulatedEarnings, pointsEarned)
                        } catch (e: Exception) {
                            android.util.Log.e("MainTransitApp", "Error auto-updating earnings: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        bottomBar = {
            if (activeMode != NavigationMode.WELCOME && activeMode != NavigationMode.ADMIN) {
                if (userRole == "chofer") {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 8.dp,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        NavigationBarItem(
                            selected = activeMode == NavigationMode.DRIVER,
                            onClick = { activeMode = NavigationMode.DRIVER },
                            icon = { Icon(Icons.Filled.DirectionsBus, contentDescription = "En Mapa") },
                            label = { Text("Mi Mapa", style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                        NavigationBarItem(
                            selected = activeMode == NavigationMode.SHIFT_DETAILS,
                            onClick = { activeMode = NavigationMode.SHIFT_DETAILS },
                            icon = { Icon(Icons.Filled.AccountBox, contentDescription = "Turno") },
                            label = { Text("Mi Turno", style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                } else {
                    // Pasajero navigation bar
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 8.dp,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        NavigationBarItem(
                            selected = activeMode == NavigationMode.PASSENGER || activeMode == NavigationMode.ROUTE_DETAIL,
                            onClick = { activeMode = NavigationMode.PASSENGER },
                            icon = { Icon(Icons.Filled.Search, contentDescription = "Rutas") },
                            label = { Text("Rutas", style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = activeMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "mode_transition"
            ) { currentMode ->
                when (currentMode) {
                    NavigationMode.WELCOME -> WelcomePortalScreen(
                        onLoginSuccess = { phone, role ->
                            registeredPhoneNumber = phone
                            if (phone == "78756107") {
                                userRole = "admin"
                                activeMode = NavigationMode.ADMIN
                            } else {
                                userRole = role
                                activeMode = if (role == "chofer") NavigationMode.DRIVER else NavigationMode.PASSENGER
                            }
                        }
                    )
                    NavigationMode.PASSENGER -> PassengerScreen(
                        registeredPhone = registeredPhoneNumber,
                        onRouteSelect = { code ->
                            selectedRouteCode = code
                            activeMode = NavigationMode.ROUTE_DETAIL
                        },
                        onSignOut = {
                            registeredPhoneNumber = ""
                            userRole = ""
                            activeMode = NavigationMode.WELCOME
                        }
                    )
                    NavigationMode.ROUTE_DETAIL -> RouteDetailScreen(
                        routeCode = selectedRouteCode,
                        busProgress = busProgress,
                        onBack = { activeMode = NavigationMode.PASSENGER }
                    )
                    NavigationMode.DRIVER -> DriverScreen(
                        registeredPhone = registeredPhoneNumber,
                        pointsEarned = pointsEarned,
                        accumulatedEarnings = accumulatedEarnings,
                        busProgress = busProgress,
                        onEndTurn = {
                            scope.launch {
                                repository.endShift(registeredPhoneNumber, accumulatedEarnings, pointsEarned)
                            }
                            activeMode = NavigationMode.SHIFT_DETAILS
                            AlertToast("Turno finalizado con éxito.")
                        },
                        onSignOut = {
                            registeredPhoneNumber = ""
                            userRole = ""
                            activeMode = NavigationMode.WELCOME
                        }
                    )
                    NavigationMode.SHIFT_DETAILS -> ShiftManagementScreen(
                        registeredPhone = registeredPhoneNumber,
                        accumulatedEarnings = accumulatedEarnings,
                        onStartDailyShift = {
                            scope.launch {
                                repository.startShift(registeredPhoneNumber, selectedRouteCode)
                            }
                            activeMode = NavigationMode.DRIVER
                        },
                        onSignOut = {
                            registeredPhoneNumber = ""
                            userRole = ""
                            activeMode = NavigationMode.WELCOME
                        }
                    )
                    NavigationMode.ADMIN -> AdminScreen(
                        repository = repository,
                        onSignOut = {
                            registeredPhoneNumber = ""
                            userRole = ""
                            activeMode = NavigationMode.WELCOME
                        }
                    )
                }
            }
        }
    }
}

// ---------------- PASSENGER SCREEN ----------------

@Composable
fun PassengerScreen(
    registeredPhone: String,
    onRouteSelect: (String) -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // High Contrast Blue TopBar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CobaltPrimary)
                .statusBarsPadding()
                .padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ExitToApp,
                            contentDescription = "Cerrar Sesión",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "LA PAZ TRANSIT",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Móvil: +591 $registeredPhone",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Topographic Map Segment
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f)
                .background(Color(0xFFE5E9EC))
        ) {
            CustomTransitMap(
                modifier = Modifier.fillMaxSize(),
                primaryRouteSelected = true,
                busProgress = 0.45f
            )

            // User Position Pulsing Blue Indicator
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
                    .background(CobaltPrimary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(CobaltPrimary, CircleShape)
                    )
                }
            }

            // Static floating card representing "Parada Cercana - Plaza Murillo"
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.93f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CobaltPrimary.copy(alpha = 0.1f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.LocationOn,
                                    contentDescription = "Location",
                                    tint = CobaltPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Parada Cercana",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Plaza Murillo",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Button(
                        onClick = { onRouteSelect("201") },
                        colors = ButtonDefaults.buttonColors(containerColor = CobaltPrimary),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Ir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Live arriving bus cards (Bottom sheet lookalike)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f)
                .background(Color.White)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Líneas Llegando",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(LimeSecondary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "En vivo",
                        style = MaterialTheme.typography.labelMedium,
                        color = LimeSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Card 1 (Active GPS stream)
            ArrivingBusCard(
                lineCode = "212 'CEJA'",
                destination = "16 JULIO FARELLÓN",
                description = "Vía Autopista • 1.2 km de distancia",
                eta = "~4 min",
                isGpsActive = true,
                badgeColor = CobaltPrimary,
                onClick = { onRouteSelect("212") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Card 2
            ArrivingBusCard(
                lineCode = "300 'SOPOCACHI'",
                destination = "6 DE AGOSTO",
                description = "Tráfico moderado en El Prado",
                eta = "~8 min",
                isGpsActive = false,
                badgeColor = MaterialTheme.colorScheme.outline,
                onClick = { onRouteSelect("300") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Suggestion Card (High Contrast Tonal)
            Card(
                colors = CardDefaults.cardColors(containerColor = GoldAlertContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "Sugerencia",
                        tint = GoldOnAlertContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Sugerencia de viaje",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = GoldOnAlertContainer
                        )
                        Text(
                            "Combina con Teleférico Celeste en San Jorge",
                            fontSize = 11.sp,
                            color = GoldOnAlertContainer.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Next",
                        tint = GoldOnAlertContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ArrivingBusCard(
    lineCode: String,
    destination: String,
    description: String,
    eta: String,
    isGpsActive: Boolean,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = lineCode,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (isGpsActive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Warning, // Simulates real-time sensors
                            contentDescription = "Sensors",
                            tint = LimeSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = eta,
                    color = CobaltPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = destination,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isGpsActive) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "GPS Active",
                        tint = LimeSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "GPS de minibús activo y transmitiendo",
                        style = MaterialTheme.typography.labelSmall,
                        color = LimeSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ---------------- ROUTE DETAILED SCREEN ----------------

@Composable
fun RouteDetailScreen(
    routeCode: String,
    busProgress: Float,
    onBack: () -> Unit
) {
    var isFollowing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // TopBar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CobaltPrimary)
                .statusBarsPadding()
                .padding(vertical = 12.dp, horizontal = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Ruta $routeCode - Detalles",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // Map preview with customizable overlays
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(Color(0xFFE5E9EC))
                ) {
                    CustomTransitMap(
                        modifier = Modifier.fillMaxSize(),
                        primaryRouteSelected = true,
                        busProgress = busProgress
                    )

                    // Overlay Map status
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(LimeSecondary, CircleShape)
                        )
                        Text(
                            "Minibús a 3 min",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Source flag banner
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "Origen",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "San Pedro",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CobaltPrimary
                            )
                        }
                    }
                }
            }

            // Route header information card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Surface(
                                    color = CobaltPrimary,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "LÍNEA $routeCode",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "SAN PEDRO - ACHUMANI",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = "Activo",
                                    tint = LimeSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    "ACTIVO",
                                    color = LimeSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Face,
                                contentDescription = "Schedule",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Frecuencia estimada cada ",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "10 min",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Route Stop-station timeline
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        "Paradas de la ruta",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    RouteTimelineStop(
                        stopName = "Plaza del Estudiante",
                        stopDesc = "Punto de partida principal",
                        timeOffset = "~2 min",
                        status = TimelineStatus.ACTIVE,
                        isFirst = true
                    )
                    RouteTimelineStop(
                        stopName = "Plaza Isabel la Católica",
                        stopDesc = "Zona central residencial",
                        timeOffset = "~6 min",
                        status = TimelineStatus.FUTURE
                    )
                    RouteTimelineStop(
                        stopName = "San Jorge",
                        stopDesc = "Conexión Teleférico Celeste",
                        timeOffset = "~10 min",
                        status = TimelineStatus.SPECIAL_CONNECTION
                    )
                    RouteTimelineStop(
                        stopName = "Obrajes Calle 17",
                        stopDesc = "Intercambiador Zona Sur",
                        timeOffset = "~18 min",
                        status = TimelineStatus.FUTURE
                    )
                    RouteTimelineStop(
                        stopName = "Calacoto Calle 15",
                        stopDesc = "Destino final Achumani",
                        timeOffset = "~25 min",
                        status = TimelineStatus.TERMINAL,
                        isLast = true
                    )
                }
            }

            // Floating control buttons
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isFollowing = !isFollowing },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowing) LimeSecondary else CobaltPrimary
                        ),
                        shape = RoundedCornerShape(27.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isFollowing) Icons.Filled.Done else Icons.Filled.Place,
                                contentDescription = "Seguir"
                            )
                            Text(
                                text = if (isFollowing) "Ruta en seguimiento activo" else "Seguir esta ruta",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { /* Check full schedule */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CobaltPrimary),
                        border = BorderStroke(1.dp, CobaltPrimary),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.DateRange, contentDescription = "Cal")
                            Text("Ver horarios completos", fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

enum class TimelineStatus {
    ACTIVE, FUTURE, SPECIAL_CONNECTION, TERMINAL
}

@Composable
fun RouteTimelineStop(
    stopName: String,
    stopDesc: String,
    timeOffset: String,
    status: TimelineStatus,
    isFirst: Boolean = false,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Vertical timeline bar segment
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Background connector line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            
            // Marker circle depending on status
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.White, CircleShape)
                    .border(
                        BorderStroke(
                            width = 4.dp,
                            color = when (status) {
                                TimelineStatus.ACTIVE -> CobaltPrimary
                                TimelineStatus.FUTURE -> MaterialTheme.colorScheme.outlineVariant
                                TimelineStatus.SPECIAL_CONNECTION -> TelefericoCelestial
                                TimelineStatus.TERMINAL -> LimeSecondary
                            }
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (status == TimelineStatus.ACTIVE) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(CobaltPrimary, CircleShape)
                    )
                } else if (status == TimelineStatus.SPECIAL_CONNECTION) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "tel",
                        tint = TelefericoCelestial,
                        modifier = Modifier.size(10.dp)
                    )
                } else if (status == TimelineStatus.TERMINAL) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "term",
                        tint = LimeSecondary,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Text details and time tag
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stopName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Celestial teleferico connection tag
                if (status == TimelineStatus.SPECIAL_CONNECTION) {
                    Row(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Cel",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Conexión Teleférico Celeste",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = stopDesc,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Time tag
            Surface(
                color = if (status == TimelineStatus.TERMINAL) LimeSecondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = timeOffset,
                    color = if (status == TimelineStatus.TERMINAL) LimeOnSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (status == TimelineStatus.TERMINAL) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

// ---------------- DRIVER ACTIVE MODE ----------------

@Composable
fun DriverScreen(
    registeredPhone: String,
    pointsEarned: Int,
    accumulatedEarnings: Int,
    busProgress: Float,
    onEndTurn: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // High Contrast Lime TopBar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(LimeSecondary)
                .statusBarsPadding()
                .padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ExitToApp,
                            contentDescription = "Cerrar Sesión",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "MODO CHOFER",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Chofer: +591 $registeredPhone",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFFE5E9EC))
        ) {
            // Interactive simulated Map with custom route lines
            CustomTransitMap(
                modifier = Modifier.fillMaxSize(),
                primaryRouteSelected = false,
                busProgress = busProgress
            )

            // Dynamic Gamification Gamified Floating Card (Top Left Corner Overlay)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .width(180.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Points",
                            tint = LimeOnSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Puntos Turno: $pointsEarned",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Progress indicator
                    LinearProgressIndicator(
                        progress = { 0.65f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = LimeSecondary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        "9 pts para el próximo bono",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            // Central Teleférico representation overlay (cable car)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-40).dp)
                    .background(TelefericoPurple, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "Línea de Teleférico Celeste Link",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Big Action FAB: FINALIZAR TURNO / CAMBIAR RUTA overlays on Map
            Button(
                onClick = onEndTurn,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(30.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .padding(bottom = 96.dp)
                    .height(54.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Terminar", tint = Color.White)
                    Text(
                        "FINALIZAR TURNO / CAMBIAR RUTA",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            // Bottom Driver Control Panel
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(96.dp),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = LimeSecondary,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "ACTIVO",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Pérez - Ceja (Vía Autopista)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Face,
                                contentDescription = "Sign",
                                tint = CobaltPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Letrero Activo: ",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "'CEJA 16 JULIO FARELLÓN'",
                                fontSize = 11.sp,
                                color = CobaltPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Report Deviation trigger
                    Button(
                        onClick = { AlertToast("Desvío enviado inmediatamente al centro de control satelital.") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Warning, contentDescription = "Alert", tint = ErrorRed, modifier = Modifier.size(16.dp))
                            Text("Reportar Desvío", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- SHIFT STATISTICS & MANAGEMENT SCREEN ----------------

@Composable
fun ShiftManagementScreen(
    registeredPhone: String,
    accumulatedEarnings: Int,
    onStartDailyShift: () -> Unit,
    onSignOut: () -> Unit
) {
    var selectedTabByDriver by remember { mutableStateOf(0) } // 0 = Rutas de Turno, 1 = Reportes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Simple Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CobaltPrimary)
                .statusBarsPadding()
                .padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ExitToApp,
                            contentDescription = "Cerrar Sesión",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "Gestión de Turno",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "ID Chofer: +591 $registeredPhone",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Active profile picture representation
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, contentDescription = "Prof", tint = Color.White)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(16.dp)
        ) {
            // Summary Blue Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CobaltPrimary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            "Resumen del Turno",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Place, contentDescription = "Completed", tint = LimeSecondaryContainer)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Rutas cumplidas", color = Color.White, fontSize = 13.sp)
                                }
                                Text("8", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Face, contentDescription = "Active", tint = LimeSecondaryContainer)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Horas activo", color = Color.White, fontSize = 13.sp)
                                }
                                Text("6h 30m", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .border(BorderStroke(1.dp, LimeSecondary), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Done, contentDescription = "Mon", tint = LimeSecondaryContainer)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Monto Acumulado", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("$accumulatedEarnings BOB", color = LimeSecondaryContainer, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }

            // Tab bar switcher: Rutas / Reportes
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    TextTabSelector(
                        title = "Rutas de Turno",
                        selected = selectedTabByDriver == 0,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTabByDriver = 0 }
                    )
                    TextTabSelector(
                        title = "Reportes financieros",
                        selected = selectedTabByDriver == 1,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTabByDriver = 1 }
                    )
                }
            }

            // Conditional view lists based on tab
            if (selectedTabByDriver == 0) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompletedShiftCard(
                            routeLine = "San Pedro - Achumani",
                            meta = "08:30 - 09:15 | PumaKatari Linea 2",
                            earnings = "60 BOB"
                        )
                        CompletedShiftCard(
                            routeLine = "Obrajes - Centro",
                            meta = "09:30 - 10:05 | PumaKatari Linea 5",
                            earnings = "55 BOB"
                        )
                        CompletedShiftCard(
                            routeLine = "Villa Fátima - Mallasa",
                            meta = "10:20 - 11:45 | Especial Directo",
                            earnings = "120 BOB"
                        )
                    }
                }
            } else {
                // Mock charts and financial stats
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Ingresos por Período",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // Simplified graph drawing with columns
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                BarSegmentChart("Lun", 0.45f)
                                BarSegmentChart("Mar", 0.65f)
                                BarSegmentChart("Mie", 0.35f)
                                BarSegmentChart("Jue", 0.85f)
                                BarSegmentChart("Hoy", 1.0f, isHighlight = true)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Promedio Diario", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("56.2 BOB", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CobaltPrimary)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Eficiencia de Tiempo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("94%", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = LimeSecondary)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { AlertToast("Reporte descargado exitosamente.") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = LimeSecondary)
                            ) {
                                Text("Descargar Reporte Completo (PDF)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Margin bottom
            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun BarSegmentChart(day: String, scale: Float, isHighlight: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(44.dp)
    ) {
        val barHeight = (100 * scale).toInt()
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(barHeight.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(if (isHighlight) LimeSecondary else CobaltPrimary.copy(alpha = 0.3f))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(day, fontSize = 11.sp, fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun CompletedShiftCard(
    routeLine: String,
    meta: String,
    earnings: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(LimeSecondary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Success",
                        tint = LimeSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = routeLine,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = earnings,
                fontWeight = FontWeight.Bold,
                color = LimeOnSecondaryContainer,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun TextTabSelector(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = if (selected) CobaltPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .height(2.dp)
                .fillMaxWidth(0.85f)
                .background(if (selected) CobaltPrimary else Color.Transparent)
        )
    }
}

// ---------------- CUSTOM COMPOSABLE MAP DRAWING ----------------

@Composable
fun CustomTransitMap(
    modifier: Modifier = Modifier,
    primaryRouteSelected: Boolean,
    busProgress: Float
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        // Background terrain shading representing the mountain valley of La Paz
        val backgroundGradient = Brush.radialGradient(
            colors = listOf(Color(0xFFE8ECEF), Color(0xFFD3D8DD)),
            center = Offset(width / 2, height / 2),
            radius = maxOf(width * 0.8f, 1f)
        )
        drawRect(brush = backgroundGradient)

        // Draw multiple curvy grey street lines for topographical feel
        val streetPath1 = Path().apply {
            moveTo(0f, height * 0.1f)
            quadraticTo(width * 0.3f, height * 0.15f, width * 0.5f, height * 0.3f)
            quadraticTo(width * 0.7f, height * 0.45f, width, height * 0.4f)
        }
        drawPath(streetPath1, Color.White, style = Stroke(width = 14f))
        drawPath(streetPath1, Color(0xFFB0B7BD), style = Stroke(width = 2f))

        val streetPath2 = Path().apply {
            moveTo(width * 0.2f, height)
            cubicTo(width * 0.4f, height * 0.7f, width * 0.1f, height * 0.4f, width * 0.6f, height * 0.2f)
            quadraticTo(width * 0.8f, height * 0.1f, width, height * 0.2f)
        }
        drawPath(streetPath2, Color.White, style = Stroke(width = 12f))
        drawPath(streetPath2, Color(0xFFC0C6CB), style = Stroke(width = 2f))

        // Main colored routes
        if (primaryRouteSelected) {
            // Cobalt Blue Transit route winding
            val transitPath = Path().apply {
                moveTo(width * 0.1f, height * 0.8f)
                cubicTo(
                    width * 0.3f, height * 0.75f,
                    width * 0.25f, height * 0.35f,
                    width * 0.6f, height * 0.3f
                )
                quadraticTo(
                    width * 0.8f, height * 0.28f,
                    width * 0.9f, height * 0.15f
                )
            }
            // Draw route shadow background
            drawPath(transitPath, CobaltPrimary.copy(alpha = 0.2f), style = Stroke(width = 12f))
            drawPath(transitPath, CobaltPrimaryContainer, style = Stroke(width = 6f))

            // Pulse point at starting station
            drawCircle(CobaltPrimary, radius = 9f, center = Offset(width * 0.13f, height * 0.79f))
            drawCircle(Color.White, radius = 4f, center = Offset(width * 0.13f, height * 0.79f))

            // Simulate the active Bus position winding along the transitPath
            val busX = width * (0.1f + (0.7f * busProgress))
            val busY = height * (0.8f - (0.6f * busProgress))
            drawCircle(LimeSecondary.copy(alpha = 0.35f), radius = 24f, center = Offset(busX, busY))
            drawCircle(Color.White, radius = 10f, center = Offset(busX, busY))
            drawCircle(LimeSecondary, radius = 6f, center = Offset(busX, busY))
        } else {
            // Lime Green Transit route winding (Driver active view)
            val transitPath = Path().apply {
                moveTo(width * 0.05f, height * 0.45f)
                cubicTo(
                    width * 0.4f, height * 0.5f,
                    width * 0.5f, height * 0.8f,
                    width * 0.85f, height * 0.65f
                )
            }
            drawPath(transitPath, LimeSecondary.copy(alpha = 0.25f), style = Stroke(width = 16f))
            drawPath(transitPath, LimeSecondary, style = Stroke(width = 8f))

            // Bus marker
            val busX = width * (0.05f + (0.75f * busProgress))
            val busY = height * (0.45f + (0.22f * busProgress))
            drawCircle(CobaltPrimary.copy(alpha = 0.35f), radius = 24f, center = Offset(busX, busY))
            drawCircle(Color.White, radius = 10f, center = Offset(busX, busY))
            drawCircle(CobaltPrimary, radius = 6f, center = Offset(busX, busY))
        }
    }
}

// ---------------- HELEPRS / TOAST NOTIFICATION UTILS ----------------

fun AlertToast(message: String) {
    // For local log traces and tracking
    println("Transit system event: $message")
}

// ---------------- WELCOME PORTAL LANDING SCREEN ----------------

@Composable
fun WelcomePortalScreen(onLoginSuccess: (String, String) -> Unit) {
    var phoneInput by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("pasajero") } // "pasajero" or "chofer"
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F1E36),
                        Color(0xFF1B2E4B),
                        Color(0xFF2E4663)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Brand Logo Element
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .size(90.dp)
                    .padding(4.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.DirectionsBus,
                        contentDescription = "Bus Logo",
                        tint = LimeSecondary,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Text(
                text = "LA PAZ TRANSIT",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Rastreo satelital y control de turnos en tiempo real",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Credentials Portal Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Configuración de Acceso",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF12233C)
                    )

                    Text(
                        text = "Ingresa tu número celular registrado para acceso directo:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )

                    // Phone text input
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { input ->
                            val digitsOnly = input.filter { it.isDigit() }
                            if (digitsOnly.length <= 8) {
                                phoneInput = digitsOnly
                                isError = false
                            }
                        },
                        label = { Text("Número de Celular") },
                        placeholder = { Text("Ej. 76543210") },
                        leadingIcon = {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🇧🇴 +591 ", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Black)
                                Box(
                                    modifier = Modifier
                                        .height(16.dp)
                                        .width(1.dp)
                                        .background(Color.Gray.copy(alpha = 0.4f))
                                )
                            }
                        },
                        isError = isError,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CobaltPrimary,
                            unfocusedBorderColor = Color.LightGray,
                            focusedLabelColor = CobaltPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("telephone_input_field")
                    )

                    if (isError) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Selecciona tu perfil de acceso:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF12233C)
                    )

                    // Dual Role selector cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Pasajero card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedRole == "pasajero") CobaltPrimary.copy(alpha = 0.08f) else Color(0xFFF1F3F6)
                            ),
                            border = BorderStroke(
                                width = if (selectedRole == "pasajero") 2.dp else 1.dp,
                                color = if (selectedRole == "pasajero") CobaltPrimary else Color.Transparent
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedRole = "pasajero" }
                                .testTag("role_pasajero_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = "Pasajero Profile",
                                    tint = if (selectedRole == "pasajero") CobaltPrimary else Color.Gray,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Pasajero",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (selectedRole == "pasajero") CobaltPrimary else Color.DarkGray
                                )
                                Text(
                                    text = "Buscar minibuses",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Chofer card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedRole == "chofer") LimeSecondary.copy(alpha = 0.08f) else Color(0xFFF1F3F6)
                            ),
                            border = BorderStroke(
                                width = if (selectedRole == "chofer") 2.dp else 1.dp,
                                color = if (selectedRole == "chofer") LimeSecondary else Color.Transparent
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedRole = "chofer" }
                                .testTag("role_chofer_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DirectionsBus,
                                    contentDescription = "Chofer Profile",
                                    tint = if (selectedRole == "chofer") LimeSecondary else Color.Gray,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Chofer",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (selectedRole == "chofer") LimeSecondary else Color.DarkGray
                                )
                                Text(
                                    text = "Gestión de turnos",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Access Button
                    Button(
                        onClick = {
                            if (phoneInput.length < 8) {
                                isError = true
                                errorMessage = "Ingresa un número de celular de 8 dígitos válido."
                            } else {
                                isError = false
                                android.widget.Toast.makeText(
                                    context,
                                    "Acceso de ingreso inmediato: +591 $phoneInput",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                onLoginSuccess(phoneInput, selectedRole)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedRole == "chofer") LimeSecondary else CobaltPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("welcome_login_button")
                    ) {
                        Text(
                            text = "ACCEDER DIRECTO",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // -- LIVE EXPO DEMO QUICK ACCESS PANEL --
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, LimeSecondary.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = LimeSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "MODO EXPO - PRESENTACIÓN RÁPIDA",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Text(
                        text = "Accesos rápidos configurados para presentar el sistema de base de datos local SQLite hoy:",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 10.5.sp
                    )

                    // Button 1: SuperAdmin (Control y seguimiento a todos los choferes)
                    Button(
                        onClick = {
                            phoneInput = "78756107"
                            selectedRole = "admin"
                            android.widget.Toast.makeText(context, "SuperAdmin Expo conectado con SQLite local", android.widget.Toast.LENGTH_SHORT).show()
                            onLoginSuccess("78756107", "admin")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC6FF00)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = Color(0xFF0F1E36), modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SuperAdmin Control Center (SQLite)", color = Color(0xFF0F1E36), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Don Ciprian (Chofer)
                        Button(
                            onClick = {
                                phoneInput = "76543210"
                                selectedRole = "chofer"
                                android.widget.Toast.makeText(context, "Don Ciprián - Modo Chofer", android.widget.Toast.LENGTH_SHORT).show()
                                onLoginSuccess("76543210", "chofer")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LimeSecondary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Icon(Icons.Filled.DirectionsBus, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Don Ciprián", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, maxLines = 1)
                        }

                        // Doña Juana (Chofer)
                        Button(
                            onClick = {
                                phoneInput = "71239845"
                                selectedRole = "chofer"
                                android.widget.Toast.makeText(context, "Doña Juana - Modo Chofer", android.widget.Toast.LENGTH_SHORT).show()
                                onLoginSuccess("71239845", "chofer")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LimeSecondary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Icon(Icons.Filled.DirectionsBus, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Doña Juana", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, maxLines = 1)
                        }
                    }

                    // Pasajero
                    Button(
                        onClick = {
                            val randPhone = (70000000..79999999).random().toString()
                            phoneInput = randPhone
                            selectedRole = "pasajero"
                            android.widget.Toast.makeText(context, "Pasajero Expo Conectado", android.widget.Toast.LENGTH_SHORT).show()
                            onLoginSuccess(randPhone, "pasajero")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CobaltPrimary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ingresar como Pasajero Expo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Gobierno Autónomo Municipal de La Paz",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.45f)
            )
        }
    }
}

// ---------------- SUPERADMIN CONTROL CENTER DASHBOARD ----------------

@Composable
fun AdminScreen(
    repository: TransitRepository,
    onSignOut: () -> Unit
) {
    val drivers by repository.allDriversFlow.collectAsState(initial = emptyList())
    val shifts by repository.allShiftsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Choferes, 1 = Historial de Turnos
    
    // Add driver modal state
    var showAddDialog by remember { mutableStateOf(false) }
    var newDriverName by remember { mutableStateOf("") }
    var newDriverPhone by remember { mutableStateOf("") }
    var newDriverPlate by remember { mutableStateOf("") }
    var newDriverRoute by remember { mutableStateOf("212 CEJA") }
    var dialogError by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6F9))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Dashboard Header
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E36)),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onSignOut) {
                                Icon(
                                    imageVector = Icons.Filled.ExitToApp,
                                    contentDescription = "Cerrar sesión",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "CONTROL SUPREMO LA PAZ",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(LimeSecondary, CircleShape)
                                    )
                                    Text(
                                        text = "Base de Datos SQLite Activa",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.82f)
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Quick Stats grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val activeDriversCount = drivers.count { it.status == "En Ruta" }
                        val totalEarnings = drivers.sumOf { it.earnings }
                        
                        StatCard(
                            title = "Total Choferes",
                            value = "${drivers.size}",
                            icon = Icons.Filled.Group,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "En Servicio",
                            value = "$activeDriversCount",
                            icon = Icons.Filled.DirectionsBus,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "Ganado Total",
                            value = "$totalEarnings BOB",
                            icon = Icons.Filled.AccountBalanceWallet,
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }
            }

            // Tabs / Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { selectedTab = 0 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 0) CobaltPrimary else Color.White,
                        contentColor = if (selectedTab == 0) Color.White else Color.DarkGray
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Monitoreo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = { selectedTab = 1 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 1) CobaltPrimary else Color.White,
                        contentColor = if (selectedTab == 1) Color.White else Color.DarkGray
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Historial Turnos", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Search Bar & Add Button
            if (selectedTab == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar chofer...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = CobaltPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            showAddDialog = true
                            dialogError = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LimeSecondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(54.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Driver", tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nuevo", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tab Content
            if (selectedTab == 0) {
                // DRIVER LIST CONTEXT
                val filteredDrivers = remember(drivers, searchQuery) {
                    drivers.filter {
                        it.phone.contains(searchQuery) || it.name.contains(searchQuery, ignoreCase = true)
                    }
                }

                if (filteredDrivers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Group, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(60.dp))
                            Text("Ningún chofer registrado coincide.", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filteredDrivers) { driver ->
                            AdminDriverCard(
                                driver = driver,
                                onUpdateStatus = { newStatus ->
                                    scope.launch {
                                        repository.updateDriverStatus(driver.phone, newStatus)
                                    }
                                },
                                onAddEarnings = {
                                    scope.launch {
                                        repository.updateDriverEarnings(driver.phone, driver.earnings + 50, driver.points + 5)
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        repository.deleteDriver(driver)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // SHIFTS LOG
                if (shifts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(60.dp))
                            Text("No se han registrado turnos hoy.", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(shifts) { shift ->
                            AdminShiftCard(shift = shift)
                        }
                    }
                }
            }
        }

        // Add Driver Dialog Modal representation
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Registrar Nuevo Chofer", fontWeight = FontWeight.Bold, color = Color(0xFF102847)) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newDriverName,
                            onValueChange = { newDriverName = it },
                            label = { Text("Nombre Completo") },
                            placeholder = { Text("Ej. Don Andrés Mamani") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newDriverPhone,
                            onValueChange = { newDriverPhone = it.filter { c -> c.isDigit() } },
                            label = { Text("Celular (8 dígitos)") },
                            placeholder = { Text("Ej. 71234567") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newDriverPlate,
                            onValueChange = { newDriverPlate = it },
                            label = { Text("Placa del Minibús") },
                            placeholder = { Text("Ej. LPZ-3948") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newDriverRoute,
                            onValueChange = { newDriverRoute = it },
                            label = { Text("Línea/Ruta Asignada") },
                            placeholder = { Text("Ej. 212 CEJA") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (dialogError.isNotEmpty()) {
                            Text(dialogError, color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = CobaltPrimary),
                        onClick = {
                            if (newDriverName.isBlank() || newDriverPhone.length != 8 || newDriverPlate.isBlank()) {
                                dialogError = "Por favor, completa un celular de 8 dígitos y todos los campos."
                            } else {
                                scope.launch {
                                    val newDriver = DriverEntity(
                                        phone = newDriverPhone,
                                        name = newDriverName,
                                        licensePlate = newDriverPlate.uppercase(),
                                        status = "Descanso",
                                        currentRoute = newDriverRoute.uppercase(),
                                        earnings = 0,
                                        points = 0
                                    )
                                    repository.createOrUpdateDriver(newDriver)
                                    showAddDialog = false
                                    // Reset values
                                    newDriverName = ""
                                    newDriverPhone = ""
                                    newDriverPlate = ""
                                }
                            }
                        }
                    ) {
                        Text("Registrar Chofer", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancelar", color = Color.Gray)
                    }
                }
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = LimeSecondary, modifier = Modifier.size(18.dp))
            Text(title, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1)
            Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1)
        }
    }
}

@Composable
fun AdminDriverCard(
    driver: DriverEntity,
    onUpdateStatus: (String) -> Unit,
    onAddEarnings: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // First Row: Name & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(driver.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF10213B))
                    Text("Contacto: +591 ${driver.phone}", fontSize = 11.sp, color = Color.Gray)
                }

                // Status chip representation
                val (statusColor, statusBgColor) = when (driver.status) {
                    "En Ruta" -> Color(0xFF2E7D32) to Color(0xFFE8F5E9)
                    "Descanso" -> Color(0xFFEF6C00) to Color(0xFFFFF3E0)
                    else -> Color(0xFFD32F2F) to Color(0xFFFFEBEE)
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = statusBgColor),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = driver.status,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("PLACA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text(driver.licensePlate, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
                }

                Column {
                    Text("LINEA ASIGNADA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text(driver.currentRoute, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
                }

                Column {
                    Text("GANANCIA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("${driver.earnings} BOB", fontSize = 13.sp, fontWeight = FontWeight.Black, color = CobaltPrimary)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F2F5))

            // Quick Operations Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Toggle status action
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            val nextStatus = when (driver.status) {
                                "En Ruta" -> "Descanso"
                                "Descanso" -> "Fuera de Servicio"
                                else -> "En Ruta"
                            }
                            onUpdateStatus(nextStatus)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = CobaltPrimary)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Estado", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = onAddEarnings,
                        colors = ButtonDefaults.textButtonColors(contentColor = LimeSecondary)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+50 BOB", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Borrar chofer",
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AdminShiftCard(shift: ShiftEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Chofer: +591 ${shift.driverPhone}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                }

                val shiftStatusColor = if (shift.status == "Activo") Color(0xFF2E7D32) else Color.Gray
                Text(
                    text = shift.status,
                    color = shiftStatusColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Ruta: ${shift.routeCode}", fontSize = 11.sp, color = Color.Gray)
                
                val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                val startFormatted = sdf.format(java.util.Date(shift.startTime))
                val endFormatted = if (shift.endTime > 0) sdf.format(java.util.Date(shift.endTime)) else "En curso..."
                
                Text(
                    text = "$startFormatted - $endFormatted",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }
            
            if (shift.status == "Completado") {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ganancias: ${shift.earnings} BOB", fontSize = 11.sp, color = CobaltPrimary, fontWeight = FontWeight.Bold)
                    Text("Puntos: ${shift.points} pts", fontSize = 11.sp, color = LimeSecondary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

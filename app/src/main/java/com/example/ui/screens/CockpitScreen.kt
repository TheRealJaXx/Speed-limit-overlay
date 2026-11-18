package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.OverlaySize
import com.example.model.SignStyle
import com.example.model.SpeedAlertThreshold
import com.example.model.SpeedUnit
import com.example.ui.CockpitUiState
import com.example.ui.components.SpeedLimitSign
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.AsphaltBlack
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceElevated
import com.example.ui.theme.InstrumentMuted
import com.example.ui.theme.InstrumentWhite
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalEmerald
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CockpitScreen(
    uiState: CockpitUiState,
    onRequestLocation: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotifications: () -> Unit,
    onToggleOverlay: () -> Unit,
    onOpenGoogleMaps: () -> Unit,
    onSelectUnit: (SpeedUnit) -> Unit,
    onSelectSignStyle: (SignStyle) -> Unit,
    onSelectOverlaySize: (OverlaySize) -> Unit,
    onToggleShowCurrentSpeed: (Boolean) -> Unit,
    onSelectAlertThreshold: (SpeedAlertThreshold) -> Unit,
    onResetPosition: () -> Unit
) {
    var showSettingsSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    StatusBadge(
                        isOverlayActive = uiState.isOverlayActive,
                        isReady = uiState.permissions.allGranted
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_settings)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 680.dp

            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        LiveSignHeroStage(
                            uiState = uiState,
                            onSelectUnit = onSelectUnit
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DrivingActionsSection(
                            uiState = uiState,
                            onToggleOverlay = onToggleOverlay,
                            onOpenGoogleMaps = onOpenGoogleMaps
                        )
                        PermissionsChecklistCard(
                            uiState = uiState,
                            onRequestLocation = onRequestLocation,
                            onRequestOverlay = onRequestOverlay,
                            onRequestNotifications = onRequestNotifications
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = 560.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LiveSignHeroStage(
                            uiState = uiState,
                            onSelectUnit = onSelectUnit
                        )
                        DrivingActionsSection(
                            uiState = uiState,
                            onToggleOverlay = onToggleOverlay,
                            onOpenGoogleMaps = onOpenGoogleMaps
                        )
                        PermissionsChecklistCard(
                            uiState = uiState,
                            onRequestLocation = onRequestLocation,
                            onRequestOverlay = onRequestOverlay,
                            onRequestNotifications = onRequestNotifications
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            OverlaySettingsContent(
                uiState = uiState,
                onSelectSignStyle = onSelectSignStyle,
                onSelectOverlaySize = onSelectOverlaySize,
                onToggleShowCurrentSpeed = onToggleShowCurrentSpeed,
                onSelectAlertThreshold = onSelectAlertThreshold,
                onResetPosition = onResetPosition,
                onDismiss = { showSettingsSheet = false }
            )
        }
    }
}

@Composable
private fun StatusBadge(
    isOverlayActive: Boolean,
    isReady: Boolean
) {
    val dotColor by animateColorAsState(
        targetValue = when {
            isOverlayActive -> SignalEmerald
            isReady -> SignalEmerald.copy(alpha = 0.7f)
            else -> SignalAmber
        },
        label = "status_dot"
    )
    val label = when {
        isOverlayActive -> stringResource(R.string.status_overlay_active)
        isReady -> stringResource(R.string.status_ready)
        else -> stringResource(R.string.status_setup_needed)
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LiveSignHeroStage(
    uiState: CockpitUiState,
    onSelectUnit: (SpeedUnit) -> Unit
) {
    val prefs = uiState.preferences
    val telemetry = uiState.telemetry
    val unit = prefs.unit
    val limit = telemetry.speedLimitInfo.limitIn(unit)
    val currentSpeed = telemetry.currentSpeedIn(unit)
    val isSpeeding = telemetry.isSpeeding(unit, prefs.alertThreshold)

    val stageGradient = Brush.radialGradient(
        colors = listOf(
            if (isSpeeding) Color(0xFF3F1219) else Color(0xFF1A2639),
            AsphaltBlack
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(stageGradient)
            .border(
                width = 1.dp,
                color = if (isSpeeding) AlertCrimson.copy(alpha = 0.65f) else CockpitBorder,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header row inside hero stage: Unit selector + GPS status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = null,
                        tint = if (telemetry.hasLocationFix) SignalEmerald else SignalAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = when {
                            !uiState.permissions.hasLocation -> "GPS permission needed"
                            telemetry.hasLocationFix -> {
                                val acc = telemetry.accuracyMeters?.roundToInt()
                                if (acc != null) "GPS ±${acc}m" else "GPS locked"
                            }
                            else -> "Acquiring GPS…"
                        },
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = InstrumentMuted
                    )
                }

                // Compact Unit Switcher (MPH / KM/H)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CockpitSurface)
                        .border(1.dp, CockpitBorder, RoundedCornerShape(50))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SpeedUnit.entries.forEach { option ->
                        val selected = option == unit
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) SignalEmerald else Color.Transparent)
                                .clickable { onSelectUnit(option) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("unit_toggle_${option.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.label,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (selected) Color(0xFF022C22) else InstrumentMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Hero Sign + Live Current Speed side-by-side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpeedLimitSign(
                    speedLimit = limit,
                    unit = unit,
                    signStyle = prefs.signStyle,
                    isSpeeding = isSpeeding,
                    scale = 1.38f,
                    showCurrentSpeedPill = false
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "CURRENT SPEED",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = InstrumentMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (telemetry.hasLocationFix) currentSpeed.toString() else "--",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 48.sp,
                        lineHeight = 52.sp,
                        color = if (isSpeeding) AlertCrimson else InstrumentWhite
                    )
                    Text(
                        text = unit.label,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = InstrumentMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Current Street Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = CockpitSurfaceElevated,
                border = BorderStroke(1.dp, CockpitBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = SignalEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (telemetry.hasLocationFix) {
                                telemetry.speedLimitInfo.displayStreetLabel
                            } else {
                                "Waiting for street fix"
                            },
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = InstrumentWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = when {
                            telemetry.statusNote != null -> telemetry.statusNote
                            !telemetry.hasLocationFix -> "Move outdoors or start navigation for GPS"
                            limit == null && telemetry.speedLimitInfo.roadClass != null ->
                                "${telemetry.speedLimitInfo.roadClass} • Speed limit unmapped on OSM"
                            telemetry.speedLimitInfo.roadClass != null ->
                                "${telemetry.speedLimitInfo.roadClass} • OpenStreetMap data"
                            else -> "OpenStreetMap live road lookup"
                        }
                        Text(
                            text = subtitle,
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 12.sp,
                            color = InstrumentMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrivingActionsSection(
    uiState: CockpitUiState,
    onToggleOverlay: () -> Unit,
    onOpenGoogleMaps: () -> Unit
) {
    val active = uiState.isOverlayActive
    val canStart = uiState.permissions.hasLocation && uiState.permissions.hasOverlay

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onToggleOverlay,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("toggle_overlay_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (active) AlertCrimson else MaterialTheme.colorScheme.primary,
                contentColor = if (active) Color.White else MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            Icon(
                imageVector = if (active) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    active -> stringResource(R.string.action_stop_overlay)
                    canStart -> stringResource(R.string.action_start_overlay)
                    else -> "Grant Permissions to Start"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        FilledTonalButton(
            onClick = onOpenGoogleMaps,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("open_maps_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = stringResource(R.string.cd_open_maps),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.action_open_maps),
                style = MaterialTheme.typography.labelLarge
            )
        }

        if (active) {
            Text(
                text = "Tip: Drag the floating sign anywhere over Google Maps, or tap it to view street name & stop.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun PermissionsChecklistCard(
    uiState: CockpitUiState,
    onRequestLocation: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotifications: () -> Unit
) {
    val perms = uiState.permissions
    var showWhenComplete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = perms.allGranted) { showWhenComplete = !showWhenComplete },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (perms.allGranted) Icons.Default.CheckCircle else Icons.Default.Layers,
                        contentDescription = null,
                        tint = if (perms.allGranted) SignalEmerald else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (perms.allGranted) {
                            "All Overlay Permissions Ready"
                        } else {
                            "Required Setup"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (perms.allGranted) {
                    Text(
                        text = if (showWhenComplete) "Hide" else "View",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(
                visible = !perms.allGranted || showWhenComplete,
                enter = androidx.compose.animation.expandVertically(spring()),
                exit = androidx.compose.animation.shrinkVertically(spring())
            ) {
                Column(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PermissionRow(
                        icon = Icons.Default.MyLocation,
                        title = stringResource(R.string.perm_location_title),
                        description = stringResource(R.string.perm_location_desc),
                        granted = perms.hasLocation,
                        testTag = "grant_location_button",
                        onGrant = onRequestLocation
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    PermissionRow(
                        icon = Icons.Default.Layers,
                        title = stringResource(R.string.perm_overlay_title),
                        description = stringResource(R.string.perm_overlay_desc),
                        granted = perms.hasOverlay,
                        testTag = "grant_overlay_button",
                        onGrant = onRequestOverlay
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        PermissionRow(
                            icon = Icons.Default.Notifications,
                            title = stringResource(R.string.perm_notifications_title),
                            description = stringResource(R.string.perm_notifications_desc),
                            granted = perms.hasNotifications,
                            testTag = "grant_notifications_button",
                            onGrant = onRequestNotifications
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    testTag: String,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (granted) SignalEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (granted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Granted",
                tint = SignalEmerald,
                modifier = Modifier.size(24.dp)
            )
        } else {
            OutlinedButton(
                onClick = onGrant,
                modifier = Modifier
                    .height(48.dp)
                    .testTag(testTag),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = stringResource(R.string.action_grant))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverlaySettingsContent(
    uiState: CockpitUiState,
    onSelectSignStyle: (SignStyle) -> Unit,
    onSelectOverlaySize: (OverlaySize) -> Unit,
    onToggleShowCurrentSpeed: (Boolean) -> Unit,
    onSelectAlertThreshold: (SpeedAlertThreshold) -> Unit,
    onResetPosition: () -> Unit,
    onDismiss: () -> Unit
) {
    val prefs = uiState.preferences

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Overlay Settings",
                style = MaterialTheme.typography.headlineMedium
            )
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }

        // Sign Style
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sign Style", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SignStyle.entries.forEachIndexed { index, style ->
                    SegmentedButton(
                        selected = prefs.signStyle == style,
                        onClick = { onSelectSignStyle(style) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = SignStyle.entries.size
                        )
                    ) {
                        Text(style.title)
                    }
                }
            }
        }

        // Overlay Size
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Overlay Size", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                OverlaySize.entries.forEachIndexed { index, size ->
                    SegmentedButton(
                        selected = prefs.overlaySize == size,
                        onClick = { onSelectOverlaySize(size) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = OverlaySize.entries.size
                        )
                    ) {
                        Text(size.label)
                    }
                }
            }
        }

        // Speeding Alert Threshold
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Speeding Visual Alert (${prefs.unit.label})",
                style = MaterialTheme.typography.titleMedium
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SpeedAlertThreshold.entries.forEachIndexed { index, threshold ->
                    val label = when (threshold) {
                        SpeedAlertThreshold.OFF -> "Off"
                        SpeedAlertThreshold.AT_LIMIT -> "Limit"
                        else -> "+${threshold.thresholdFor(prefs.unit)}"
                    }
                    SegmentedButton(
                        selected = prefs.alertThreshold == threshold,
                        onClick = { onSelectAlertThreshold(threshold) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = SpeedAlertThreshold.entries.size
                        )
                    ) {
                        Text(label)
                    }
                }
            }
        }

        // Show Current Speed Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onToggleShowCurrentSpeed(!prefs.showCurrentSpeed) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Live Speed Pill", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Displays your current GPS speed beneath the floating limit sign",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = prefs.showCurrentSpeed,
                onCheckedChange = onToggleShowCurrentSpeed
            )
        }

        OutlinedButton(
            onClick = onResetPosition,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Reset Floating Sign Position")
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

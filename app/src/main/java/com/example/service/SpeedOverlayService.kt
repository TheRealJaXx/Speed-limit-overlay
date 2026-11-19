package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.data.DrivingLocationTracker
import com.example.data.EtaCalculator
import com.example.data.OverlayRuntimeState
import com.example.data.PreferencesRepository
import com.example.data.SpeedLimitRepository
import com.example.model.ActiveTripEta
import com.example.model.DrivingTelemetry
import com.example.model.LearnedPaceProfile
import com.example.model.OverlayPreferences
import com.example.model.SpeedUnit
import com.example.ui.components.SpeedLimitSign
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.InstrumentMuted
import com.example.ui.theme.InstrumentWhite
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalEmerald
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

class SpeedOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.example.action.START_OVERLAY"
        const val ACTION_STOP = "com.example.action.STOP_OVERLAY"
        const val ACTION_RESET_POSITION = "com.example.action.RESET_POSITION"
        private const val CHANNEL_ID = "speed_overlay_channel"
        private const val NOTIFICATION_ID = 4102

        fun start(context: Context) {
            val intent = Intent(context, SpeedOverlayService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SpeedOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun resetPosition(context: Context) {
            val intent = Intent(context, SpeedOverlayService::class.java).apply {
                action = ACTION_RESET_POSITION
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var locationJob: Job? = null
    private var lastPaceSampleMillis: Long = 0L

    private lateinit var windowManager: WindowManager
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var speedLimitRepository: SpeedLimitRepository
    private lateinit var locationTracker: DrivingLocationTracker

    private var overlayView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        preferencesRepository = PreferencesRepository(applicationContext)
        speedLimitRepository = SpeedLimitRepository()
        locationTracker = DrivingLocationTracker(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopOverlayAndService()
                return START_NOT_STICKY
            }
            ACTION_RESET_POSITION -> {
                serviceScope.launch {
                    preferencesRepository.setOverlayPosition(32, 220)
                    overlayParams?.let { params ->
                        params.x = 32
                        params.y = 220
                        overlayView?.let { view ->
                            runCatching { windowManager.updateViewLayout(view, params) }
                        }
                    }
                }
                return START_STICKY
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        OverlayRuntimeState.setOverlayRunning(true)

        if (overlayView == null) {
            serviceScope.launch {
                val initialPrefs = preferencesRepository.preferencesFlow.first()
                attachOverlayWindow(initialPrefs.overlayX, initialPrefs.overlayY)
            }
        }

        startLocationAndSpeedUpdates()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        OverlayRuntimeState.setOverlayRunning(false)
        locationJob?.cancel()
        detachOverlayWindow()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun stopOverlayAndService() {
        OverlayRuntimeState.setOverlayRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground() {
        val notification = buildNotification("Watching GPS speed, street limit & pace")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SpeedOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(statusText)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                R.mipmap.ic_launcher_round,
                getString(R.string.overlay_notification_stop),
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.overlay_notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startLocationAndSpeedUpdates() {
        if (locationJob?.isActive == true) return

        locationJob = serviceScope.launch(Dispatchers.IO) {
            locationTracker.locationUpdates()
                .catch { error ->
                    val current = OverlayRuntimeState.telemetry.value
                    OverlayRuntimeState.updateTelemetry(
                        current.copy(statusNote = error.localizedMessage ?: "Location unavailable")
                    )
                }
                .collectLatest { location ->
                    OverlayRuntimeState.advanceTripDistance(location.latitude, location.longitude)

                    val current = OverlayRuntimeState.telemetry.value
                    val baseTelemetry = current.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        currentSpeedMps = if (location.hasSpeed()) location.speed else 0f,
                        bearingDegrees = if (location.hasBearing()) location.bearing else null,
                        accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                        hasLocationFix = true,
                        isFetchingStreet = true,
                        statusNote = null
                    )
                    OverlayRuntimeState.updateTelemetry(baseTelemetry)

                    val lookup = speedLimitRepository.lookupStreetSpeedLimit(
                        lat = location.latitude,
                        lon = location.longitude,
                        bearingDegrees = baseTelemetry.bearingDegrees,
                        speedMps = baseTelemetry.currentSpeedMps
                    )

                    val updatedInfo = lookup.info ?: baseTelemetry.speedLimitInfo
                    val updatedTelemetry = baseTelemetry.copy(
                        speedLimitInfo = updatedInfo,
                        isFetchingStreet = false,
                        statusNote = lookup.errorMessage
                    )
                    OverlayRuntimeState.updateTelemetry(updatedTelemetry)

                    val now = System.currentTimeMillis()
                    if (now - lastPaceSampleMillis >= 5_000L) {
                        val currentProfile = preferencesRepository.learnedPaceFlow.first()
                        val updatedProfile = EtaCalculator.recordDrivingSample(
                            current = currentProfile,
                            tier = updatedInfo.roadTier,
                            speedMps = updatedTelemetry.currentSpeedMps,
                            accuracyMeters = updatedTelemetry.accuracyMeters
                        )
                        if (updatedProfile != null) {
                            lastPaceSampleMillis = now
                            preferencesRepository.saveLearnedPaceProfile(updatedProfile)
                        }
                    }
                }
        }
    }

    private fun attachOverlayWindow(initialX: Int, initialY: Int) {
        if (overlayView != null) return

        val owner = OverlayLifecycleOwner().apply {
            performCreate()
            performResume()
        }
        lifecycleOwner = owner

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }
        overlayParams = params

        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val telemetry by OverlayRuntimeState.telemetry.collectAsState()
                val activeTrip by OverlayRuntimeState.activeTrip.collectAsState()
                val prefs by preferencesRepository.preferencesFlow.collectAsState(
                    initial = OverlayPreferences()
                )
                val learnedPace by preferencesRepository.learnedPaceFlow.collectAsState(
                    initial = LearnedPaceProfile()
                )

                FloatingOverlayHud(
                    telemetry = telemetry,
                    prefs = prefs,
                    learnedPace = learnedPace,
                    activeTrip = activeTrip,
                    onDragDelta = { dx, dy ->
                        val p = overlayParams ?: return@FloatingOverlayHud
                        val metrics = resources.displayMetrics
                        val maxX = (metrics.widthPixels - width).coerceAtLeast(0)
                        val maxY = (metrics.heightPixels - height).coerceAtLeast(0)
                        p.x = (p.x + dx.roundToInt()).coerceIn(0, maxX)
                        p.y = (p.y + dy.roundToInt()).coerceIn(0, maxY)
                        runCatching { windowManager.updateViewLayout(this, p) }
                    },
                    onDragEnd = {
                        val p = overlayParams ?: return@FloatingOverlayHud
                        serviceScope.launch {
                            preferencesRepository.setOverlayPosition(p.x, p.y)
                        }
                    },
                    onOpenApp = {
                        val launchIntent = Intent(this@SpeedOverlayService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(launchIntent)
                    },
                    onStopOverlay = {
                        stopOverlayAndService()
                    }
                )
            }
        }

        overlayView = view
        runCatching {
            windowManager.addView(view, params)
        }.onFailure {
            detachOverlayWindow()
            stopSelf()
        }
    }

    private fun detachOverlayWindow() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        overlayParams = null
        lifecycleOwner?.performDestroy()
        lifecycleOwner = null
    }
}

@Composable
private fun FloatingOverlayHud(
    telemetry: DrivingTelemetry,
    prefs: OverlayPreferences,
    learnedPace: LearnedPaceProfile,
    activeTrip: ActiveTripEta,
    onDragDelta: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onOpenApp: () -> Unit,
    onStopOverlay: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val unit = prefs.unit
    val limit = telemetry.speedLimitInfo.limitIn(unit)
    val currentSpeed = telemetry.currentSpeedIn(unit)
    val isSpeeding = telemetry.isSpeeding(unit, prefs.alertThreshold)
    val etaSummary = remember(telemetry, prefs, learnedPace, activeTrip) {
        EtaCalculator.calculateEtaSummary(
            telemetry = telemetry,
            prefs = prefs,
            learnedProfile = learnedPace,
            activeTrip = activeTrip
        )
    }

    val capInUnit = if (unit == SpeedUnit.MPH) {
        (prefs.personalMaxSpeedKmh * 0.621371f).roundToInt()
    } else {
        prefs.personalMaxSpeedKmh
    }

    val etaOverlayText = remember(etaSummary) {
        val arrival = etaSummary.recalculatedArrivalMillis ?: return@remember null
        val timeStr = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(arrival))
        val delta = etaSummary.deltaMinutesVsMaps ?: 0
        when {
            delta > 0 -> "ETA $timeStr (+${delta}m)"
            delta < 0 -> "ETA $timeStr (${delta}m)"
            else -> "ETA $timeStr"
        }
    }

    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.x, dragAmount.y)
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable { expanded = !expanded }
        ) {
            SpeedLimitSign(
                speedLimit = limit,
                unit = unit,
                signStyle = prefs.signStyle,
                isSpeeding = isSpeeding,
                scale = prefs.overlaySize.scaleFactor,
                currentSpeed = if (telemetry.hasLocationFix) currentSpeed else null,
                showCurrentSpeedPill = prefs.showCurrentSpeed,
                isExceedingPersonalCap = etaSummary.isExceedingPersonalCap,
                personalCapValue = if (prefs.personalMaxSpeedEnabled) capInUnit else null,
                etaOverlayText = etaOverlayText
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .widthIn(min = 172.dp, max = 230.dp)
                    .shadow(8.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(CockpitSurface.copy(alpha = 0.95f))
                    .border(
                        width = 1.dp,
                        color = when {
                            isSpeeding -> AlertCrimson
                            etaSummary.isExceedingPersonalCap -> SignalAmber
                            else -> CockpitBorder
                        },
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (telemetry.hasLocationFix) SignalEmerald else SignalAmber)
                    )
                    Text(
                        text = telemetry.speedLimitInfo.displayStreetLabel,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = InstrumentWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Pace: ${etaSummary.effectivePaceIn(unit)} ${unit.label.lowercase()} (${etaSummary.paceSourceLabel})",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = SignalEmerald,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (etaSummary.extraMinutesPerHour > 0 && etaSummary.recalculatedRemainingMinutes == null) {
                    Text(
                        text = "+${etaSummary.extraMinutesPerHour}m per hr vs limit",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 10.sp,
                        color = InstrumentMuted
                    )
                }

                if (etaSummary.recalculatedRemainingMinutes != null) {
                    Text(
                        text = "${etaSummary.recalculatedRemainingMinutes} min left • Open app to edit ETA",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 10.sp,
                        color = InstrumentMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenApp,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B))
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = stringResource(R.string.app_name),
                            tint = InstrumentWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onStopOverlay,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF7F1D1D))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_close_overlay),
                            tint = InstrumentWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun performCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun performResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun performDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.OverlayPreferences
import com.example.model.OverlaySize
import com.example.model.SignStyle
import com.example.model.SpeedAlertThreshold
import com.example.model.SpeedUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.overlayDataStore: DataStore<Preferences> by preferencesDataStore(name = "speedsign_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val SPEED_UNIT = stringPreferencesKey("speed_unit")
        val SIGN_STYLE = stringPreferencesKey("sign_style")
        val OVERLAY_SIZE = stringPreferencesKey("overlay_size")
        val SHOW_CURRENT_SPEED = booleanPreferencesKey("show_current_speed")
        val ALERT_THRESHOLD = stringPreferencesKey("alert_threshold")
        val OVERLAY_X = intPreferencesKey("overlay_x")
        val OVERLAY_Y = intPreferencesKey("overlay_y")
    }

    val preferencesFlow: Flow<OverlayPreferences> = context.overlayDataStore.data.map { prefs ->
        OverlayPreferences(
            unit = prefs[Keys.SPEED_UNIT]?.let { runCatching { SpeedUnit.valueOf(it) }.getOrNull() }
                ?: SpeedUnit.MPH,
            signStyle = prefs[Keys.SIGN_STYLE]?.let { runCatching { SignStyle.valueOf(it) }.getOrNull() }
                ?: SignStyle.US_MUTCD,
            overlaySize = prefs[Keys.OVERLAY_SIZE]?.let { runCatching { OverlaySize.valueOf(it) }.getOrNull() }
                ?: OverlaySize.STANDARD,
            showCurrentSpeed = prefs[Keys.SHOW_CURRENT_SPEED] ?: true,
            alertThreshold = prefs[Keys.ALERT_THRESHOLD]?.let {
                runCatching { SpeedAlertThreshold.valueOf(it) }.getOrNull()
            } ?: SpeedAlertThreshold.PLUS_5,
            overlayX = prefs[Keys.OVERLAY_X] ?: 32,
            overlayY = prefs[Keys.OVERLAY_Y] ?: 220
        )
    }

    suspend fun setSpeedUnit(unit: SpeedUnit) {
        context.overlayDataStore.edit { it[Keys.SPEED_UNIT] = unit.name }
    }

    suspend fun setSignStyle(style: SignStyle) {
        context.overlayDataStore.edit { it[Keys.SIGN_STYLE] = style.name }
    }

    suspend fun setOverlaySize(size: OverlaySize) {
        context.overlayDataStore.edit { it[Keys.OVERLAY_SIZE] = size.name }
    }

    suspend fun setShowCurrentSpeed(show: Boolean) {
        context.overlayDataStore.edit { it[Keys.SHOW_CURRENT_SPEED] = show }
    }

    suspend fun setAlertThreshold(threshold: SpeedAlertThreshold) {
        context.overlayDataStore.edit { it[Keys.ALERT_THRESHOLD] = threshold.name }
    }

    suspend fun setOverlayPosition(x: Int, y: Int) {
        context.overlayDataStore.edit {
            it[Keys.OVERLAY_X] = x
            it[Keys.OVERLAY_Y] = y
        }
    }
}

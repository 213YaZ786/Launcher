package com.yaz.launcher.data.glance

// What the panels' header reads: the clock, the battery, the next alarm, the ringer and Do not disturb, and the apps behind the missed calls and the
// unread messages. Nothing runs unless the header is shown ([active] is false otherwise): the clock ticks, the receivers are registered, and all of it
// stops with the header, the lifecycle's STARTED state included (no background work).
// Sources, AOSP SystemUI (LineageOS android_frameworks_base, Apache-2.0, lineage-22.2), the status bar's own readers:
// - BatteryControllerImpl.onReceive: the level is 100 * EXTRA_LEVEL / EXTRA_SCALE, charging is plugged (EXTRA_PLUGGED) with a status of CHARGING or
//   FULL, charged is BATTERY_STATUS_FULL, and the battery saver is PowerManager.isPowerSaveMode, refreshed on ACTION_POWER_SAVE_MODE_CHANGED
//   (https://github.com/LineageOS/android_frameworks_base/blob/lineage-22.2/packages/SystemUI/src/com/android/systemui/statusbar/policy/BatteryControllerImpl.java).
// - PhoneStatusBarPolicy.updateVolumeZen and updateAlarm: Do not disturb comes before the ringer's vibrate and silent, the next alarm is
//   AlarmManager.getNextAlarmClock
//   (https://github.com/LineageOS/android_frameworks_base/blob/lineage-22.2/packages/SystemUI/src/com/android/systemui/statusbar/phone/PhoneStatusBarPolicy.java).
// - The time left to charge is BatteryManager.computeChargeTimeRemaining, -1 when unknown
//   (https://developer.android.com/reference/android/os/BatteryManager#computeChargeTimeRemaining()).
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/** The battery now. [remainingMs] is the time to full while charging, -1 when Android does not know or when it is not charging. */
data class BatteryState(val percent: Int, val charging: Boolean, val charged: Boolean, val saver: Boolean, val remainingMs: Long)

/** How the phone rings now: Do not disturb comes first, then the ringer's silent and vibrate. */
enum class PhoneMode { NORMAL, VIBRATE, SILENT, DND }

/**
 * The mode of the ringer and the next alarm in the next 24 hours (its time in milliseconds, 0 when none), and [nextAlarmIntent], the alarm's showIntent
 * (the screen the clock app opens for it), null when the alarm has none or when there is no alarm shown.
 */
data class PhoneStatus(val mode: PhoneMode = PhoneMode.NORMAL, val alarmAt: Long = 0L, val nextAlarmIntent: PendingIntent? = null)

/** The alarms shown are the ones in the next day. */
private const val ALARM_WINDOW_MS = 24L * 60 * 60 * 1000

/** BatteryControllerImpl.onReceive's reading of a battery intent. */
fun readBattery(context: Context, intent: Intent): BatteryState {
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val percent = (100 * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) / scale).coerceIn(0, 100)
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    val charged = status == BatteryManager.BATTERY_STATUS_FULL
    val charging = plugged && (status == BatteryManager.BATTERY_STATUS_CHARGING || charged)
    val saver = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    val remaining = if (charging && !charged) {
        context.getSystemService(BatteryManager::class.java)?.computeChargeTimeRemaining() ?: -1L
    } else {
        -1L
    }
    return BatteryState(percent, charging, charged, saver, remaining)
}

/**
 * PhoneStatusBarPolicy.updateVolumeZen and updateAlarm: Do not disturb, else the ringer's mode, and the next alarm clock within a day. The alarm's
 * showIntent is kept beside its time, as ShadeHeaderController's nextAlarmCallback keeps nextAlarm?.showIntent in nextAlarmIntent
 * (https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/src/com/android/systemui/shade/ShadeHeaderController.kt#nextAlarmIntent).
 */
fun readPhone(context: Context, now: Long = System.currentTimeMillis()): PhoneStatus {
    val filter = context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
        ?: NotificationManager.INTERRUPTION_FILTER_ALL
    val dnd = filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
        filter == NotificationManager.INTERRUPTION_FILTER_NONE ||
        filter == NotificationManager.INTERRUPTION_FILTER_ALARMS
    val mode = when {
        dnd -> PhoneMode.DND
        else -> when (context.getSystemService(AudioManager::class.java)?.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> PhoneMode.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> PhoneMode.VIBRATE
            else -> PhoneMode.NORMAL
        }
    }
    val nextAlarm = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock
    val trigger = nextAlarm?.triggerTime ?: 0L
    val alarm = if (trigger > now && trigger - now <= ALARM_WINDOW_MS) trigger else 0L
    // nextAlarmIntent = nextAlarm?.showIntent; ours goes with the alarm's window: no alarm shown, no intent.
    val nextAlarmIntent = if (alarm > 0L) nextAlarm?.showIntent else null
    return PhoneStatus(mode, alarm, nextAlarmIntent)
}

/**
 * The clock in milliseconds, ticking each second on the second while [active] (a LaunchedEffect that waits for the next second, the way the lock
 * screen's clock is driven by its minute or second ticks; here the coroutine is the only timer and it ends with the header).
 */
@Composable
fun rememberClock(active: Boolean): State<Long> {
    // The previews and the screenshot tests (inspection mode) show a fixed moment that never ticks, so their pictures stay the same
    // (https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/package-summary#LocalInspectionMode()).
    val inspection = androidx.compose.ui.platform.LocalInspectionMode.current
    val now = remember { mutableLongStateOf(if (inspection) INSPECTION_NOW else System.currentTimeMillis()) }
    LaunchedEffect(active, inspection) {
        while (active && !inspection) {
            now.longValue = System.currentTimeMillis()
            delay(1000L - now.longValue % 1000L)
        }
    }
    return now
}

/** 2026-11-01 14:32:07 UTC, the moment the previews and the screenshot tests show. */
private const val INSPECTION_NOW = 1_793_543_527_000L

/**
 * The battery while [active]: a receiver for ACTION_BATTERY_CHANGED (sticky, so the first value is read at once) and for the battery saver's
 * change, registered with the header and removed with it (BatteryControllerImpl registers the same two actions,
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-22.2/packages/SystemUI/src/com/android/systemui/statusbar/policy/BatteryControllerImpl.java).
 */
@Composable
fun rememberBattery(active: Boolean): State<BatteryState?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<BatteryState?>(null) }
    DisposableEffect(active) {
        var receiver: BroadcastReceiver? = null
        if (active) {
            var last: Intent? = null
            val each = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    if (intent.action == Intent.ACTION_BATTERY_CHANGED) last = intent
                    last?.let { state.value = readBattery(c, it) }
                }
            }
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply { addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) }
            last = context.registerReceiver(each, filter, Context.RECEIVER_NOT_EXPORTED)
            last?.let { state.value = readBattery(context, it) }
            receiver = each
        }
        onDispose { receiver?.let { context.unregisterReceiver(it) } }
    }
    return state
}

/**
 * The ringer, Do not disturb and the next alarm while [active]: read when the header appears and on their broadcasts only
 * (AudioManager.RINGER_MODE_CHANGED_ACTION, NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED, AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED, the
 * broadcasts PhoneStatusBarPolicy listens to,
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-22.2/packages/SystemUI/src/com/android/systemui/statusbar/phone/PhoneStatusBarPolicy.java).
 */
@Composable
fun rememberPhone(active: Boolean): State<PhoneStatus> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(PhoneStatus()) }
    DisposableEffect(active) {
        var receiver: BroadcastReceiver? = null
        if (active) {
            state.value = readPhone(context)
            val each = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    state.value = readPhone(c)
                }
            }
            val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION).apply {
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            }
            context.registerReceiver(each, filter, Context.RECEIVER_NOT_EXPORTED)
            receiver = each
        }
        onDispose { receiver?.let { context.unregisterReceiver(it) } }
    }
    return state
}

/** The default phone app: TelecomManager.getDefaultDialerPackage (https://developer.android.com/reference/android/telecom/TelecomManager#getDefaultDialerPackage()). */
fun defaultDialerPackage(context: Context): String? = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage

/** The default messages app: Telephony.Sms.getDefaultSmsPackage (https://developer.android.com/reference/android/provider/Telephony.Sms#getDefaultSmsPackage(android.content.Context)). */
fun defaultSmsPackage(context: Context): String? = Telephony.Sms.getDefaultSmsPackage(context)

/**
 * Opens an app by its launch intent (PackageManager.getLaunchIntentForPackage, https://developer.android.com/reference/android/content/pm/PackageManager#getLaunchIntentForPackage(java.lang.String);
 * the apps with a launcher entry are visible through the manifest's queries). False when there is none.
 */
fun launchPackage(context: Context, packageName: String): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
    return runCatching { context.startActivity(intent) }.isSuccess
}

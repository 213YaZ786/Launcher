package com.yaz.launcher.data.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat

/** The kinds of profile the launcher tells apart; Trebuchet's UserIconInfo.TYPE_MAIN, TYPE_WORK, TYPE_CLONED, TYPE_PRIVATE. */
enum class ProfileKind { MAIN, WORK, CLONE, PRIVATE }

/** A profile of the phone: its handle, its kind, its serial number, and whether its apps can be started now. */
data class ProfileInfo(
    val user: UserHandle,
    val kind: ProfileKind,
    val serial: Long,
    /** Paused (work) or locked (private): UserManager.isQuietModeEnabled. */
    val quiet: Boolean,
    /** UserManager.isUserUnlocked: a locked profile lists nothing. */
    val unlocked: Boolean
)

object Profiles {

    // The user types of Android's UserManager (USER_TYPE_PROFILE_MANAGED, _CLONE, _PRIVATE), as their string values.
    internal const val TYPE_MANAGED = "android.os.usertype.profile.MANAGED"
    internal const val TYPE_CLONE = "android.os.usertype.profile.CLONE"
    internal const val TYPE_PRIVATE = "android.os.usertype.profile.PRIVATE"

    /**
     * The kind for a user type string. After SystemApiWrapper.queryAllUsers, the `when (userType)`
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/quickstep/src/com/android/launcher3/uioverrides/SystemApiWrapper.kt).
     */
    internal fun kindOfUserType(userType: String?): ProfileKind = when (userType) {
        TYPE_MANAGED -> ProfileKind.WORK
        TYPE_CLONE -> ProfileKind.CLONE
        TYPE_PRIVATE -> ProfileKind.PRIVATE
        else -> ProfileKind.MAIN
    }

    /**
     * Every profile of the phone with its kind. After ApiWrapper.queryAllUsers
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/util/ApiWrapper.java):
     * UserManager.getUserProfiles, and the badge trick for the work profile (a drawable that Android badges is a
     * managed profile's). On Android 15 and later the kind comes from LauncherApps.getLauncherUserInfo, as
     * SystemApiWrapper.queryAllUsers does, which also tells the private space and a clone.
     */
    fun queryAll(context: Context): List<ProfileInfo> {
        val users = context.getSystemService(UserManager::class.java) ?: return emptyList()
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val packages = context.packageManager
        return (users.userProfiles ?: emptyList()).map { user ->
            val serial = users.getSerialNumberForUser(user)
            val reported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                launcherApps?.getLauncherUserInfo(user)?.let { kindOfUserType(it.userType) }
            } else {
                null
            }
            val kind = reported ?: run {
                // Simple check to find out whether the provided user is a work profile (ApiWrapper's own comment).
                val probe = NoopDrawable()
                if (probe !== packages.getUserBadgedIcon(probe, user)) ProfileKind.WORK else ProfileKind.MAIN
            }
            ProfileInfo(
                user = user,
                kind = kind,
                serial = serial,
                quiet = runCatching { users.isQuietModeEnabled(user) }.getOrDefault(false),
                unlocked = runCatching { users.isUserUnlocked(user) }.getOrDefault(true)
            )
        }
    }

    /**
     * The serials of the profiles to load again after an event: those added, removed, or whose kind, quiet or unlocked state differs.
     * Trebuchet's PackageUpdatedTask with OP_USER_AVAILABILITY_CHANGE touches only that user's items (`ItemInfoMatcher.ofUser(mUser)`),
     * the other profiles' icons are not reloaded
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/PackageUpdatedTask.java).
     * The broadcast does not say which profile it is about, so the new list is compared with the old.
     */
    fun changed(old: List<ProfileInfo>, new: List<ProfileInfo>): Set<Long> {
        val before = old.associateBy { it.serial }
        val after = new.associateBy { it.serial }
        return after.filter { (serial, info) -> before[serial] != info }.keys + (before.keys - after.keys)
    }

    /** ApiWrapper.NoopDrawable: a drawable of one pixel, which Android returns as it is unless the user is badged. */
    private class NoopDrawable : ColorDrawable() {
        override fun getIntrinsicHeight(): Int = 1
        override fun getIntrinsicWidth(): Int = 1
    }

    /**
     * The broadcasts that tell a profile came, went, was paused or locked (UserCache.initAsync's list). The private space sends
     * PROFILE_AVAILABLE and PROFILE_UNAVAILABLE when it is unlocked or locked, which UserCache adds to its filter for the private space
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/pm/UserCache.java);
     * the private space exists from Android 15 (VANILLA_ICE_CREAM), so the two are listened to from there.
     */
    internal fun actions(sdk: Int): List<String> {
        val atLeastU = sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        val atLeastV = sdk >= Build.VERSION_CODES.VANILLA_ICE_CREAM
        return listOfNotNull(
            Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
            Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
            Intent.ACTION_MANAGED_PROFILE_REMOVED,
            if (atLeastU) Intent.ACTION_PROFILE_ADDED else Intent.ACTION_MANAGED_PROFILE_ADDED,
            if (atLeastU) Intent.ACTION_PROFILE_REMOVED else Intent.ACTION_MANAGED_PROFILE_REMOVED,
            if (atLeastU) Intent.ACTION_PROFILE_ACCESSIBLE else Intent.ACTION_MANAGED_PROFILE_UNLOCKED,
            if (atLeastU) Intent.ACTION_PROFILE_INACCESSIBLE else Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
            if (atLeastV) ACTION_PROFILE_AVAILABLE else null,
            if (atLeastV) ACTION_PROFILE_UNAVAILABLE else null
        ).distinct()
    }

    internal const val ACTION_PROFILE_AVAILABLE = "android.intent.action.PROFILE_AVAILABLE"
    internal const val ACTION_PROFILE_UNAVAILABLE = "android.intent.action.PROFILE_UNAVAILABLE"
}

/**
 * The state of the work profile and of the private space, one per profile (the key is its serial). UserProfileManager keeps one
 * mCurrentState for the profile type it manages: STATE_UNKNOWN before the first reset, STATE_ENABLED running or unlocked, STATE_DISABLED paused or
 * locked, STATE_TRANSITION from the tap on its tile until Android's answer arrives
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/UserProfileManager.java#STATE_TRANSITION).
 * The tap sets the TRANSITION ([setCurrentState], WorkProfileManager.setWorkProfileEnabled's updateCurrentState); [reset] reads the quiet state
 * again after a profile reload, as PrivateProfileManager.reset does: previousState, `isEnabled = !hasModelFlag(QUIET_MODE_ENABLED)`, updatedState
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/PrivateProfileManager.java#reset).
 * Calls come from the main thread and the model's, hence @Synchronized.
 */
class UserProfileManager {

    companion object {
        const val STATE_UNKNOWN = 0
        const val STATE_ENABLED = 1
        const val STATE_DISABLED = 2
        const val STATE_TRANSITION = 3

        // UserProfileManager.getCurrentState for a tile: the manager's state, or, while it is STATE_UNKNOWN (no reset yet, a fixed state in a test),
        // what the first reset sets, `isEnabled = !hasModelFlag(QUIET_MODE_ENABLED)` (PrivateProfileManager.reset)
        // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/UserProfileManager.java#getCurrentState).
        fun getCurrentState(profile: ProfileInfo, states: Map<Long, Int>): Int {
            val currentState = states[profile.serial] ?: STATE_UNKNOWN
            if (currentState != STATE_UNKNOWN) return currentState
            return if (!profile.quiet) STATE_ENABLED else STATE_DISABLED
        }
    }

    private var mCurrentState = emptyMap<Long, Int>()

    /** The quiet flag each profile had when its TRANSITION began: the TRANSITION ends when the flag differs (the quiet state arrived). */
    private var mQuietMode = emptyMap<Long, Boolean>()

    /** Sets current state for the profile. Answers every profile's state. */
    @Synchronized
    fun setCurrentState(profile: ProfileInfo, state: Int): Map<Long, Int> {
        mCurrentState = mCurrentState + (profile.serial to state)
        mQuietMode = mQuietMode + (profile.serial to profile.quiet)
        return mCurrentState
    }

    /** Returns current state for the profile. */
    @Synchronized
    fun getCurrentState(serial: Long): Int = mCurrentState[serial] ?: STATE_UNKNOWN

    /** Returns if user profile is enabled. */
    fun isEnabled(serial: Long): Boolean = getCurrentState(serial) == STATE_ENABLED

    /**
     * Resets the current state of the work profile and the private space from the new load. A TRANSITION stays while the profile's quiet flag is
     * still the one the tap started from, unless [force] (Android refused, or the panels are back at rest: a credential prompt that was
     * cancelled sends no event). Answers every profile's state.
     */
    @Synchronized
    fun reset(profiles: List<ProfileInfo>, force: Boolean = false): Map<Long, Int> {
        mCurrentState = profiles.filter { it.kind == ProfileKind.WORK || it.kind == ProfileKind.PRIVATE }.associate { profile ->
            val previousState = getCurrentState(profile.serial)
            val isEnabled = !profile.quiet
            val updatedState = if (isEnabled) STATE_ENABLED else STATE_DISABLED
            val transitioning = previousState == STATE_TRANSITION && mQuietMode[profile.serial] == profile.quiet
            profile.serial to if (transitioning && !force) STATE_TRANSITION else updatedState
        }
        return mCurrentState
    }
}

/**
 * Tells when a profile is added, removed, paused or locked, from the start to the stop of the screen only (nothing
 * listens in the background). After UserCache's receiver
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/pm/UserCache.java):
 * the same list of actions, [onChange] where UserCache.onUsersChanged refreshes its cache.
 */
class ProfileEvents(private val context: Context, private val onChange: () -> Unit) {

    private var receiver: BroadcastReceiver? = null

    fun start() {
        if (receiver != null) return
        val created = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onChange()
        }
        val filter = IntentFilter().apply { Profiles.actions(Build.VERSION.SDK_INT).forEach(::addAction) }
        // System broadcasts: a receiver that is not exported still gets them.
        ContextCompat.registerReceiver(context, created, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = created
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }
}

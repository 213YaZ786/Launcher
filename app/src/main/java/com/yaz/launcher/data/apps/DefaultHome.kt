package com.yaz.launcher.data.apps

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

/** Whether this app is the phone's home app, and the request that makes it so. */
object DefaultHome {

    /** Whether Android's home role is this app's. RoleManager.isRoleHeld. */
    fun held(context: Context): Boolean {
        val roles = context.getSystemService(RoleManager::class.java) ?: return false
        return roles.isRoleAvailable(RoleManager.ROLE_HOME) && roles.isRoleHeld(RoleManager.ROLE_HOME)
    }

    /**
     * The intent of Android's consent screen for the home role, or null when the role is not available or already
     * held. After ApiWrapper.assignDefaultHomeRole
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/util/ApiWrapper.java):
     * `isRoleAvailable && !isRoleHeld`, then `createRequestRoleIntent(ROLE_HOME)`. The caller starts it for a result
     * (Trebuchet calls startActivityForResult with REQUEST_HOME_ROLE).
     */
    fun requestIntent(context: Context): Intent? {
        val roles = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roles.isRoleAvailable(RoleManager.ROLE_HOME) || roles.isRoleHeld(RoleManager.ROLE_HOME)) return null
        return roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }
}

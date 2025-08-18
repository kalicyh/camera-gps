package com.saschl.cameragps.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object PreferencesManager {
    private const val PREFS_NAME = "camera_gps_prefs"
    private const val KEY_FIRST_LAUNCH = "is_first_launch"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isFirstLaunch(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchCompleted(context: Context) {
        getPreferences(context).edit {
            putBoolean(KEY_FIRST_LAUNCH, false)
        }
    }
}

package com.example.sentriai.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Single source of truth for where the profile form persists its values, so the
 * form and the navigation graph can't drift apart on prefs/key names.
 */
object ProfileStore {
    private const val PREFS_NAME = "guardian_ai_profile_prefs"

    const val KEY_FULL_NAME = "full_name"
    const val KEY_MOBILE_NUMBER = "mobile_number"
    const val KEY_HANDLER_NAME = "handler_name"
    const val KEY_HANDLER_MOBILE = "handler_mobile"

    private val REQUIRED_KEYS = listOf(
        KEY_FULL_NAME,
        KEY_MOBILE_NUMBER,
        KEY_HANDLER_NAME,
        KEY_HANDLER_MOBILE,
    )

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * True once every required field has been saved. Only a completed save writes all
     * four, so this doubles as "the user has finished setup at least once".
     */
    fun isProfileComplete(context: Context): Boolean {
        val prefs = preferences(context)
        return REQUIRED_KEYS.all { !prefs.getString(it, "").isNullOrBlank() }
    }
}

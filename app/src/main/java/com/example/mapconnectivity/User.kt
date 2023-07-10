package com.example.mapconnectivity

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.UUID

object User {
    private const val PREFS_KEY_USER_ID = "user_id"

    fun getUserId(context: Context): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        var userId = sharedPreferences.getString(PREFS_KEY_USER_ID, null)

        if (userId == null) {
            userId = generateUserId()
            sharedPreferences.edit().putString(PREFS_KEY_USER_ID, userId).apply()
        }

        return userId
    }

    private fun generateUserId(): String {
        return UUID.randomUUID().toString()
    }
}
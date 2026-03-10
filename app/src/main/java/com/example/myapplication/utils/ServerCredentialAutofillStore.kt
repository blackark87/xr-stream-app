package com.example.myapplication.utils

import android.content.Context

/**
 * Lightweight app-managed credential autofill fallback when platform autofill is unavailable.
 */
object ServerCredentialAutofillStore {

    private const val PREFS_NAME = "server_autofill"
    private const val KEY_USERNAME = "last_username"
    private const val KEY_PASSWORD = "last_password"

    fun load(context: Context): Pair<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val username = prefs.getString(KEY_USERNAME, "").orEmpty()
        val password = prefs.getString(KEY_PASSWORD, "").orEmpty()
        return username to password
    }

    fun save(context: Context, username: String, password: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }
}

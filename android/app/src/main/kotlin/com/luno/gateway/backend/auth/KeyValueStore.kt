package com.luno.gateway.backend.auth

import android.content.SharedPreferences

/** A tiny string store so [DeviceCredentialStore] is unit-testable off-device. */
interface KeyValueStore {
    fun getString(key: String): String?

    fun putString(key: String, value: String)

    fun remove(key: String)

    fun contains(key: String): Boolean

    fun clear()
}

class SharedPrefsStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun contains(key: String): Boolean = prefs.contains(key)

    override fun clear() {
        prefs.edit().clear().apply()
    }
}

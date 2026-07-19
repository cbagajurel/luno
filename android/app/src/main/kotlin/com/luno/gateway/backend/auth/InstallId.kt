package com.luno.gateway.backend.auth

import java.util.UUID

/**
 * A random per-install identifier sent with every enrollment. It survives
 * unpairing so a backend whose policy allows device replacement can recognise a
 * returning install, and it is generated locally rather than derived from
 * hardware ids (ANDROID_ID, IMEI) which are privacy-sensitive, permission-gated
 * and not stable across the API levels we support.
 */
object InstallId {
    private const val KEY = "install_id"

    fun getOrCreate(store: KeyValueStore): String =
        store.getString(KEY) ?: UUID.randomUUID().toString().also { store.putString(KEY, it) }
}

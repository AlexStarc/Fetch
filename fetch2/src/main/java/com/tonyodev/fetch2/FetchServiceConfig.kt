package com.tonyodev.fetch2

import android.content.Context

@Suppress("MemberVisibilityCanPrivate")
/**
 * Class to hold configuration for {@see FetchService}
 */
class FetchServiceConfig(private var context: Context) {
    var notificationEnabled = true
    var notificationTitleResId = R.string.app_name
    var notificationChannelTitle: String
    var notificationChannelDescription: String
    var notificationChannelId: String
    var notificationSmallIconResId: Int
    var network = NetworkType.ALL

    init {
        val prefs = context.getSharedPreferences(CONFIG_PREFS_NAME, Context.MODE_PRIVATE)
        notificationEnabled = prefs.getBoolean(KEY_NOTIFICATION_ENABLED, true)
        notificationTitleResId = prefs.getInt(KEY_NOTIFICATION_TITLE_RES_ID, R.string.app_name)
        network = NetworkType.valueOf(prefs.getString(KEY_NETWORK, NetworkType.ALL.name))
        notificationChannelTitle = prefs.getString(KEY_NOTIFICATION_CHANNEL_TITLE,
                context.getString(R.string.app_name))
        notificationChannelDescription = prefs.getString(KEY_NOTIFICATION_CHANNEL_DESCRIPTION,
                context.getString(R.string.channel_description))
        notificationSmallIconResId = prefs.getInt(KEY_NOTIFICATION_SMALL_ICON_RES_ID, R.drawable.ic_notification)
        notificationChannelId = ""
    }

    @SuppressLint("ApplySharedPref")
    @Suppress("unused")
    @Synchronized
    fun flash(force: Boolean = false) {
        val prefsEditor = context.getSharedPreferences(CONFIG_PREFS_NAME, Context.MODE_PRIVATE).edit()

        prefsEditor.putBoolean(KEY_NOTIFICATION_ENABLED, notificationEnabled)
        prefsEditor.putInt(KEY_NOTIFICATION_TITLE_RES_ID, notificationTitleResId)
        prefsEditor.putString(KEY_NETWORK, network.name)
        prefsEditor.putString(KEY_NOTIFICATION_CHANNEL_ID, notificationChannelId)
        prefsEditor.putString(KEY_NOTIFICATION_CHANNEL_TITLE, notificationChannelTitle)
        prefsEditor.putString(KEY_NOTIFICATION_CHANNEL_DESCRIPTION, notificationChannelDescription)
        prefsEditor.putInt(KEY_NOTIFICATION_SMALL_ICON_RES_ID, notificationSmallIconResId)

        if (force) {
            prefsEditor.commit()
        } else {
            prefsEditor.apply()
        }
    }

    companion object {
        private var CONFIG_PREFS_NAME = "fetch_service_config"

        /** boolean */
        private var KEY_NOTIFICATION_ENABLED = "notification.enabled"
        /** int */
        private var KEY_NOTIFICATION_TITLE_RES_ID = "notification.title_res_id"
        /** int */
        private var KEY_NOTIFICATION_SMALL_ICON_RES_ID = "notification.small_icon_res_id"
        /** String */
        private var KEY_NOTIFICATION_CHANNEL_ID = "notification.channel_id"
        /** String */
        private var KEY_NOTIFICATION_CHANNEL_TITLE = "notification.channel_title"
        /** String */
        private var KEY_NOTIFICATION_CHANNEL_DESCRIPTION = "notification.channel_description"
        /** String @see Network */
        private var KEY_NETWORK = "network"
    }
}
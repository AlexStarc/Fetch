package com.tonyodev.fetch2.provider

import android.content.Context
import android.net.ConnectivityManager
import com.tonyodev.fetch2.NetworkType
import com.tonyodev.fetch2.util.isNetworkAvailable
import com.tonyodev.fetch2.util.isOnWiFi


open class NetworkProvider constructor(private val contextInternal: Context) {

    open fun isOnAllowedNetwork(networkType: NetworkType): Boolean {
        if (networkType == NetworkType.WIFI_ONLY && contextInternal.isOnWiFi()) {
            return true
        }
        if (networkType == NetworkType.ALL && contextInternal.isNetworkAvailable()) {
            return true
        }
        return false
    }

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = contextInternal.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnected
    }
}
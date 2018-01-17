package com.tonyodev.fetch2

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ComponentName
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import com.tonyodev.fetch2.database.DownloadInfo
import com.tonyodev.fetch2.provider.NetworkProvider

/**
 * Service class to provide ability to launch downloads in the service,
 * so downloads can proceed in background with notifications
 */
@SuppressLint("LogNotTimber")
class FetchService: Service(), FetchListener {

    enum class Action {
        DOWNLOAD,
        CONNECTION_CHANGED,
        PAUSE,
        RESUME,
        RETRY,
        CANCEL,
        REMOVE,
        CANCEL_ALL,
        REMOVE_ALL
    }

    private lateinit var fetch: Fetch
    private lateinit var fetchConfig: FetchServiceConfig
    private var notificationChannel: String = ""
    private val networkProvider = NetworkProvider(this)
    private lateinit var handler: Handler

    private val runningStateQuery: Func<List<Download>> = object: Func<List<Download>> {
        override fun call(t: List<Download>) {
            if (t.isEmpty()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "No running downloads, stop self")
                stopSelf()
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "There's " + t.size + " downloads running")
                refreshNotification(t)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        handler = Handler()

        fetchConfig = FetchServiceConfig(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || fetchConfig.notificationEnabled) {
            runAsForeground()
        }

        val fetchBuilder = Fetch.Builder(this, FETCH_NAME)

        if (BuildConfig.DEBUG) {
            fetchBuilder.enableLogging(true)
        }

        fetchBuilder.setDownloadBufferSize(DOWNLOAD_BUFFER_SIZE)
                .setGlobalNetworkType(fetchConfig.network)
                .setDownloadConcurrentLimit(DOWNLOAD_CONCURRENT_LIMIT)

        fetch = fetchBuilder.build()
        fetch.addListener(this)
    }

    private fun runAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationChannel = setupNotificationChannel()
        }
        startForeground(FOREGROUND_DOWNLOAD_SERVICE_ID,
                createNotification())
    }

    private fun createNotification(): Notification {
        val b = NotificationCompat.Builder(this, notificationChannel)

        b.setOngoing(true).setContentTitle(getString(fetchConfig.notificationTitleResId))

        if (fetchConfig.notificationSmallIconResId != 0) {
            b.setSmallIcon(fetchConfig.notificationSmallIconResId)
        }

        b.setTicker(getString(R.string.notification_downloading))
        return b.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupNotificationChannel(): String {
        if (fetchConfig.notificationChannelId.isNotEmpty()) {
            return fetchConfig.notificationChannelId
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
                fetchConfig.notificationChannelTitle,
                NotificationManager.IMPORTANCE_LOW)

        channel.description = fetchConfig.notificationChannelDescription
        notificationManager.createNotificationChannel(channel)

        return NOTIFICATION_CHANNEL_ID
    }

    override fun onDestroy() {
        super.onDestroy()

        fetch.removeListener(this)
        fetch.close()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleIntent(it) }

        return START_STICKY
    }

    private fun handleIntent(intent: Intent) {
        var action: Action? = null

        try {
            val intentAction = intent.getStringExtra(ACTION_EXTRA)

            intentAction?.let { action = Action.valueOf(intentAction) }
        } catch (e: IllegalArgumentException) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to parse action " + intent.action, e)
            return
        }

        when (action!!) {
            Action.CONNECTION_CHANGED -> handleConnectionChange()
            Action.DOWNLOAD -> downloadRequest(intent)
            Action.PAUSE -> pauseRequest(intent.getIntExtra(REQUEST_ID_EXTRA, 0))
            Action.RESUME -> resumeRequest(intent.getIntExtra(REQUEST_ID_EXTRA, 0))
            Action.RETRY -> retryRequest(intent.getIntExtra(REQUEST_ID_EXTRA, 0))
            Action.REMOVE -> removeRequest(intent.getIntExtra(REQUEST_ID_EXTRA, 0))
            Action.CANCEL -> cancelRequest(intent.getIntExtra(REQUEST_ID_EXTRA, 0))
            Action.CANCEL_ALL -> {
                cancelAll()
            }
            Action.REMOVE_ALL -> {
                removeAll()
            }
        }
    }

    private fun removeAll() {
        fetch.deleteAll()
    }

    private fun cancelAll() {
        fetch.cancelAll()
    }

    private fun cancelRequest(requestId: Int) {
        if (requestId > 0) {
            fetch.cancel(requestId)
        }
    }

    private fun removeRequest(requestId: Int) {
        if (requestId > 0) {
            fetch.remove(requestId)
        }
    }

    private fun retryRequest(requestId: Int) {
        if (requestId > 0) {
            fetch.retry(requestId)
        }
    }

    private fun pauseRequest(requestId: Int) {
        if (requestId > 0) {
            fetch.pause(requestId)
        }
    }

    private fun resumeRequest(requestId: Int) {
        if (requestId > 0) {
            fetch.resume(requestId)
        }
    }

    private fun downloadRequest(intent: Intent) {
        if (intent.hasExtra(REQUEST_EXTRA)) {
            val request: Request = intent.getParcelableExtra(REQUEST_EXTRA)

            fetch.enqueue(request, null, object : Func<Error> {
                override fun call(t: Error) {
                    broadcastResult(downloadInfoFromRequestError(request, t))
                }
            })
        } else if (intent.hasExtra(REQUEST_LIST_EXTRA)) {
            val requests: ArrayList<Request> = intent.getParcelableArrayListExtra<Request>(REQUEST_LIST_EXTRA)

            if (requests.isNotEmpty()) {
                fetch.enqueue(requests, null, object : Func<Error> {
                    override fun call(t: Error) {
                        for (request in requests) {
                            broadcastResult(downloadInfoFromRequestError(request, t))
                        }
                    }
                })
            }
        }
    }

    private fun downloadInfoFromRequestError(request: Request, error: Error): Download {
        val downloadInfo = DownloadInfo()

        downloadInfo.error = error
        downloadInfo.status = Status.FAILED
        downloadInfo.id = request.id
        downloadInfo.networkType = request.networkType
        downloadInfo.priority = request.priority
        downloadInfo.name = request.name
        downloadInfo.file = request.file

        return downloadInfo
    }

    private fun pauseAll() {
        fetch.pauseAll()
    }

    private fun resumeAll() {
        fetch.resumeAll()
    }

    private fun handleConnectionChange() {
        if (!networkProvider.isNetworkAvailable()) {
            pauseAll()
        } else {
            fetchConfig = FetchServiceConfig(this)

            if (networkProvider.isOnAllowedNetwork(fetchConfig.network)) {
                resumeAll()
            } else {
                pauseAll()
            }
        }
    }

    private fun broadcastResult(download: Download) {
        val intent = Intent(INTENT_ACTION_RESULT)

        intent.putExtra(DOWNLOADINFO_EXTRA, DownloadInfo(download))
        intent.`package` = this.packageName
        sendBroadcast(intent)
    }

    private fun broadcastQueued(download: Download) {
        val intent = Intent(INTENT_ACTION_QUERY)

        intent.putExtra(DOWNLOADINFO_EXTRA, DownloadInfo(download))
        intent.`package` = this.packageName
        sendBroadcast(intent)
    }

    private fun broadcastLocalProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
        val intent = Intent(INTENT_ACTION_PROGRESS)

        intent.putExtra(DOWNLOADINFO_EXTRA, DownloadInfo(download))
        intent.putExtra(DOWNLOAD_ETA_MS_EXTRA, etaInMilliSeconds)
        intent.putExtra(DOWNLOAD_BYTES_PER_SEC, downloadedBytesPerSecond)
        intent.`package` = this.packageName
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun refreshNotification(result: List<Download>?) {
        // Just show number of downloads or exact progress, if it's known
        val b = NotificationCompat.Builder(this, notificationChannel)

        fetchConfig = FetchServiceConfig(this)
        b.setOngoing(true).setContentTitle(getString(fetchConfig.notificationTitleResId))

        if (fetchConfig.notificationSmallIconResId != 0) {
            b.setSmallIcon(fetchConfig.notificationSmallIconResId)
        }

        if (result == null || result.isEmpty()) {
            b.setTicker(getString(R.string.notification_finish_downloading))
        } else if (result.size == 1) {
            b.setTicker(getString(R.string.notification_downloading))
        } else {
            b.setTicker(getString(R.string.notification_downloading_count, result.size))
        }

        var progress = -1

        if (result != null) {
            for (data in result) {
                if (data.total <= 0) {
                    progress = -1
                    break
                }

                progress += data.progress
            }

            if (progress >= 0) {
                progress /= result.size
            }
        }

        if (progress >= 0) {
            b.setProgress(100, progress, false)
        } else {
            b.setProgress(0, 0, true)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.notify(FOREGROUND_DOWNLOAD_SERVICE_ID, b.build())
    }

    override fun onQueued(download: Download) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Request $download queued")
        broadcastQueued(download)
    }

    override fun onCompleted(download: Download) {
        checkStopNeeded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Request completed $download")
        broadcastResult(download)
    }

    override fun onError(download: Download) {
        checkStopNeeded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Request error $download")
        broadcastResult(download)
    }

    override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
        checkStopNeeded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Request progress $download")
        broadcastLocalProgress(download, etaInMilliSeconds, downloadedBytesPerSecond)
    }

    override fun onPaused(download: Download) {
        checkStopNeeded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Request paused $download")
        broadcastResult(download)
    }

    override fun onResumed(download: Download) {
        checkStopNeeded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Request resumed $download")
        broadcastResult(download)
    }

    override fun onCancelled(download: Download) {
        checkStopNeeded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Request cancelled $download")
        broadcastResult(download)
    }

    override fun onRemoved(download: Download) {
        checkStopNeeded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Request removed $download")
        broadcastResult(download)
    }

    override fun onDeleted(download: Download) {
        checkStopNeeded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Request deleted $download")
        broadcastResult(download)
    }

    private val stopCheckRunnable: Runnable = Runnable {
        if (!fetch.isClosed) {
            fetch.getDownloadsWithStatus(arrayOf(Status.DOWNLOADING, Status.QUEUED, Status.NONE), runningStateQuery)
        }
    }

    private fun checkStopNeeded() {
        handler.removeCallbacks(stopCheckRunnable)
        handler.postDelayed(stopCheckRunnable, SHUTDOWN_WAIT_TIMEOUT)
    }

    @Suppress("unused")
    companion object {
        private const val TAG = "FetchService"

        private const val FETCH_NAME = TAG

        private const val NOTIFICATION_CHANNEL_ID = "fetchservice.general"
        private val FOREGROUND_DOWNLOAD_SERVICE_ID = TAG.hashCode()

        private const val DOWNLOAD_BUFFER_SIZE = 8192
        private const val DOWNLOAD_CONCURRENT_LIMIT = 3

        private const val SHUTDOWN_WAIT_TIMEOUT = 10000L

        const val ACTION_EXTRA = BuildConfig.APPLICATION_ID + ".action"
        const val REQUEST_EXTRA = BuildConfig.APPLICATION_ID + ".request"
        const val REQUEST_LIST_EXTRA = BuildConfig.APPLICATION_ID + ".requestList"
        const val REQUEST_ID_EXTRA = BuildConfig.APPLICATION_ID + ".requestId"

        const val INTENT_ACTION_RESULT = BuildConfig.APPLICATION_ID + ".RESULT_ACTION"
        const val INTENT_ACTION_QUERY = BuildConfig.APPLICATION_ID + ".QUERY_ACTION"
        const val INTENT_ACTION_PROGRESS = BuildConfig.APPLICATION_ID + ".PROGRESS_ACTION"
        const val DOWNLOADINFO_EXTRA = BuildConfig.APPLICATION_ID + ".downloadinfo"
        const val DOWNLOAD_ETA_MS_EXTRA = BuildConfig.APPLICATION_ID + ".download_eta_ms"
        const val DOWNLOAD_BYTES_PER_SEC = BuildConfig.APPLICATION_ID + ".download_bytes_per_sec"

        /**
         * Adds request to execution and starts it
         */
        @JvmStatic
        fun download(context: Context, request: Request) {
            val intent = createIntentWithAction(Action.DOWNLOAD)

            intent.putExtra(REQUEST_EXTRA, request)

            startService(context, intent)
        }

        /**
         * Adds list of requests to execution and starts it
         */
        @JvmStatic
        fun download(context: Context, requests: List<Request>) {
            val intent = createIntentWithAction(Action.DOWNLOAD)

            intent.putParcelableArrayListExtra(REQUEST_LIST_EXTRA, ArrayList(requests))

            startService(context, intent)
        }

        /**
         * Cancels request with supplied id
         */
        @JvmStatic
        fun cancel(context: Context, id: Int) {
            launchWithActionWithId(context, Action.CANCEL, id)
        }

        /**
         * Removes request with supplied id
         */
        @JvmStatic
        fun remove(context: Context, id: Int) {
            launchWithActionWithId(context, Action.REMOVE, id)
        }

        /**
         * Pauses request with supplied id
         */
        @JvmStatic
        fun pause(context: Context, id: Int) {
            launchWithActionWithId(context, Action.PAUSE, id)
        }

        /**
         * Resumes request with supplied id
         */
        @JvmStatic
        fun resume(context: Context, id: Int) {
            launchWithActionWithId(context, Action.RESUME, id)
        }

        /**
         * Retries request with supplied id
         */
        @JvmStatic
        fun retry(context: Context, id: Int) {
            launchWithActionWithId(context, Action.RETRY, id)
        }

        private fun launchWithActionWithId(context: Context, action: Action, id: Int) {
            val intent = createIntentWithAction(action)

            intent.putExtra(REQUEST_ID_EXTRA, id)

            startService(context, intent)
        }

        /**
         * Cancels all requests
         */
        @JvmStatic
        fun cancelAll(context: Context) {
            startService(context, createIntentWithAction(Action.CANCEL_ALL))
        }

        /**
         * Removes all requests (and cancels)
         */
        @JvmStatic
        fun removeAll(context: Context) {
            startService(context, createIntentWithAction(Action.REMOVE_ALL))
        }

        /**
         * Registers provided @see BroadcastReceiver to listen all service events
         */
        @JvmStatic
        fun registerReceiver(context: Context, receiver: BroadcastReceiver) {
            val filter = IntentFilter()

            filter.addAction(INTENT_ACTION_RESULT)
            filter.addAction(INTENT_ACTION_QUERY)
            context.registerReceiver(receiver, filter)
        }

        /**
         * Registers provided @see BroadcastReceiver to listen progress service events.
         * Events will be sent only to Local receivers
         */
        @JvmStatic
        fun registerLocalProgressReceiver(context: Context, receiver: BroadcastReceiver) {
            LocalBroadcastManager.getInstance(context)
                    .registerReceiver(receiver, IntentFilter(INTENT_ACTION_PROGRESS))
        }

        /**
         * Unregisters provided @see BroadcastReceiver to listen all service events
         */
        @JvmStatic
        fun unregisterReceiver(context: Context, receiver: BroadcastReceiver) {
            context.unregisterReceiver(receiver)
        }

        /**
         * Unregisters provided @see BroadcastReceiver which was registered to listen
         */
        @JvmStatic
        fun unregisterLocalProgressReceiver(context: Context, receiver: BroadcastReceiver) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        }

        private fun startService(context: Context, intent: Intent) {
            intent.component = ComponentName(context, FetchService::class.java)
            context.startService(intent)
        }

        private fun createIntentWithAction(action: Action): Intent {
            val intent = Intent()

            intent.putExtra(ACTION_EXTRA, action.name)
            return intent
        }
    }
}
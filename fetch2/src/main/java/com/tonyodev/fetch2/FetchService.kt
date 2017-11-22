package com.tonyodev.fetch2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.*
import android.os.Build
import android.os.IBinder
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.content.LocalBroadcastManager
import android.util.Log

/**
 * Service class to provide ability to launch downloads in the service,
 * so downloads can proceed in background with notifications
 */
class FetchService: Service(), FetchListener, Callback {
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

    private val runningStateQuery: Query<List<RequestData>> = object: Query<List<RequestData>> {
        override fun onResult(result: List<RequestData>?) {
            if (result == null || result.isEmpty()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "No running downloads, stop self")
                stopSelf()
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "There's " + result.size + " downloads running")
                refreshNotification(result)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        fetchConfig = FetchServiceConfig(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || fetchConfig.notificationEnabled) {
            runAsForeground()
        }

        fetch = Fetch.create(FETCH_NAME, this, null)
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
            Action.PAUSE -> pauseRequest(intent.getLongExtra(REQUEST_ID_EXTRA, 0L))
            Action.RESUME -> resumeRequest(intent.getLongExtra(REQUEST_ID_EXTRA, 0L))
            Action.RETRY -> retryRequest(intent.getLongExtra(REQUEST_ID_EXTRA, 0L))
            Action.REMOVE -> removeRequest(intent.getLongExtra(REQUEST_ID_EXTRA, 0L))
            Action.CANCEL -> cancelRequest(intent.getLongExtra(REQUEST_ID_EXTRA, 0L))
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

    private fun cancelRequest(requestId: Long) {
        if (requestId > 0) {
            fetch.cancel(requestId)
        }
    }

    private fun removeRequest(requestId: Long) {
        if (requestId > 0) {
            fetch.remove(requestId)
        }
    }

    private fun retryRequest(requestId: Long) {
        if (requestId > 0) {
            fetch.retry(requestId)
        }
    }

    private fun pauseRequest(requestId: Long) {
        if (requestId > 0) {
            fetch.pause(requestId)
        }
    }

    private fun resumeRequest(requestId: Long) {
        if (requestId > 0) {
            fetch.resume(requestId)
        }
    }

    private fun downloadRequest(intent: Intent) {
        if (intent.hasExtra(REQUEST_EXTRA)) {
            fetch.download(intent.getParcelableExtra<Request>(REQUEST_EXTRA), this)
        } else if (intent.hasExtra(REQUEST_LIST_EXTRA)) {
            val requests: ArrayList<Request> = intent.getParcelableArrayListExtra<Request>(REQUEST_LIST_EXTRA)

            if (requests.isNotEmpty()) {
                fetch.download(requests, this)
            }
        }
    }

    private fun pauseAll() {
        fetch.pauseAll()
    }

    private fun resumeAll() {
        fetch.resumeAll()
    }

    private fun handleConnectionChange() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            pauseAll()
        } else {
            fetchConfig = FetchServiceConfig(this)

            when (fetchConfig.network) {
                Network.ALL, Network.CELLULAR -> resumeAll()
                Network.WIFI -> {
                    if (NetworkUtils.isOnWiFi(this)) {
                        resumeAll()
                    } else {
                        pauseAll()
                    }
                }
            }
        }
    }

    private fun broadcastResult(id: Long, status: Status, progress: Int, downloadedBytes: Long, totalBytes: Long, error: Error = Error.NONE) {
        val intent = Intent(INTENT_ACTION_RESULT)

        intent.putExtra(RESULT_EXTRA, Result(id, status, progress, downloadedBytes, totalBytes, error))
        intent.`package` = this.packageName
        sendBroadcast(intent)
    }

    private fun broadcastResult(request: Request, error: Error = Error.NONE) {
        val intent = Intent(INTENT_ACTION_RESULT)

        intent.putExtra(RESULT_EXTRA, Result(request.id, Status.ERROR, 0, 0, 0, error))
        intent.`package` = this.packageName
        sendBroadcast(intent)
    }

    private fun broadcastQueued(request: Request) {
        val intent = Intent(INTENT_ACTION_QUERY)

        intent.putExtra(REQUEST_EXTRA, request)
        intent.`package` = this.packageName
        sendBroadcast(intent)
    }

    private fun broadcastLocalProgress(id: Long, status: Status, progress: Int, downloadedBytes: Long, totalBytes: Long) {
        val intent = Intent(INTENT_ACTION_PROGRESS)

        intent.putExtra(RESULT_EXTRA, Result(id, status, progress, downloadedBytes, totalBytes))
        intent.`package` = this.packageName
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun refreshNotification(result: List<RequestData>?) {
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
                if (data.totalBytes <= 0) {
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

    override fun onQueued(request: Request) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Request $request queued")
        broadcastQueued(request)
    }

    override fun onComplete(id: Long, progress: Int, downloadedBytes: Long, totalBytes: Long) {
        checkStopNeeded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Request completed $id progress:$progress")
        broadcastResult(id, Status.COMPLETED, progress, downloadedBytes, totalBytes)
    }

    override fun onAttach(fetch: Fetch) {
        // nothing to do
    }

    override fun onFailure(request: Request, error: Error) {
        checkStopNeeded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Request failed $request with $error")
        broadcastResult(request, error)
    }

    override fun onDetach(fetch: Fetch) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Listener detached")
    }

    override fun onError(id: Long, error: Error, progress: Int, downloadedBytes: Long, totalBytes: Long) {
        checkStopNeeded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Request error $id progress:$progress with $error")
        broadcastResult(id, Status.ERROR, progress, downloadedBytes, totalBytes, error)
    }

    override fun onProgress(id: Long, progress: Int, downloadedBytes: Long, totalBytes: Long) {
        checkStopNeeded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Request progress $id progress:$progress ($downloadedBytes/$totalBytes)")
        broadcastLocalProgress(id, Status.DOWNLOADING, progress, downloadedBytes, totalBytes)
    }

    override fun onPause(id: Long, progress: Int, downloadedBytes: Long, totalBytes: Long) {
        checkStopNeeded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Request paused $id progress:$progress ($downloadedBytes/$totalBytes)")
        broadcastResult(id, Status.PAUSED, progress, downloadedBytes, totalBytes)
    }

    override fun onCancelled(id: Long, progress: Int, downloadedBytes: Long, totalBytes: Long) {
        checkStopNeeded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Request cancelled $id progress:$progress ($downloadedBytes/$totalBytes)")
        broadcastResult(id, Status.CANCELLED, progress, downloadedBytes, totalBytes)
    }

    override fun onRemoved(id: Long, progress: Int, downloadedBytes: Long, totalBytes: Long) {
        checkStopNeeded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Request removed $id progress:$progress ($downloadedBytes/$totalBytes)")
        broadcastResult(id, Status.REMOVED, progress, downloadedBytes, totalBytes)
    }

    private fun checkStopNeeded() {
        fetch.queryByStatus(arrayOf(Status.DOWNLOADING, Status.QUEUED), runningStateQuery)
    }

    @Suppress("unused")
    companion object {
        private const val TAG = "FetchService"

        private const val FETCH_NAME = TAG

        private const val NOTIFICATION_CHANNEL_ID = "fetchservice.general"
        private val FOREGROUND_DOWNLOAD_SERVICE_ID = TAG.hashCode()

        const val ACTION_EXTRA = BuildConfig.APPLICATION_ID + ".action"
        const val REQUEST_EXTRA = BuildConfig.APPLICATION_ID + ".request"
        const val REQUEST_LIST_EXTRA = BuildConfig.APPLICATION_ID + ".requestList"
        const val REQUEST_ID_EXTRA = BuildConfig.APPLICATION_ID + ".requestId"

        const val INTENT_ACTION_RESULT = BuildConfig.APPLICATION_ID + ".RESULT_ACTION"
        const val INTENT_ACTION_QUERY = BuildConfig.APPLICATION_ID + ".QUERY_ACTION"
        const val INTENT_ACTION_PROGRESS = BuildConfig.APPLICATION_ID + ".PROGRESS_ACTION"
        const val RESULT_EXTRA = BuildConfig.APPLICATION_ID + ".result"

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
        fun cancel(context: Context, id: Long) {
            launchWithActionWithId(context, Action.CANCEL, id)
        }

        /**
         * Removes request with supplied id
         */
        @JvmStatic
        fun remove(context: Context, id: Long) {
            launchWithActionWithId(context, Action.REMOVE, id)
        }

        /**
         * Pauses request with supplied id
         */
        @JvmStatic
        fun pause(context: Context, id: Long) {
            launchWithActionWithId(context, Action.PAUSE, id)
        }

        /**
         * Resumes request with supplied id
         */
        @JvmStatic
        fun resume(context: Context, id: Long) {
            launchWithActionWithId(context, Action.RESUME, id)
        }

        /**
         * Retries request with supplied id
         */
        @JvmStatic
        fun retry(context: Context, id: Long) {
            launchWithActionWithId(context, Action.RETRY, id)
        }

        private fun launchWithActionWithId(context: Context, action: Action, id: Long) {
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
package com.tonyodev.fetch2

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
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
        CANCEL,
        CANCEL_ALL,
        REMOVE_ALL
    }

    lateinit var fetch: Fetch

    private val runningStateQuery: Query<List<RequestData>> = object: Query<List<RequestData>> {
        override fun onResult(result: List<RequestData>?) {
            if (result == null || result.isEmpty()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "No running downloads, stop self")
                stopSelf()
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "There's " + result.size + " downloads running")
            }

            refreshNotification(result)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        fetch = Fetch.create(FETCH_NAME, this, null)
        fetch.addListener(this)
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
        }

        when (action) {
            Action.CONNECTION_CHANGED -> handleConnectionChange()
            Action.DOWNLOAD -> downloadRequest(intent.getParcelableExtra(REQUEST_EXTRA))
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

    private fun downloadRequest(request: Request) {
        fetch.download(request, this)
    }

    private fun pauseAll() {
        fetch.pauseAll()
    }

    private fun handleConnectionChange() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            pauseAll()
        } else {
            TODO("check current setting about WiFi")
        }
    }

    private fun broadcastResult(id: Long, status: Status, progress: Int, downloadedBytes: Long, totalBytes: Long, error: Error = Error.NONE) {
        val intent = Intent(INTENT_ACTION_RESULT)

        intent.putExtra(RESULT_EXTRA, Result(id, status, progress, downloadedBytes, totalBytes, error))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastResult(request: Request, error: Error = Error.NONE) {
        val intent = Intent(INTENT_ACTION_RESULT)

        intent.putExtra(RESULT_EXTRA, Result(request.id, Status.ERROR, 0, 0, 0, error))
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun refreshNotification(result: List<RequestData>?) {
        TODO("refresh downloading notification based on status etc")
    }

    private fun refreshNotification(result: Long, progress: Int) {
        TODO("refresh progress for separate file")
    }

    override fun onQueued(request: Request) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Request $request queued")
    }

    override fun onComplete(id: Long, progress: Int, downloadedBytes: Long, totalBytes: Long) {
        checkStopNeeded()
        if (BuildConfig.DEBUG) Log.d(TAG, "Request compeleted $id progress:$progress")
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
        if (BuildConfig.DEBUG) Log.d(TAG, "Request progress $id progress:$progress ($downloadedBytes/$totalBytes)")
        refreshNotification(id, progress)
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

    companion object {
        private const val TAG = "FetchService"

        private const val FETCH_NAME = TAG

        const val ACTION_EXTRA = BuildConfig.APPLICATION_ID + ".action"
        const val REQUEST_EXTRA = BuildConfig.APPLICATION_ID + ".request"
        const val REQUEST_ID_EXTRA = BuildConfig.APPLICATION_ID + ".requestId"

        const val INTENT_ACTION_RESULT = BuildConfig.APPLICATION_ID + ".RESULT_ACTION"
        const val RESULT_EXTRA = BuildConfig.APPLICATION_ID + ".result"

        /**
         * Adds request to execution and starts it
         */
        fun download(context: Context, request: Request) {
            val intent = createIntentWithAction(Action.DOWNLOAD)

            intent.putExtra(REQUEST_EXTRA, request)

            startService(context, intent)
        }

        /**
         * Cancels request with supplied id
         */
        fun cancel(context: Context, id: Long) {
            val intent = createIntentWithAction(Action.CANCEL)

            intent.putExtra(REQUEST_ID_EXTRA, id)

            startService(context, intent)
        }

        /**
         * Cancels all requests
         */
        fun cancelAll(context: Context) {
            startService(context, createIntentWithAction(Action.CANCEL_ALL))
        }

        /**
         * Removes all requests (and cancels)
         */
        fun removeAll(context: Context) {
            startService(context, createIntentWithAction(Action.REMOVE_ALL))
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
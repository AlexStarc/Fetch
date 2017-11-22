package com.tonyodev.fetch2sample.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.tonyodev.fetch2.DownloadListener;
import com.tonyodev.fetch2.Error;
import com.tonyodev.fetch2.FetchService;
import com.tonyodev.fetch2.FetchServiceConfig;
import com.tonyodev.fetch2.Network;
import com.tonyodev.fetch2.Request;
import com.tonyodev.fetch2.Result;
import com.tonyodev.fetch2.Status;
import com.tonyodev.fetch2sample.ActionListener;
import com.tonyodev.fetch2sample.Data;
import com.tonyodev.fetch2sample.Download;
import com.tonyodev.fetch2sample.FileAdapter;
import com.tonyodev.fetch2sample.R;

public class ServiceDownloadListActivity extends AppCompatActivity implements ActionListener,
        DownloadListener {

    private RecyclerView recyclerView;
    private FileAdapter fileAdapter;

    private final BroadcastReceiver fetchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }

            final String action = intent.getAction();

            if (FetchService.INTENT_ACTION_QUERY.equals(action)) {
                Request request = intent.getParcelableExtra(FetchService.REQUEST_EXTRA);

                if (request != null) {
                    onQueued(request);
                }
            } else if (FetchService.INTENT_ACTION_RESULT.equals(action)) {
                Result result = intent.getParcelableExtra(FetchService.RESULT_EXTRA);

                if (result != null) {
                    switch (result.getStatus()) {
                        case PAUSED:
                            onPause(result.getId(), result.getProgress(), result.getDownloadedBytes(), result.getTotalBytes());
                            break;

                        case CANCELLED:
                            onCancelled(result.getId(), result.getProgress(), result.getDownloadedBytes(), result.getTotalBytes());
                            break;

                        case COMPLETED:
                            onComplete(result.getId(), result.getProgress(), result.getDownloadedBytes(), result.getTotalBytes());
                            break;

                        case REMOVED:
                            onRemoved(result.getId(), result.getProgress(), result.getDownloadedBytes(), result.getTotalBytes());
                            break;

                        case ERROR:
                            onError(result.getId(), result.getError(), result.getProgress(), result.getDownloadedBytes(), result.getTotalBytes());
                            break;

                        case DOWNLOADING:
                        case INVALID:
                        case QUEUED:
                        default:
                            break;
                    }
                }
            }
        }
    };

    private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }

            final String action = intent.getAction();

            if (FetchService.INTENT_ACTION_PROGRESS.equals(action)) {
                Result result = intent.getParcelableExtra(FetchService.RESULT_EXTRA);

                if (result != null) {
                    onProgress(result.getId(), result.getProgress(), result.getDownloadedBytes(), result.getTotalBytes());
                }
            }
        }
    };

    private void onQueued(Request request) {
        Download download = new Download();
        download.setId(request.getId());
        download.setUrl(request.getUrl());
        download.setFilePath(request.getAbsoluteFilePath());
        download.setError(Error.NONE);
        download.setProgress(0);
        download.setStatus(Status.QUEUED);

        fileAdapter.addDownload(download);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_list);
        setViews();

        FetchServiceConfig fetchServiceConfig = new FetchServiceConfig(getApplicationContext());

        fetchServiceConfig.setNetwork(Network.ALL);
        fetchServiceConfig.setNotificationChannelDescription("Test notification channel");
        fetchServiceConfig.setNotificationChannelTitle("Fetch 2 Sample");
        fetchServiceConfig.flash();

        FetchService.removeAll(getApplicationContext());
        FetchService.download(getApplicationContext(), Data.getFetchRequests());
    }

    private void setViews() {
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        fileAdapter = new FileAdapter(this);
        recyclerView.setAdapter(fileAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        FetchService.registerReceiver(getApplicationContext(), fetchReceiver);
        FetchService.registerLocalProgressReceiver(getApplicationContext(), progressReceiver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        FetchService.unregisterReceiver(getApplicationContext(), fetchReceiver);
        FetchService.unregisterLocalProgressReceiver(getApplicationContext(), progressReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FetchService.removeAll(getApplicationContext());
    }

    @Override
    public void onPauseDownload(long id) {
        FetchService.pause(getApplicationContext(), id);
    }

    @Override
    public void onResumeDownload(long id) {
        FetchService.resume(getApplicationContext(), id);
    }

    @Override
    public void onRemoveDownload(long id) {
        FetchService.remove(getApplicationContext(), id);
    }

    @Override
    public void onRetryDownload(long id) {
        FetchService.retry(getApplicationContext(), id);
    }

    @Override
    public void onComplete(long id, int progress, long downloadedBytes, long totalBytes) {
        fileAdapter.onUpdate(id, Status.COMPLETED,progress,downloadedBytes,totalBytes, Error.NONE);
    }

    @Override
    public void onError(long id, Error reason, int progress, long downloadedBytes, long totalBytes) {
        fileAdapter.onUpdate(id, Status.ERROR,progress,downloadedBytes,totalBytes,reason);
    }

    @Override
    public void onProgress(long id, int progress, long downloadedBytes, long totalBytes) {
        fileAdapter.onUpdate(id, Status.DOWNLOADING,progress,downloadedBytes,totalBytes, Error.NONE);
    }

    @Override
    public void onPause(long id, int progress, long downloadedBytes, long totalBytes) {
        fileAdapter.onUpdate(id, Status.PAUSED,progress,downloadedBytes,totalBytes, Error.NONE);
    }

    @Override
    public void onCancelled(long id, int progress, long downloadedBytes, long totalBytes) {
        fileAdapter.onUpdate(id, Status.CANCELLED,progress,downloadedBytes,totalBytes, Error.NONE);
    }

    @Override
    public void onRemoved(long id, int progress, long downloadedBytes, long totalBytes) {
        fileAdapter.onUpdate(id, Status.REMOVED,progress,downloadedBytes,totalBytes, Error.NONE);
    }
}
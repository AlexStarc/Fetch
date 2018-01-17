package com.tonyodev.fetchapp.service;

import static com.tonyodev.fetchapp.DownloadListActivity.STORAGE_PERMISSION_CODE;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;

import com.tonyodev.fetch2.Download;
import com.tonyodev.fetch2.Error;
import com.tonyodev.fetch2.FetchService;
import com.tonyodev.fetch2.FetchServiceConfig;
import com.tonyodev.fetch2.Func;
import com.tonyodev.fetch2.NetworkType;
import com.tonyodev.fetch2.Request;
import com.tonyodev.fetch2.database.DownloadInfo;
import com.tonyodev.fetchapp.ActionListener;
import com.tonyodev.fetchapp.Data;
import com.tonyodev.fetchapp.DownloadListActivity;
import com.tonyodev.fetchapp.FileAdapter;
import com.tonyodev.fetchapp.R;

import java.util.List;

public class ServiceDownloadListActivity extends AppCompatActivity implements ActionListener {

    private View mainView;
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
                DownloadInfo downloadInfo = intent.getParcelableExtra(FetchService.DOWNLOADINFO_EXTRA);

                if (downloadInfo != null) {
                    onQueued(downloadInfo);
                }
            } else if (FetchService.INTENT_ACTION_RESULT.equals(action)) {
                DownloadInfo download = intent.getParcelableExtra(FetchService.DOWNLOADINFO_EXTRA);

                if (download != null) {
                    switch (download.getStatus()) {
                        case PAUSED:
                            onPause(download);
                            break;

                        case CANCELLED:
                            onCancelled(download);
                            break;

                        case COMPLETED:
                            onComplete(download);
                            break;

                        case REMOVED:
                            onRemoved(download);
                            break;

                        case FAILED:
                            onError(download);
                            break;

                        case DOWNLOADING:
                        case QUEUED:
                        case DELETED:
                        case NONE:
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
                Download download = intent.getParcelableExtra(FetchService.DOWNLOADINFO_EXTRA);

                if (download != null) {
                    onProgress(download, intent.getLongExtra(FetchService.DOWNLOAD_ETA_MS_EXTRA, 0),
                            intent.getLongExtra(FetchService.DOWNLOAD_BYTES_PER_SEC, 0));
                }
            }
        }
    };

    private void onQueued(Download download) {
        fileAdapter.addDownload(download);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_list);
        setUpViews();

        FetchServiceConfig fetchServiceConfig = new FetchServiceConfig(getApplicationContext());

        fetchServiceConfig.setNetwork(NetworkType.ALL);
        fetchServiceConfig.setNotificationChannelDescription("Test notification channel");
        fetchServiceConfig.setNotificationChannelTitle("Fetch 2 Sample");
        fetchServiceConfig.flash();

        FetchService.removeAll(getApplicationContext());
        checkStoragePermissions();
    }

    private void setUpViews() {
        final SwitchCompat networkSwitch = findViewById(R.id.networkSwitch);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        mainView = findViewById(R.id.activity_main);

        networkSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                FetchServiceConfig fetchServiceConfig = new FetchServiceConfig(getApplicationContext());

                if (isChecked) {
                    fetchServiceConfig.setNetwork(NetworkType.WIFI_ONLY);
                } else {
                    fetchServiceConfig.setNetwork(NetworkType.ALL);
                }

                fetchServiceConfig.flash();
            }
        });
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

    public void onComplete(Download download) {
        fileAdapter.update(download, DownloadListActivity.UNKNOWN_REMAINING_TIME, DownloadListActivity.UNKNOWN_DOWNLOADED_BYTES_PER_SECOND);
    }

    public void onError(Download download) {
        fileAdapter.update(download, DownloadListActivity.UNKNOWN_REMAINING_TIME, DownloadListActivity.UNKNOWN_DOWNLOADED_BYTES_PER_SECOND);
    }

    public void onProgress(Download download, long etaMs, long bytesPerSec) {
        fileAdapter.update(download, etaMs, bytesPerSec);
    }

    public void onPause(Download download) {
        fileAdapter.update(download, DownloadListActivity.UNKNOWN_REMAINING_TIME, DownloadListActivity.UNKNOWN_DOWNLOADED_BYTES_PER_SECOND);
    }

    public void onCancelled(Download download) {
        fileAdapter.update(download, DownloadListActivity.UNKNOWN_REMAINING_TIME, DownloadListActivity.UNKNOWN_DOWNLOADED_BYTES_PER_SECOND);
    }

    public void onRemoved(Download download) {
        fileAdapter.update(download, DownloadListActivity.UNKNOWN_REMAINING_TIME, DownloadListActivity.UNKNOWN_DOWNLOADED_BYTES_PER_SECOND);
    }

    private void checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_CODE);
        } else {
            enqueueDownloads();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            enqueueDownloads();

        } else {
            Snackbar.make(mainView, R.string.permission_not_enabled, Snackbar.LENGTH_INDEFINITE)
                    .show();
        }
    }

    private void enqueueDownloads() {
        FetchService.download(getApplicationContext(), Data.getFetchRequests());
    }

    @Override
    public void onPauseDownload(int id) {
        FetchService.pause(getApplicationContext(), id);
    }

    @Override
    public void onResumeDownload(int id) {
        FetchService.resume(getApplicationContext(), id);
    }

    @Override
    public void onRemoveDownload(int id) {
        FetchService.remove(getApplicationContext(), id);
    }

    @Override
    public void onRetryDownload(int id) {
        FetchService.retry(getApplicationContext(), id);
    }
}
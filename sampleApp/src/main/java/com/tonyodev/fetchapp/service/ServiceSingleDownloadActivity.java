package com.tonyodev.fetchapp.service;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.tonyodev.fetch2.AbstractFetchListener;
import com.tonyodev.fetch2.Download;
import com.tonyodev.fetch2.Error;
import com.tonyodev.fetch2.FetchListener;
import com.tonyodev.fetch2.FetchService;
import com.tonyodev.fetch2.FetchServiceConfig;
import com.tonyodev.fetch2.NetworkType;
import com.tonyodev.fetch2.Request;
import com.tonyodev.fetch2.Status;
import com.tonyodev.fetch2.database.DownloadInfo;
import com.tonyodev.fetchapp.Data;
import com.tonyodev.fetchapp.R;
import com.tonyodev.fetchapp.Utils;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public class ServiceSingleDownloadActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_CODE = 100;

    private View mainView;
    private TextView progressTextView;
    private TextView titleTextView;
    private TextView etaTextView;
    private TextView downloadSpeedTextView;

    private Request request;

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
                Download download = intent.getParcelableExtra(FetchService.DOWNLOADINFO_EXTRA);

                if (download != null) {
                    Log.d("ServiceSingleDownload", "Received download result " + download);

                    switch (download.getStatus()) {
                        case PAUSED:
                            listener.onPaused(download);
                            break;

                        case CANCELLED:
                            listener.onCancelled(download);
                            break;

                        case COMPLETED:
                            listener.onCompleted(download);
                            break;

                        case REMOVED:
                            listener.onRemoved(download);
                            break;

                        case FAILED:
                            listener.onError(download);
                            break;

                        case QUEUED:
                            setTitleView(download.getFile());
                            setProgressView(download.getStatus(), download.getProgress());
                            break;

                        case DOWNLOADING:
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
                    listener.onProgress(download,
                            intent.getLongExtra(FetchService.DOWNLOAD_ETA_MS_EXTRA, 0),
                            intent.getLongExtra(FetchService.DOWNLOAD_BYTES_PER_SEC, 0));
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setUpViews();

        FetchServiceConfig fetchServiceConfig = new FetchServiceConfig(getApplicationContext());

        fetchServiceConfig.setNetwork(NetworkType.ALL);
        fetchServiceConfig.setNotificationChannelDescription("Test notification channel");
        fetchServiceConfig.setNotificationChannelTitle("Fetch 2 Sample");
        fetchServiceConfig.flash();
    }

    private void setUpViews() {
        setContentView(R.layout.activity_single_download);
        mainView = findViewById(R.id.activity_single_download);
        progressTextView = findViewById(R.id.progressTextView);
        titleTextView = findViewById(R.id.titleTextView);
        etaTextView = findViewById(R.id.etaTextView);
        downloadSpeedTextView = findViewById(R.id.downloadSpeedTextView);
        checkStoragePermission();
    }

    private void deleteFileIfExist() {
        File file = new File(request.getFile());

        if(file.exists()) {
            file.delete();
        }
    }

    private Request createRequest() {
        return new Request(Data.sampleUrls[1], Data.getSaveDir() + "/files/zips.json");
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

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}
                    , STORAGE_PERMISSION_CODE);
        } else {
            enqueueDownload();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        if (requestCode == STORAGE_PERMISSION_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enqueueDownload();
        } else {
            Snackbar.make(mainView, R.string.permission_not_enabled, Snackbar.LENGTH_LONG)
                    .show();
        }
    }

    private void enqueueDownload() {
        request = createRequest();
        deleteFileIfExist();

        FetchService.remove(getApplicationContext(), request.getId());
        FetchService.download(getApplicationContext(), request);
    }

    private void setTitleView(String fileName) {
        final Uri uri = Uri.parse(fileName);
        titleTextView.setText(uri.getLastPathSegment());
    }

    private void setProgressView(Status status, int progress) {
        switch (status) {
            case QUEUED: {
                progressTextView.setText(R.string.queued);
                break;
            }
            case DOWNLOADING:
            case COMPLETED: {
                if (progress == -1) {
                    progressTextView.setText(R.string.downloading);
                } else {
                    final String progressString = getResources()
                            .getString(R.string.percent_progress, progress);
                    progressTextView.setText(progressString);
                }
                break;
            }
            default: {
                progressTextView.setText(R.string.status_unknown);
                break;
            }
        }
    }

    private void showDownloadErrorSnackBar(Error error) {
        final Snackbar snackbar = Snackbar.make(mainView, "Download Failed: ErrorCode: "
                + error, Snackbar.LENGTH_INDEFINITE);

        snackbar.setAction(R.string.retry, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (request != null) {
                    FetchService.retry(getApplicationContext(), request.getId());
                    snackbar.dismiss();
                }
            }
        });

        snackbar.show();
    }

    private void onQueued(Download download) {
        if (request != null && request.getId() == download.getId()) {
            setProgressView(download.getStatus(), download.getProgress());
            etaTextView.setText(Utils
                    .getETAString(ServiceSingleDownloadActivity.this, 0));
            downloadSpeedTextView.setText(Utils.getDownloadSpeedString(ServiceSingleDownloadActivity.this, 0));
        }
    }

    private final FetchListener listener = new AbstractFetchListener() {
        @Override
        public void onCompleted(@NotNull Download download) {
            if(request.getId() == download.getId()) {
                setProgressView(download.getStatus(), download.getProgress());
                etaTextView.setText(Utils
                        .getETAString(ServiceSingleDownloadActivity.this, 0));
                downloadSpeedTextView.setText(Utils.getDownloadSpeedString(ServiceSingleDownloadActivity.this, 0));
            }
        }

        @Override
        public void onError(@NotNull Download download) {
            if(request.getId() == download.getId()) {
                showDownloadErrorSnackBar(download.getError());
                etaTextView.setText(Utils
                        .getETAString(ServiceSingleDownloadActivity.this, 0));
                downloadSpeedTextView.setText(Utils.getDownloadSpeedString(ServiceSingleDownloadActivity.this, 0));
            }
        }

        @Override
        public void onProgress(@NotNull Download download, long etaInMilliSeconds,
                long downloadedBytesPerSecond) {
            if(request.getId() == download.getId()) {
                setProgressView(download.getStatus(), download.getProgress());
                etaTextView.setText(Utils
                        .getETAString(ServiceSingleDownloadActivity.this, etaInMilliSeconds));
                downloadSpeedTextView.setText(Utils.getDownloadSpeedString(ServiceSingleDownloadActivity.this, downloadedBytesPerSecond));
            }
        }

        @Override
        public void onResumed(@NotNull Download download) {
            if (request != null && request.getId() == download.getId()) {
                setProgressView(download.getStatus(), download.getProgress());
                etaTextView.setText(Utils
                        .getETAString(ServiceSingleDownloadActivity.this, 0));
                downloadSpeedTextView.setText(Utils.getDownloadSpeedString(ServiceSingleDownloadActivity.this, 0));
            }
        }

        @Override
        public void onCancelled(@NotNull Download download) {
            if (request != null && request.getId() == download.getId()) {
                setProgressView(download.getStatus(), download.getProgress());
                etaTextView.setText(Utils
                        .getETAString(ServiceSingleDownloadActivity.this, 0));
                downloadSpeedTextView.setText(Utils.getDownloadSpeedString(ServiceSingleDownloadActivity.this, 0));
            }
        }

        @Override
        public void onRemoved(@NotNull Download download) {
            if (request != null && request.getId() == download.getId()) {
                setProgressView(download.getStatus(), download.getProgress());
                etaTextView.setText(Utils
                        .getETAString(ServiceSingleDownloadActivity.this, 0));
                downloadSpeedTextView.setText(Utils.getDownloadSpeedString(ServiceSingleDownloadActivity.this, 0));
            }
        }

        @Override
        public void onDeleted(@NotNull Download download) {
            if (request != null && request.getId() == download.getId()) {
                setProgressView(download.getStatus(), download.getProgress());
                etaTextView.setText(Utils
                        .getETAString(ServiceSingleDownloadActivity.this, 0));
                downloadSpeedTextView.setText(Utils.getDownloadSpeedString(ServiceSingleDownloadActivity.this, 0));
            }
        }
    };
}
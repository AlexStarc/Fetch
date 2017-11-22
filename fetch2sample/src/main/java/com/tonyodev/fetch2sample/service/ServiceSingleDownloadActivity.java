package com.tonyodev.fetch2sample.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.tonyodev.fetch2.AbstractFetchListener;
import com.tonyodev.fetch2.Error;
import com.tonyodev.fetch2.Fetch;
import com.tonyodev.fetch2.FetchListener;
import com.tonyodev.fetch2.FetchService;
import com.tonyodev.fetch2.FetchServiceConfig;
import com.tonyodev.fetch2.Network;
import com.tonyodev.fetch2.Request;
import com.tonyodev.fetch2.Result;
import com.tonyodev.fetch2sample.Data;
import com.tonyodev.fetch2sample.R;

import java.io.File;

public class ServiceSingleDownloadActivity extends AppCompatActivity {
    private TextView progressTextView;
    private TextView titleTextView;

    private Request request;

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
                            listener.onPause(result.getId(), result.getProgress(), result.getDownloadedBytes(), result.getTotalBytes());
                            break;

                        case CANCELLED:
                            listener.onCancelled(result.getId(), result.getProgress(), result.getDownloadedBytes(), result.getTotalBytes());
                            break;

                        case COMPLETED:
                            listener.onComplete(result.getId(), result.getProgress(), result.getDownloadedBytes(), result.getTotalBytes());
                            break;

                        case REMOVED:
                            listener.onRemoved(result.getId(), result.getProgress(), result.getDownloadedBytes(), result.getTotalBytes());
                            break;

                        case ERROR:
                            listener.onError(result.getId(), result.getError(), result.getProgress(), result.getDownloadedBytes(), result.getTotalBytes());
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
                    listener.onProgress(result.getId(), result.getProgress(), result.getDownloadedBytes(), result.getTotalBytes());
                }
            }
        }
    };

    private void onQueued(Request request) {
        Log.d("onQueued",request.toString());
        setTitleView(request.getAbsoluteFilePath());
        setDownloadProgressView(0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setUpViews();

        FetchServiceConfig fetchServiceConfig = new FetchServiceConfig(getApplicationContext());

        fetchServiceConfig.setNetwork(Network.ALL);
        fetchServiceConfig.setNotificationChannelDescription("Test notification channel");
        fetchServiceConfig.setNotificationChannelTitle("Fetch 2 Sample");
        fetchServiceConfig.flash();

        request = createRequest();
        deleteFileIfExist();

        FetchService.remove(getApplicationContext(), request.getId());
        FetchService.download(getApplicationContext(), request);
    }

    private void setUpViews() {
        setContentView(R.layout.activity_single_download);
        progressTextView = findViewById(R.id.progressTextView);
        titleTextView = findViewById(R.id.titleTextView);
    }

    private void deleteFileIfExist() {
        File file = new File(request.getAbsoluteFilePath());

        if(file.exists()) {
            file.delete();
        }
    }

    private void setTitleView(String fileName) {
        Uri uri = Uri.parse(fileName);
        titleTextView.setText(uri.getLastPathSegment());
    }

    private void setDownloadProgressView(int progress) {
        String progressString = getResources()
                .getString(R.string.percent_progress,progress);

        progressTextView.setText(progressString);
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

    private final FetchListener listener = new AbstractFetchListener() {
        @Override
        public void onComplete(long id, int progress, long downloadedBytes, long totalBytes) {
            if(request.getId() == id) {
                setDownloadProgressView(progress);
                Toast.makeText(ServiceSingleDownloadActivity.this,"Download Completed",Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onError(long id, Error reason, int progress, long downloadedBytes, long totalBytes) {
            if(request.getId() == id) {
                progressTextView.setText("Enqueue Request: " + request.toString() + "\nFailed: Error:" + reason);
            }
        }

        @Override
        public void onProgress(long id, int progress, long downloadedBytes, long totalBytes) {
            if(request.getId() == id) {
                setDownloadProgressView(progress);
                Log.d("onProgress",progress+"%");
            }
        }

        @Override
        public void onAttach(Fetch fetch) {
            // this handled in onQueued()
        }
    };
}
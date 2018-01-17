package com.tonyodev.fetchapp.service;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.util.ArrayMap;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.tonyodev.fetch2.Download;
import com.tonyodev.fetch2.FetchService;
import com.tonyodev.fetch2.Request;
import com.tonyodev.fetch2.database.DownloadInfo;
import com.tonyodev.fetchapp.Data;
import com.tonyodev.fetchapp.GameFilesActivity;
import com.tonyodev.fetchapp.R;

import java.util.List;
import java.util.Set;

import timber.log.Timber;

public class ServiceGameFilesActivity extends AppCompatActivity {

    private TextView progressTextView;
    private ProgressBar progressBar;
    private Button startButton;
    private TextView labelTextView;

    private final ArrayMap<Integer, Integer> fileProgressMap = new ArrayMap<>();

    private List<Request> requestList;
    private int completed = 0;
    private int removeCount = 0;

    private final BroadcastReceiver fetchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }

            final String action = intent.getAction();

            if (FetchService.INTENT_ACTION_QUERY.equals(action)) {
                DownloadInfo download = intent.getParcelableExtra(FetchService.DOWNLOADINFO_EXTRA);

                if (download != null) {
                    Timber.d(download.toString());
                    fileProgressMap.put(download.getId(), 0);
                }
            } else if (FetchService.INTENT_ACTION_RESULT.equals(action)) {
                Download download = intent.getParcelableExtra(FetchService.DOWNLOADINFO_EXTRA);

                if (download != null) {
                    Timber.d("Received download result %s", download);

                    switch (download.getStatus()) {
                        case COMPLETED:
                            completed++;
                            fileProgressMap.put(download.getId(), download.getProgress());
                            updateUI();
                            break;

                        case REMOVED:
                            if (++removeCount == requestList.size()) {
                                updateUI();
                                removeCount = 0;
                                completed = 0;
                            }
                            break;

                        case FAILED:
                            reset();
                            Toast.makeText(ServiceGameFilesActivity.this, R.string.game_download_error,
                                    Toast.LENGTH_SHORT).show();
                            break;

                        case CANCELLED:
                        case DOWNLOADING:
                        case DELETED:
                        case QUEUED:
                        case PAUSED:
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
                    onProgress(download,
                            intent.getLongExtra(FetchService.DOWNLOAD_ETA_MS_EXTRA, 0),
                            intent.getLongExtra(FetchService.DOWNLOAD_BYTES_PER_SEC, 0));
                }
            }
        }
    };

    private void onProgress(Download download, long eta, long speed) {
        fileProgressMap.put(download.getId(), download.getProgress());
        updateUI();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_files);
        setupViews();

        requestList = Data.getGameUpdates();
    }

    private void setupViews() {
        progressTextView = findViewById(R.id.progressTextView);
        progressBar = findViewById(R.id.progressBar);
        startButton = findViewById(R.id.startButton);
        labelTextView = findViewById(R.id.labelTextView);

        //Start downloads
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String label = (String) startButton.getText();
                final Context context = ServiceGameFilesActivity.this;
                if(label.equals(context.getString(R.string.reset))) {
                    reset();
                }else {
                    startButton.setVisibility(View.GONE);
                    labelTextView.setText(R.string.fetch_started);
                    checkStoragePermission();
                }
            }
        });
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    GameFilesActivity.STORAGE_PERMISSION_CODE);
        } else {
            enqueueFiles();
        }
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == GameFilesActivity.STORAGE_PERMISSION_CODE || grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enqueueFiles();
        } else {
            Toast.makeText(this, R.string.permission_not_enabled, Toast.LENGTH_SHORT)
                    .show();
            reset();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FetchService.removeAll(getApplicationContext());
    }

    private void updateUI() {
        final int totalFiles = requestList.size();
        final int completedFiles = completed;

        progressTextView.setText(getResources()
                .getString(R.string.complete_over,completedFiles,totalFiles));

        final int progress = getDownloadProgress();

        progressBar.setProgress(progress);

        if (completedFiles == totalFiles) {
            labelTextView.setText(getString(R.string.fetch_done));
            startButton.setText(R.string.reset);
            startButton.setVisibility(View.VISIBLE);

            FetchService.removeAll(getApplicationContext());
        }
    }

    private int getDownloadProgress() {
        int currentProgress = 0;
        final int totalProgress = fileProgressMap.size() * 100;

        final Set<Integer> ids = fileProgressMap.keySet();

        for (int id : ids) {
            currentProgress += fileProgressMap.get(id);
        }

        currentProgress = (int) (((double) currentProgress / (double) totalProgress) * 100);

        return currentProgress;
    }

    private void reset() {
        startButton.setEnabled(true);
        completed = 0;
        removeCount = 0;
        fileProgressMap.clear();
        progressBar.setProgress(0);
        progressTextView.setText("");
        labelTextView.setText(R.string.start_fetching);
        startButton.setText(R.string.start);
        startButton.setVisibility(View.VISIBLE);
    }

    private void enqueueFiles() {
        FetchService.download(getApplicationContext(), requestList);
    }
}

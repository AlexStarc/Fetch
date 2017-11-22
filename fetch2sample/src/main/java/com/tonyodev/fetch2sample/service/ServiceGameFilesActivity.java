package com.tonyodev.fetch2sample.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.tonyodev.fetch2.FetchService;
import com.tonyodev.fetch2.Request;
import com.tonyodev.fetch2.Result;
import com.tonyodev.fetch2sample.Data;
import com.tonyodev.fetch2sample.R;

import java.util.List;

public class ServiceGameFilesActivity extends AppCompatActivity {

    private TextView progressTextView;
    private ProgressBar progressBar;
    private Button startButton;
    private TextView labelTextView;

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
                Request request = intent.getParcelableExtra(FetchService.REQUEST_EXTRA);

                if (request != null) {
                    Log.d("onQueued", request.toString());
                }
            } else if (FetchService.INTENT_ACTION_RESULT.equals(action)) {
                Result result = intent.getParcelableExtra(FetchService.RESULT_EXTRA);

                if (result != null) {
                    switch (result.getStatus()) {
                        case COMPLETED:
                            completed++;
                            updateUI();
                            break;

                        case REMOVED:
                            if (++removeCount == requestList.size()) {
                                updateUI();
                                removeCount = 0;
                                completed = 0;
                            }
                            break;

                        case ERROR:
                            reset();
                            Toast.makeText(ServiceGameFilesActivity.this, R.string.game_download_error,
                                    Toast.LENGTH_SHORT).show();
                            break;

                        case CANCELLED:
                        case DOWNLOADING:
                        case INVALID:
                        case QUEUED:
                        case PAUSED:
                        default:
                            break;
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_files);
        setViews();

        requestList = Data.getGameUpdates();

        FetchService.registerReceiver(getApplicationContext(), fetchReceiver);
    }

    private void setViews() {

        progressTextView = findViewById(R.id.progressTextView);
        progressBar = findViewById(R.id.progressBar);
        startButton = findViewById(R.id.startButton);
        labelTextView = findViewById(R.id.labelTextView);

        //Start downloads
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String label = (String) startButton.getText();
                Context context = ServiceGameFilesActivity.this;

                if(label.equals(context.getString(R.string.reset))) {
                    reset();
                }else {
                    startButton.setEnabled(false);
                    startButton.setText("downloading...");
                    labelTextView.setText(R.string.fetch_started);
                    enqueueFiles();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FetchService.unregisterReceiver(getApplicationContext(), fetchReceiver);
        FetchService.removeAll(getApplicationContext());
    }

    private void updateUI() {

        int totalFiles = requestList.size();
        int completedFiles = completed;

        progressTextView.setText(getResources()
                .getString(R.string.complete_over,completedFiles,totalFiles));

        int progress = getDownloadProgress();

        progressBar.setProgress(progress);

        if(completedFiles == totalFiles) {
            labelTextView.setText(getString(R.string.fetch_done));
            startButton.setText(R.string.reset);
            startButton.setEnabled(true);
            startButton.setVisibility(View.VISIBLE);
        }
    }

    private int getDownloadProgress() {
        return (int) (((double) completed / (double)requestList.size()) * 100);
    }
    private void reset() {
        startButton.setEnabled(true);
        completed = 0;
        removeCount = 0;
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

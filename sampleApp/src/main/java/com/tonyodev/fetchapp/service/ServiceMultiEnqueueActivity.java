package com.tonyodev.fetchapp.service;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.tonyodev.fetch2.AbstractFetchListener;
import com.tonyodev.fetch2.Download;
import com.tonyodev.fetch2.FetchListener;
import com.tonyodev.fetch2.FetchService;
import com.tonyodev.fetch2.Request;
import com.tonyodev.fetchapp.App;
import com.tonyodev.fetchapp.Data;
import com.tonyodev.fetchapp.R;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class ServiceMultiEnqueueActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_enqueue);
        final View mainView = findViewById(R.id.activity_main);
        final List<Request> requests = new ArrayList<>();

        final String url = "https://www.notdownloadable.com/test.txt";

        final int size = 15;

        for(int x = 0; x < size; x++) {

            final String filePath = Data.getSaveDir()
                    .concat("/multiTest/")
                    .concat("file")
                    .concat(""+(x+1))
                    .concat(".txt");

            final Request request = new Request(url,filePath);
            requests.add(request);
        }

        FetchService.removeAll(getApplicationContext());
        FetchService.download(getApplicationContext(), requests);

        Snackbar.make(mainView, "Enqueued " + size + " requests. Check Logcat for " +
                "failed status", Snackbar.LENGTH_INDEFINITE)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ((App)getApplication()).getFetch().addListener(listener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ((App)getApplication()).getFetch().removeListener(listener);
    }

    private final FetchListener listener = new AbstractFetchListener() {
        @Override
        public void onProgress(@NotNull Download download, long etaInMilliSeconds,
                long downloadedBytesPerSecond) {
            Timber.i("Download id:" + download.getId() + " - progress:" + download.getProgress());
        }

        @Override
        public void onError(@NotNull Download download) {
            Timber.tag("ServiceMultiEnqueue").d(
                    "Download id:" + download.getId() + " - error:" + download.getError());
        }
    };
}

package com.goodcodeforfun.awarenessdemo;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.awareness.Awareness;
import com.google.android.gms.awareness.fence.AwarenessFence;
import com.google.android.gms.awareness.fence.DetectedActivityFence;
import com.google.android.gms.awareness.fence.FenceState;
import com.google.android.gms.awareness.fence.FenceUpdateRequest;
import com.google.android.gms.awareness.snapshot.DetectedActivityResult;
import com.google.android.gms.awareness.snapshot.WeatherResult;
import com.google.android.gms.awareness.state.Weather;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private final String TAG = getClass().getSimpleName();

    private GoogleApiClient mApiClient;
    private final String FENCE_KEY = "fence_key";
    private final String START_FENCE_KEY = "start_fence_key";
    private final String STOP_FENCE_KEY = "stop_fence_key";

    private PendingIntent mStartPendingIntent;
    private PendingIntent mStopPendingIntent;

    private FenceReceiver mFenceReceiver;

    // The intent action which will be fired when your fence is triggered.
    private final String FENCE_RECEIVER_ACTION =
            BuildConfig.APPLICATION_ID + ".FENCE_RECEIVER_ACTION";


    private static final int MY_PERMISSION_LOCATION = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                printSnapshot();
            }
        });

        Context context = this;
        mApiClient = new GoogleApiClient.Builder(context)
                .addApi(Awareness.API)
                .enableAutoManage(this, 1, null)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {
                        // Set up the PendingIntent that will be fired when the fence is triggered.
                        Intent startIntent = new Intent(FENCE_RECEIVER_ACTION);
                        startIntent.putExtra(FENCE_KEY, START_FENCE_KEY);
                        Intent stopIntent = new Intent(FENCE_RECEIVER_ACTION);
                        stopIntent.putExtra(FENCE_KEY, STOP_FENCE_KEY);
                        mStartPendingIntent =
                                PendingIntent.getBroadcast(MainActivity.this, 0, startIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                        mStopPendingIntent =
                                PendingIntent.getBroadcast(MainActivity.this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                        // The broadcast receiver that will receive intents when a fence is triggered.
                        mFenceReceiver = new FenceReceiver();
                        registerReceiver(mFenceReceiver, new IntentFilter(FENCE_RECEIVER_ACTION));
                        setupFences();
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                    }
                })
                .build();
    }

    @Override
    protected void onResume() {
        checkAndRequestWeatherPermissions();
        super.onResume();
    }

    @Override
    protected void onStop() {
        if (mFenceReceiver != null) {
            unregisterReceiver(mFenceReceiver);
        }
        super.onStop();
    }

    @Override
    protected void onPause() {
        // Unregister the fence:
        Awareness.FenceApi.updateFences(
                mApiClient,
                new FenceUpdateRequest.Builder()
                        .removeFence(STOP_FENCE_KEY)
                        .build())
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Fence was successfully unregistered.");
                        } else {
                            Log.e(TAG, "Fence could not be unregistered: " + status);
                        }
                    }
                });
        super.onPause();
    }

    private void printSnapshot() {
        if (checkAndRequestWeatherPermissions()) {
            Awareness.SnapshotApi.getDetectedActivity(mApiClient)
                    .setResultCallback(new ResultCallback<DetectedActivityResult>() {
                        @Override
                        public void onResult(@NonNull DetectedActivityResult dar) {
                            ActivityRecognitionResult arr = dar.getActivityRecognitionResult();
                            DetectedActivity probableActivity = arr.getMostProbableActivity();
                            String activityStr = probableActivity.toString();
                            Toast.makeText(getApplicationContext(), activityStr, Toast.LENGTH_LONG).show();
                        }
                    });
            getWeatherSnapshot();
        }
    }

    private void getWeatherSnapshot() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            Awareness.SnapshotApi.getWeather(mApiClient)
                    .setResultCallback(new ResultCallback<WeatherResult>() {
                        @Override
                        public void onResult(@NonNull WeatherResult weatherResult) {
                            if (!weatherResult.getStatus().isSuccess()) {
                                Log.e(TAG, "Could not get weather.");
                                return;
                            }
                            Weather weather = weatherResult.getWeather();
                            weather.getConditions();
                        }
                    });
        }
    }

    private boolean checkAndRequestWeatherPermissions() {
        if (ContextCompat.checkSelfPermission(
                MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSION_LOCATION
            );
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSION_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getWeatherSnapshot();
                } else {
                    Log.i(TAG, "Location permission denied.  Weather snapshot skipped.");
                }
            }
        }
    }

    private void setupFences() {
        AwarenessFence stillStartFence = DetectedActivityFence.starting(DetectedActivityFence.STILL);
        AwarenessFence stillStopFence = DetectedActivityFence.stopping(DetectedActivityFence.STILL);

        setupFence(START_FENCE_KEY, stillStartFence, mStartPendingIntent);
        setupFence(STOP_FENCE_KEY, stillStopFence, mStopPendingIntent);
    }

    private void setupFence(String key, AwarenessFence fence, PendingIntent intent) {
        Awareness.FenceApi.updateFences(
                mApiClient,
                new FenceUpdateRequest.Builder()
                        .addFence(key, fence, intent)
                        .build())
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Fence was successfully registered.");
                        } else {
                            Log.e(TAG, "Fence could not be registered: " + status);
                        }
                    }
                });
    }

    public class FenceReceiver extends BroadcastReceiver {

        private MediaPlayer mPlayer = null;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!TextUtils.equals(FENCE_RECEIVER_ACTION, intent.getAction())) {
                //errror
                return;
            }

            FenceState fenceState = FenceState.extract(intent);

            boolean started = false;
            String fenceStateStr = null;
            switch (fenceState.getCurrentState()) {
                case FenceState.TRUE:
                    started = true;
                    break;
                case FenceState.FALSE:
                    started = false;
                    break;
                default:
                    //
            }

            if (null == mPlayer)
                mPlayer = MediaPlayer.create(MainActivity.this, R.raw.starwars);

            try {
                if (null != mPlayer)
                    mPlayer.prepare();
            } catch (IllegalStateException | IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            if (TextUtils.equals(fenceState.getFenceKey(), START_FENCE_KEY)) {
                fenceStateStr = "NOT ACTIVE";
                mPlayer.start();
            } else if (TextUtils.equals(fenceState.getFenceKey(), STOP_FENCE_KEY)) {
                fenceStateStr = "ACTIVE";
                if (null != mPlayer) {
                    mPlayer.stop();
                    mPlayer.reset();
                    mPlayer = null;
                }
            }

            if (started)
                Toast.makeText(getApplicationContext(), fenceStateStr, Toast.LENGTH_LONG).show();
        }
    }
}
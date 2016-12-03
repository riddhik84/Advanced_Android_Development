/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private final String LOG_TAG = MyWatchFace.class.getSimpleName();

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

        private SunshineWatchFace sunshineWatchFace;

        //rkakadia wearable sync
        private static final String PATH = "/wearable";
        private static final String KEY_HIGH = "key_high";
        private static final String KEY_LOW = "key_low";
        private static final String KEY_WEATHER_ID = "key_weather_id";

        private Double mHigh;
        private Double mLow;
        private Integer weatherId;

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        boolean mAmbient;
        Calendar mCalendar;

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowUnreadCountIndicator(true)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

//            mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
//                    .addConnectionCallbacks(this)
//                    .addOnConnectionFailedListener(this)
//                    .addApi(Wearable.API)
//                    .build();
            mGoogleApiClient.connect();

            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.yellow_500));

            sunshineWatchFace = SunshineWatchFace.newInstance(MyWatchFace.this);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {

                mGoogleApiClient.connect();
                //wake up device register broadcast receiver
                registerReceiver();

            } else {
                //invisible device, unregister broadcast receiver
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    //Log.d(LOG_TAG, "rkakadia In mLowBitAmbient mode");
                }

                sunshineWatchFace.setAntiAlias(!inAmbientMode);
                sunshineWatchFace.setColor(inAmbientMode ? Color.GRAY : Color.WHITE);
                sunshineWatchFace.setShowSeconds(!isInAmbientMode());
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            Resources resources = MyWatchFace.this.getResources();

            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                Paint yellow = new Paint();
                Paint purple = new Paint();
                yellow.setColor(resources.getColor(R.color.orange_500));
                purple.setColor(resources.getColor(R.color.purple_500));
//                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                canvas.drawRect(0, 0, bounds.width(), bounds.height() / 2, yellow);
                canvas.drawRect(0, bounds.height() / 2, bounds.width(), bounds.height(), purple);

                // Draw the background image
//                Resources resources = MyWatchFace.this.getResources();
//                Drawable backgroundDrawable = resources.getDrawable(R.drawable.background_purple, null);
//                Bitmap mBackgroundBitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();
//                Paint background_image = new Paint();
//                canvas.drawBitmap(mBackgroundBitmap, 0, bounds.height() / 2, background_image);

            }
            if (mHigh == null) {
                mHigh = 0.0d;
            }
            if (mLow == null) {
                mLow = 0.0d;
            }
            if (weatherId == null) {
                weatherId = 0;
            }
            //Log.d(LOG_TAG, "rkakadia draw watchface " + "mLow: " + mLow + " mHigh: " + mHigh + " weatherId: " + weatherId);
            sunshineWatchFace.draw(canvas, bounds, isInAmbientMode(), mLow, mHigh, weatherId);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            //Log.d(LOG_TAG, "rkakadia onConnected watchface");

            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);

            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(PATH);
            PutDataRequest request = putDataMapRequest.asPutDataRequest();
            request.setUrgent();

            Wearable.DataApi.putDataItem(mGoogleApiClient, request).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                    if (!dataItemResult.getStatus().isSuccess()) {
                        //Log.d(LOG_TAG, "rkakadia Failed weather data sync from phone");
                    } else {
                        //Log.d(LOG_TAG, "rkakadia Successful weather data sync from phone " + dataItemResult.getDataItem().getUri());

                        DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItemResult.getDataItem());
                        DataMap config = dataMapItem.getDataMap();

                        mHigh = config.getDouble(KEY_HIGH);
                        mLow = config.getDouble(KEY_LOW);
                        weatherId = config.getInt(KEY_WEATHER_ID);
                        //Log.d(LOG_TAG, "rkakadia onConnected() watchface high: " + mHigh + " low: " + mLow + " weatherId: " + weatherId);
                    }
                }
            });
        }

        @Override
        public void onConnectionSuspended(int i) {
            //Log.d(LOG_TAG, "rkakadia onConnectionSuspended()");
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            //Log.d(LOG_TAG, "rkakadia onConnectionFailed()");

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            //Log.d(LOG_TAG, "rkakadia onDataChanged() start....watchface");

            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    continue;
                }

                DataItem dataItem = dataEvent.getDataItem();
                if (!dataItem.getUri().getPath().equals(
                        PATH)) {
                    continue;
                }

                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap config = dataMapItem.getDataMap();

                mHigh = config.getDouble(KEY_HIGH);
                mLow = config.getDouble(KEY_LOW);
                weatherId = config.getInt(KEY_WEATHER_ID);
                //Log.d(LOG_TAG, "rkakadia onDataChanged() watchface high: " + mHigh + " low: " + mLow + " weatherId: " + weatherId);
            }
            //Log.d(LOG_TAG, "rkakadia onDataChanged() end...watchface");
        }
    }
}

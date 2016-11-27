package com.example.android.sunshine.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by RKs on 11/27/2016.
 */


 class SunshineSyncService implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener {

    private static final String PATH = "/wearable";
    private static final String KEY_HIGH = "key_high";
    private static final String KEY_LOW = "key_low";
    private static final String KEY_ASSET = "key_asset";

    private Double mTempHigh;
    private Double mTempLow;
    private Bitmap weatherId;

    GoogleApiClient mGoogleApiClient;

    public SunshineSyncService(Context context) {

        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Wearable.DataApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    public void requestWeatherForecast() {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(
                "/sync/weather/" + System.currentTimeMillis());
        putDataMapRequest.setUrgent();
        //putDataMapRequest.getDataMap().putInt(BLANK_KEY, BLANK_VALUE);
        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);
    }


    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {

    }
}

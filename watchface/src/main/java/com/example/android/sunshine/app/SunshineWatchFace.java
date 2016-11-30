package com.example.android.sunshine.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.format.Time;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by RKs on 11/24/2016.
 */

public class SunshineWatchFace {

    private final String LOG_TAG = SunshineWatchFace.class.getSimpleName();

    private static final String TIME_FORMAT_WITHOUT_SECONDS = "%02d:%02d";
    private static final String TIME_FORMAT_WITH_SECONDS = TIME_FORMAT_WITHOUT_SECONDS + ":%02d";
    private static final String DATE_FORMAT = "EEE, MMM d yyyy";

    private Paint timePaint;
    private Paint datePaint;
    private Paint maxTempPaint;
    private Paint minTempPaint;
    private Paint tempImagePaint;

    private Time time;

    private boolean shouldShowSeconds = true;
    private Context context;

    public static SunshineWatchFace newInstance(Context context) {

        Paint timePaint = new Paint();
        timePaint.setColor(Color.WHITE);
        timePaint.setTextSize(context.getResources().getDimension(R.dimen.time_size));
        timePaint.setAntiAlias(true);

        Paint datePaint = new Paint();
        datePaint.setColor(Color.WHITE);
        datePaint.setTextSize(context.getResources().getDimension(R.dimen.date_size));
        datePaint.setAntiAlias(true);

        Paint maxTempPaint = new Paint();
        maxTempPaint.setColor(Color.WHITE);
        maxTempPaint.setTextSize(context.getResources().getDimension(R.dimen.temp_size));
        maxTempPaint.setAntiAlias(true);

        Paint minTempPaint = new Paint();
        minTempPaint.setColor(Color.WHITE);
        minTempPaint.setTextSize(context.getResources().getDimension(R.dimen.temp_size));
        minTempPaint.setAntiAlias(true);

        Paint tempImagePaint = new Paint();
        tempImagePaint.setColor(Color.WHITE);
        tempImagePaint.setTextSize(context.getResources().getDimension(R.dimen.temp_size));
        tempImagePaint.setAntiAlias(true);

        return new SunshineWatchFace(context, timePaint, datePaint, maxTempPaint, minTempPaint, tempImagePaint, new Time());
    }

    SunshineWatchFace(Context context, Paint timePaint, Paint datePaint, Paint maxTempPaint, Paint minTempPaint,
                      Paint tempImagePaint, Time time) {
        this.context = context;

        this.timePaint = timePaint;
        this.datePaint = datePaint;
        this.maxTempPaint = maxTempPaint;
        this.minTempPaint = minTempPaint;
        this.tempImagePaint = tempImagePaint;
        this.time = time;
    }

    public void draw(Canvas canvas, Rect bounds, boolean isInAmbiantMode, double tempHigh, double tempLow, int weatherId) {
        time.setToNow();

        //Time -- 15:50
        String timeText = String.format(shouldShowSeconds ? TIME_FORMAT_WITH_SECONDS :
                TIME_FORMAT_WITHOUT_SECONDS, time.hour, time.minute, time.second);
        float timeXOffset = computeXOffset(timeText, timePaint, bounds);
        float timeYOffset = computeTimeYOffset(timeText, timePaint, bounds) - bounds.height() / 4;
//        Log.d(LOG_TAG, "RK timeXOffset " + timeXOffset + " timeYOffset " + timeYOffset);
        canvas.drawText(timeText, timeXOffset, timeYOffset, timePaint);

        //Date -- FRI, JUL 14 2015
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_FORMAT);
        String formatedDate = simpleDateFormat.format(new Date()).toUpperCase();
//        Log.d(LOG_TAG, "RK formatedDate " + formatedDate);
        float dateXOffset = computeXOffset(formatedDate, datePaint, bounds);
        float dateYOffset = computeDateYOffset(formatedDate, datePaint);
//        Log.d(LOG_TAG, "RK dateXOffset " + dateXOffset + " dateYOffset " + dateYOffset);
        canvas.drawText(formatedDate, dateXOffset, timeYOffset + dateYOffset, datePaint);

        if (!isInAmbiantMode) {
            //Temperature Information
            float tempYOffset = bounds.height() - (bounds.height() / 3);

            //Center: Temp Image "IMG"
            Drawable weatherImage = null;
            Log.d(LOG_TAG, "rkakadia weatherId " + weatherId);
            if (weatherId != 0) {
                weatherImage = context.getResources().getDrawable(Utility.getArtResourceForWeatherCondition(weatherId));
            } else {
                weatherImage = context.getResources().getDrawable(R.drawable.art_clear);
            }
            Bitmap weatherImgBitmap = ((BitmapDrawable) weatherImage).getBitmap();
            float imgTempXOffset = computeImgXOffset(weatherImgBitmap, bounds);
            //float imgTempYOffset = computeImgYOffset(weatherImage, tempImagePaint);
//            Log.d(LOG_TAG, "RK imgTempXOffset " + imgTempXOffset + " tempYOffset " + tempYOffset);
            canvas.drawBitmap(weatherImgBitmap, imgTempXOffset, tempYOffset - (tempYOffset / 4) + 10.0f, tempImagePaint);


            //Left: Max Temp: 25
            //String maxTemp = "25" + "\u00B0";
            String maxTemp = (int) tempHigh + "\u00B0";
//            Log.d(LOG_TAG, "RK maxTemp " + maxTemp);
            //float maxTempXOffset = computeXOffset(maxTemp, maxTempPaint, bounds);
            float maxTempXOffset = imgTempXOffset - (imgTempXOffset / 2);
//            Log.d(LOG_TAG, "RK maxTempXOffset " + maxTempXOffset + " tempYOffset " + tempYOffset);
            canvas.drawText(maxTemp, maxTempXOffset, tempYOffset, maxTempPaint);

            //Right: Min Temp: 16
            //String minTemp = "16" + "\u00B0";
            String minTemp = (int) tempLow + "\u00B0";
//            Log.d(LOG_TAG, "RK minTemp " + minTemp);
            float minTempXOffset = computeXOffset(minTemp, minTempPaint, bounds);
//            Log.d(LOG_TAG, "RK minTempXOffset " + minTempXOffset + " tempYOffset " + tempYOffset);
            canvas.drawText(minTemp, minTempXOffset + minTempXOffset / 2, tempYOffset, minTempPaint);
        }

    }

    private float computeXOffset(String text, Paint paint, Rect watchBounds) {
        float centerX = watchBounds.exactCenterX();
        float timeLength = paint.measureText(text);
        return centerX - (timeLength / 2.0f);
    }

    private float computeImgXOffset(Bitmap bitmap, Rect watchBounds) {
        float centerX = watchBounds.exactCenterX();
        return centerX - (bitmap.getWidth() / 2);
    }

    private float computeTimeYOffset(String timeText, Paint timePaint, Rect watchBounds) {
        float centerY = watchBounds.exactCenterY();
        Rect textBounds = new Rect();
        timePaint.getTextBounds(timeText, 0, timeText.length(), textBounds);
        int textHeight = textBounds.height();
        return centerY + (textHeight / 2.0f);
    }

    private float computeDateYOffset(String dateText, Paint datePaint) {
        Rect textBounds = new Rect();
        datePaint.getTextBounds(dateText, 0, dateText.length(), textBounds);
        return textBounds.height() + 14.0f;
    }

    private float computeImgYOffset(Bitmap bitmap, Paint imgPaint, Rect watchBounds) {
        float centerY = watchBounds.exactCenterY();
        return centerY + (bitmap.getHeight() / 2) - 10.0f;
    }

    public void setAntiAlias(boolean antiAlias) {
        timePaint.setAntiAlias(antiAlias);
        datePaint.setAntiAlias(antiAlias);
    }

    public void setColor(int color) {
        timePaint.setColor(color);
        datePaint.setColor(color);
    }

    public void setShowSeconds(boolean showSeconds) {
        shouldShowSeconds = showSeconds;
    }

}

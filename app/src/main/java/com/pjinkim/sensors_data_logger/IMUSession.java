package com.pjinkim.sensors_data_logger;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.security.Key;
import java.security.KeyException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class IMUSession implements SensorEventListener {

    // properties
    private final static String LOG_TAG = IMUSession.class.getName();

    private MainActivity mContext;
    private SensorManager mSensorManager;
    private HashMap<String, Sensor> mSensors = new HashMap<>();
    private float mInitialStepCount = -1;
    private FileStreamer mFileStreamer = null;

    private AtomicBoolean mIsRecording = new AtomicBoolean(false);
    private AtomicBoolean mIsWritingFile = new AtomicBoolean(false);
    private AtomicBoolean mIsAuthenticating = new AtomicBoolean(false);

    private float[] mAcceMeasure = new float[3];
    private float[] mGyroMeasure = new float[3];
    private float[] mMagnetMeasure = new float[3];

    private float[] mAcceBias = new float[3];
    private float[] mGyroBias = new float[3];
    private float[] mMagnetBias = new float[3];

    private ArrayList<float[]> acceValues = new ArrayList<float[]>();
    private ArrayList<float[]> gyroValues = new ArrayList<float[]>();
    private ArrayList<float[]> magneValues = new ArrayList<float[]>();


    private float[][] mAcceToFeed;
    private float[][] mGyroToFeed;
    private float[][] mMagnetToFeed;

    // constructor
    public IMUSession(MainActivity context) {

        // initialize object and sensor manager
        mContext = context;
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);

        // setup and register various sensors
        mSensors.put("acce", mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
        //mSensors.put("acce_uncalib", mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED));
        mSensors.put("gyro", mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE));
        //mSensors.put("gyro_uncalib", mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED));
        mSensors.put("linacce", mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION));
        mSensors.put("gravity", mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY));
        mSensors.put("magnet", mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
        //mSensors.put("magnet_uncalib", mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED));
        mSensors.put("rv", mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR));
        //mSensors.put("game_rv", mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR));
        mSensors.put("magnetic_rv", mSensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR));
        mSensors.put("step", mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER));
        mSensors.put("pressure", mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE));
        registerSensors();
    }


    // methods
    public void registerSensors() {
        for (Sensor eachSensor : mSensors.values()) {
            mSensorManager.registerListener(this, eachSensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    public void unregisterSensors() {
        for (Sensor eachSensor : mSensors.values()) {
            mSensorManager.unregisterListener(this, eachSensor);
        }
    }

    public void startSession(String streamFolder) {

        // initialize text file streams
        if (streamFolder != null) {
            mFileStreamer = new FileStreamer(mContext, streamFolder);
            try {
                mFileStreamer.addFile("acce", "acce.txt");
                //mFileStreamer.addFile("acce_uncalib", "acce_uncalib.txt");
                mFileStreamer.addFile("gyro", "gyro.txt");
                //mFileStreamer.addFile("gyro_uncalib", "gyro_uncalib.txt");
                mFileStreamer.addFile("linacce", "linacce.txt");
                mFileStreamer.addFile("gravity", "gravity.txt");
                mFileStreamer.addFile("magnet", "magnet.txt");
                //mFileStreamer.addFile("magnet_uncalib", "magnet_uncalib.txt");
                mFileStreamer.addFile("rv", "rv.txt");
                //mFileStreamer.addFile("game_rv", "game_rv.txt");
                mFileStreamer.addFile("magnetic_rv", "magnetic_rv.txt");
                mFileStreamer.addFile("step", "step.txt");
                mFileStreamer.addFile("pressure", "pressure.txt");
                //mFileStreamer.addFile("acce_bias", "acce_bias.txt");
                //mFileStreamer.addFile("gyro_bias", "gyro_bias.txt");
                //mFileStreamer.addFile("magnet_bias", "magnet_bias.txt");
                mIsWritingFile.set(true);
            } catch (IOException e) {
                mContext.showToast("Error occurs when creating output IMU files.");
                e.printStackTrace();
            }
        }
        mIsRecording.set(true);
    }

    //Authentication method for user validation
    //Using our sensor data collected
    public void startAuthentication(){
        mFileStreamer = new FileStreamer(mContext);
        mIsRecording.set(true);
        mIsAuthenticating.set(true);
    }

    public void stopAuthentication(){
        mIsRecording.set(false);
        Log.d("stopisAuthenticating","Value of boolean: " + mIsAuthenticating.get());
        if(mIsAuthenticating.get()){
            //currently initializing array size to 260, but we can change it if necessary
            mAcceToFeed = new float[260][];
            mMagnetToFeed = new float[260][];
            mGyroToFeed = new float[260][];

            //Size difference between ArrayList and defined array to feed to svm
            //This is a safety measure to assure that the float arrays we pass
            //to the svm don't contain null values
            int accedif = acceValues.size() - mAcceToFeed.length ;
            int magnedif = magneValues.size() - mMagnetToFeed.length;
            int gyrodif = acceValues.size() - mGyroToFeed.length;

            Log.d("Differences", "accedif " + accedif + " magnedif " + magnedif + " magnedif " + gyrodif);

            for(int i = 0; i < 260; i++){

                if (accedif < 0){
                    if((i-260) == accedif){
                        mAcceToFeed[i] = new float[]{0.0f,0.0f,0.0f};
                        accedif++;
                    } else {
                        mAcceToFeed[i] = acceValues.get(i);
                    }
                } else{
                    mAcceToFeed[i] = acceValues.get(i);
                }

                if (magnedif < 0){
                    if((i-260) == magnedif){
                        mMagnetToFeed[i] = new float[]{0.0f,0.0f,0.0f};
                        magnedif++;
                    } else {
                        mMagnetToFeed[i] = magneValues.get(i);
                    }
                } else{
                    mMagnetToFeed[i] = magneValues.get(i);
                }

                if (gyrodif < 0){
                    if((i-260) == gyrodif){
                        mGyroToFeed[i] = new float[]{0.0f,0.0f,0.0f};
                        gyrodif++;
                    } else {
                        mGyroToFeed[i] = gyroValues.get(i);
                    }
                } else{
                    mGyroToFeed[i] = gyroValues.get(i);
                }

            }
        }
        //float[] temp;
        //for (int i = 0; i < 260; i++){
        //    temp = mAcceToFeed[i];
        //    Log.d("mAcceToFeedi", "i value: " + i);
        //   Log.d("mAcceToFeedlenght","Lenght: " + mAcceToFeed.length);
        //   Log.d("mAcceToFeed", "X value: " + temp[0] + "Y value: " + temp[1] + "Z value: " + temp[2] );

        //}
        mIsAuthenticating.set(false);
        mInitialStepCount = -1;
        mFileStreamer = null;
    }


    public void stopSession() {

        mIsRecording.set(false);
        if (mIsWritingFile.get()) {

            // close all recorded text files
            try {
                mFileStreamer.endFiles();
            } catch (IOException e) {
                mContext.showToast("Error occurs when finishing IMU text files.");
                e.printStackTrace();
            }

            // copy accelerometer calibration file to the streaming folder
            try {
                File acceCalibFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/acce_calib.txt");
                File outAcceCalibFile = new File(mFileStreamer.getOutputFolder() + "/acce_calib.txt");
                if (acceCalibFile.exists()) {
                    FileInputStream istr = new FileInputStream(acceCalibFile);
                    FileOutputStream ostr = new FileOutputStream(outAcceCalibFile);
                    FileChannel ichn = istr.getChannel();
                    FileChannel ochn = ostr.getChannel();
                    ichn.transferTo(0, ichn.size(), ochn);
                    istr.close();
                    ostr.close();

                    Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    scanIntent.setData(Uri.fromFile(outAcceCalibFile));
                    mContext.sendBroadcast(scanIntent);
                }
            } catch (IOException e) {
                mContext.showToast("Error occurs when copying accelerometer calibration text files.");
                e.printStackTrace();
            }

            // reset some properties
            mIsWritingFile.set(false);
            mFileStreamer = null;
        }
        mInitialStepCount = -1;
    }

    @Override
    public void onSensorChanged(final SensorEvent sensorEvent) {

        // set some variables
        float[] values = {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        boolean isFileSaved = (mIsRecording.get() && mIsWritingFile.get());
        boolean isAuthenticating = (mIsRecording.get() && mIsAuthenticating.get());

        // update each sensor measurements
        long timestamp = sensorEvent.timestamp;
        Sensor eachSensor = sensorEvent.sensor;
        try {
            switch (eachSensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    mAcceMeasure[0] = sensorEvent.values[0];
                    mAcceMeasure[1] = sensorEvent.values[1];
                    mAcceMeasure[2] = sensorEvent.values[2];
                    if (isFileSaved) {
                        Log.d("AccelerometerRecorded", "The acce reading has been recorded");
                        mFileStreamer.addRecord(timestamp, "acce", 3, sensorEvent.values);
                    }else if (isAuthenticating){
                        acceValues.add(mFileStreamer.addArray(3, sensorEvent.values));
                    }
                    break;

                case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
                    mAcceBias[0] = sensorEvent.values[3];
                    mAcceBias[1] = sensorEvent.values[4];
                    mAcceBias[2] = sensorEvent.values[5];
                    if (isFileSaved) {
                        mFileStreamer.addRecord(timestamp, "acce_uncalib", 3, sensorEvent.values);
                        mFileStreamer.addRecord(timestamp, "acce_bias", 3, mAcceBias);
                    }
                    break;

                case Sensor.TYPE_GYROSCOPE:
                    mGyroMeasure[0] = sensorEvent.values[0];
                    mGyroMeasure[1] = sensorEvent.values[1];
                    mGyroMeasure[2] = sensorEvent.values[2];
                    if (isFileSaved) {
                        mFileStreamer.addRecord(timestamp, "gyro", 3, sensorEvent.values);
                    }else if (isAuthenticating){
                        gyroValues.add(mFileStreamer.addArray(3, sensorEvent.values));
                    }
                    break;

                case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                    mGyroBias[0] = sensorEvent.values[3];
                    mGyroBias[1] = sensorEvent.values[4];
                    mGyroBias[2] = sensorEvent.values[5];
                    if (isFileSaved) {
                        mFileStreamer.addRecord(timestamp, "gyro_uncalib", 3, sensorEvent.values);
                        mFileStreamer.addRecord(timestamp, "gyro_bias", 3, mGyroBias);
                    }
                    break;

                case Sensor.TYPE_LINEAR_ACCELERATION:
                    if (isFileSaved) {
                        mFileStreamer.addRecord(timestamp, "linacce", 3, sensorEvent.values);
                    }
                    break;

                case Sensor.TYPE_GRAVITY:
                    if (isFileSaved) {
                        mFileStreamer.addRecord(timestamp, "gravity", 3, sensorEvent.values);
                    }
                    break;

                case Sensor.TYPE_MAGNETIC_FIELD:
                    mMagnetMeasure[0] = sensorEvent.values[0];
                    mMagnetMeasure[1] = sensorEvent.values[1];
                    mMagnetMeasure[2] = sensorEvent.values[2];
                    if (isFileSaved) {
                        mFileStreamer.addRecord(timestamp, "magnet", 3, sensorEvent.values);
                    }else if (isAuthenticating){
                        magneValues.add(mFileStreamer.addArray(3,sensorEvent.values));
                        Log.d("magneValues", "magneValues size: " + magneValues.size());
                    }
                    break;

                case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                    mMagnetBias[0] = sensorEvent.values[3];
                    mMagnetBias[1] = sensorEvent.values[4];
                    mMagnetBias[2] = sensorEvent.values[5];
                    if (isFileSaved) {
                        mFileStreamer.addRecord(timestamp, "magnet_uncalib", 3, sensorEvent.values);
                        mFileStreamer.addRecord(timestamp, "magnet_bias", 3, mMagnetBias);
                    }
                    break;

                case Sensor.TYPE_ROTATION_VECTOR:
                    if (isFileSaved) {
                        mFileStreamer.addRecord(timestamp, "rv", 4, sensorEvent.values);
                    }
                    break;

                case Sensor.TYPE_GAME_ROTATION_VECTOR:
                    if (isFileSaved) {
                        mFileStreamer.addRecord(timestamp, "game_rv", 4, sensorEvent.values);
                    }
                    break;

                case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR:
                    if (isFileSaved) {
                        mFileStreamer.addRecord(timestamp, "magnetic_rv", 4, sensorEvent.values);
                    }
                    break;

                case Sensor.TYPE_STEP_COUNTER:
                    if (mInitialStepCount < 0) {
                        mInitialStepCount = sensorEvent.values[0] - 1;
                    }
                    values[0] = sensorEvent.values[0] - mInitialStepCount;
                    if (isFileSaved) {
                        mFileStreamer.addRecord(timestamp, "step", 1, values);
                    }
                    break;

                case Sensor.TYPE_PRESSURE:
                    if (isFileSaved) {
                        mFileStreamer.addRecord(timestamp, "pressure", 1, sensorEvent.values);
                    }
                    break;
            }
        } catch (IOException | KeyException e) {
            Log.d(LOG_TAG, "onSensorChanged: Something is wrong.");
            e.printStackTrace();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void clearArrayList(){
        acceValues.clear();
        magneValues.clear();
        gyroValues.clear();
    }


    // getter and setter
    public boolean isRecording() {
        return mIsRecording.get();
    }

    public boolean isAuthenticating() {return mIsAuthenticating.get();}

    public float[] getAcceMeasure() {
        return mAcceMeasure;
    }

    public float[] getGyroMeasure() {
        return mGyroMeasure;
    }

    public float[] getMagnetMeasure() {
        return mMagnetMeasure;
    }

    public float[] getAcceBias() {
        return mAcceBias;
    }

    public float[] getGyroBias() {
        return mGyroBias;
    }

    public float[] getMagnetBias() {
        return mMagnetBias;
    }

    public float[][] getAcceToFeed() {
        return mAcceToFeed;
    }

    public float[][] getGyroToFeed() {
        return mGyroToFeed;
    }

    public float[][] getMagnetToFeed() {
        return mMagnetToFeed;
    }

}
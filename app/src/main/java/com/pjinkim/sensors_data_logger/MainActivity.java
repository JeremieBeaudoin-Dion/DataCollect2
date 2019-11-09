package com.pjinkim.sensors_data_logger;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;


public class MainActivity extends AppCompatActivity {

    // properties
    private final static String LOG_TAG = MainActivity.class.getName();

    private final static int REQUEST_CODE_ANDROID = 1001;
    private static String[] REQUIRED_PERMISSIONS = new String[] {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private IMUConfig mConfig = new IMUConfig();
    private IMUSession mIMUSession;
    private BatterySession mBatterySession;

    private Handler mHandler = new Handler();
    private AtomicBoolean mIsRecording = new AtomicBoolean(false);
    private PowerManager.WakeLock mWakeLock;


    private Button mStartStopButton;
    private TextView mLabelInterfaceTime;
    private EditText mUserIdentifier;
    private Timer mInterfaceTimer = new Timer();
    private int mSecondCounter = 0;

    private MediaPlayer mp;

    private Handler handler;
    private Random random;
    private Runnable runnable;

    // For python
    private Python py;

    // Android activity lifecycle states
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // initialize screen labels and buttons
        initializeViews();

        // creating media player
        mp = MediaPlayer.create(this, R.raw.ding);

        // create handler for time wait
        random = new Random();

        // setup sessions
        mIMUSession = new IMUSession(this);
        mBatterySession = new BatterySession(this);

        // battery power setting
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sensors_data_logger:wakelocktag");
        mWakeLock.acquire();


        // monitor various sensor measurements
        mLabelInterfaceTime.setText(R.string.ready_title);
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_ANDROID);
        }
        updateConfig();

        // Chaquopy: Starts python
        if (! Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
            py = Python.getInstance();
        } else {
            py = Python.getInstance();
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
    }


    @Override
    protected void onDestroy() {
        if (mIsRecording.get()) {
            stopRecording();
        }
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        mIMUSession.unregisterSensors();
        super.onDestroy();
    }


    // methods
    public void startStopRecording(View view) {


        if (!mIsRecording.get()) {

            // Get hello.py as test object -- calling
            PyObject helloModule = py.getModule("hello");
            PyObject testObject = helloModule.callAttr("Test");

            int value = testObject.get("value").toInt();
            int value2 = testObject.callAttr("returnFive").toInt();

            final CountDownTimer countDownTimer = new CountDownTimer(value * 1000, 1000) {

                public void onTick(long millisUntilFinished) {
                    mStartStopButton.setText("Starting in: " + millisUntilFinished / 1000);
                }

                public void onFinish() {
                    // mStartStopButton.setText("done!");
                    // start recording sensor measurements when button is pressed
                    startRecording();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // start interface timer on display
                            mSecondCounter = 0;
                            mInterfaceTimer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    mSecondCounter += 1;
                                    mLabelInterfaceTime.setText(interfaceIntTime(mSecondCounter));
                                }
                            }, 0, 1000);
                        }
                    });

                }
            };

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    countDownTimer.start();
                }
            });



        } else {

            // stop recording sensor measurements when button is pressed
            stopRecording();

            // stop interface timer on display
            mInterfaceTimer.cancel();
            mLabelInterfaceTime.setText(R.string.ready_title);
        }
    }


    private void startRecording() {
        mp.start();
        String userName = mUserIdentifier.getText().toString();
        mConfig.setFolderSuffix(userName);
        // output directory for text files
        String outputFolder = null;
        try {
            OutputDirectoryManager folder = new OutputDirectoryManager(mConfig.getFolderPrefix(), mConfig.getSuffix());
            outputFolder = folder.getOutputDirectory();
            mConfig.setOutputFolder(outputFolder);
        } catch (IOException e) {
            showAlertAndStop("Cannot create output folder.");
            e.printStackTrace();
        }

        // start each session
        mIMUSession.startSession(outputFolder);
        mBatterySession.startSession(outputFolder);
        mIsRecording.set(true);

        // update Start/Stop button UI
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStartStopButton.setEnabled(true);
                mStartStopButton.setText(R.string.stop_title);
            }
        });
        showToast("Recording starts!");
    }

    protected void stopRecording() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {

                // stop each session
                mIMUSession.stopSession();
                mBatterySession.stopSession();
                mIsRecording.set(false);

                // update screen UI and button
                showToast("Recording stops!");
                resetUI();
            }
        });
    }


    private static boolean hasPermissions(Context context, String... permissions) {

        // check Android hardware permissions
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }


    private void updateConfig() {
        final int MICRO_TO_SEC = 1000;
    }


    public void showAlertAndStop(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(text)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                stopRecording();
                            }
                        }).show();
            }
        });
    }


    public void showToast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void resetUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStartStopButton.setEnabled(true);
                mStartStopButton.setText(R.string.start_title);
            }
        });
    }


    @Override
    public void onBackPressed() {

        // nullify back button when recording starts
        if (!mIsRecording.get()) {
            super.onBackPressed();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode != REQUEST_CODE_ANDROID) {
            return;
        }

        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                showToast("Permission not granted");
                finish();
                return;
            }
        }
    }


    private void initializeViews() {


        mStartStopButton = (Button) findViewById(R.id.button_start_stop);
        mLabelInterfaceTime = (TextView) findViewById(R.id.label_interface_time);
        mUserIdentifier = (EditText) findViewById(R.id.user_identifier);
    }


    private String interfaceIntTime(final int second) {

        // check second input
        if (second < 0) {
            showAlertAndStop("Second cannot be negative.");
        }

        // extract hour, minute, second information from second
        int input = second;
        int hours = input / 3600;
        input = input % 3600;
        int mins = input / 60;
        int secs = input % 60;

        // return interface int time
        return String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs);
    }
}
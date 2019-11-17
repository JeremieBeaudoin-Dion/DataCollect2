package com.pjinkim.sensors_data_logger;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;
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

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;


public class MainActivity extends Activity {

    // properties
    private final static String LOG_TAG = MainActivity.class.getName();

    private final static int REQUEST_CODE_ANDROID = 1001;
    private static String[] REQUIRED_PERMISSIONS = new String[] {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private IMUConfig mConfig = new IMUConfig();
    private IMUSession mIMUSession;

    private Handler mHandler = new Handler();
    private AtomicBoolean mIsRecording = new AtomicBoolean(false);
    private PowerManager.WakeLock mWakeLock;


    private Button mStartStopButton;
    private Button mAuthenticationButton;
    private TextView mLabelInterfaceTime;
    private TextView mLabelAuthenticationResult;
    private EditText mUserIdentifier;
    private Timer mInterfaceTimer;
    private int mSecondCounter = 0;

    //Keeps track if its the same subject being tested
    //Simplifies data collection process by saving us the time of constantly updating the user field
    //For a single user. For example: While testing for ARIEL, it would be tedious to
    //Constantly be updating the field to ARIEL1, ARIEL2, ARIEL3, etc...
    private int mSessionCounter;
    private String mCurrentSubject;


    private Handler handler;
    private Random random;
    private Runnable runnable;

    // For python
    private Python py;

    //For device enabled lockscreen
    private DevicePolicyManager mgr = null;
    private ComponentName cn= null;

    //For Recording Notification
    private final String CHANNEL_ID = "recording_stop";
    private final int NOTIFICATION_ID = 001;
    private boolean isRecording = false;

    //For Authentication Notification
    private final int AUTH_NOTIFICATION_ID = 002;
    private String mDecision = "";

    // Android activity lifecycle states
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // initialize screen labels and buttons
        initializeViews();

        // create handler for time wait
        random = new Random();

        // setup sessions
        mIMUSession = new IMUSession(this);

        // battery power setting
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sensors_data_logger:wakelocktag");
        mWakeLock.acquire();


        // monitor various sensor measurements
        mLabelInterfaceTime.setText(R.string.ready_title);
        mLabelAuthenticationResult.setVisibility(View.INVISIBLE);

        //Set DevicePolicyManager
        cn = new ComponentName(this, AdminReceiver.class);
        mgr = (DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_ANDROID);
        }
        updateConfig();

        //Chaquopy: Starts python;
        //if (! Python.isStarted()) {
         //   Python.start(new AndroidPlatform(this));
           // py = Python.getInstance();
        //} else {
          //  py = Python.getInstance();
        //}

        // Test python integration of SVM
        //float[3] accell = {0.0f, 0.0f, 0.0f};
        //float[3] gyro = {0.0f, 0.0f, 0.0f};
        //float[3] magnet = {0.0f, 0.0f, 0.0f};
        //int value = validateUserWithSVM(accell, gyro, magnet);
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
        //This if branch checks if the policy manager is active.
        if(mgr.isAdminActive(cn)) {

            if (!mIsRecording.get()) {
                int value = 5;

                final CountDownTimer countDownTimer = new CountDownTimer(value * 1000, 1000) {

                    public void onTick(long millisUntilFinished) {
                        mStartStopButton.setText("Starting in: " + millisUntilFinished / 1000);
                        mgr.lockNow();
                    }

                    public void onFinish() {
                        // mStartStopButton.setText("done!");
                        // start recording sensor measurements when button is pressed
                        startRecording();

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // start interface timer on display

                                mInterfaceTimer = new Timer();
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
                mLabelAuthenticationResult.setVisibility(View.INVISIBLE);


            }
            //if device manager isn't active, it will show a prompt to the user explaining what
            //it is and what its used for.
        } else {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.permission_explanation));
            startActivity(intent);
        }
    }

    /**
     * Authenticates user from sensors
     *
     * input:   float[] array of x, y, z for accellerometer, gyrometer, magnetometer
     * output:  0 if this is not user
     *          1 if this is the user
     */
    //private int validateUserWithSVM(float[] accell, float[] gyro, float[] magnet) {
    // Get hello.py as test object -- calling
        //PyObject helloModule = py.getModule("hello");
        //PyObject svmObject = helloModule.callAttr("SVM");
        //int isUser = svmObject.callAttr("returnFive").toInt();
        //int isUser = svmObject.callAttr("validate_user", accell, gyro, magnet).toInt();

        //return isUser;
    //}

    //Dummy user validator until above method works
    //float[] accell, float gyro, float[] magnet
    private int validateUserWithSVM(){
        final int min = 0;
        final int max = 1;
        final int isUser = new Random().nextInt((max - min) + 1) + min;
        return isUser;
    }

    private void startRecording() {
        String userName = mUserIdentifier.getText().toString();
        if (mCurrentSubject != null){
            if (mCurrentSubject.equals(userName)){
                mSessionCounter += 1;
            } else {
                mSessionCounter = 0;
            }
        } else {
            mCurrentSubject = userName;
        }
        userName = userName + mSessionCounter;
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
        mIsRecording.set(true);
        isRecording = true;
        displayRecordingNotification(mCurrentSubject);

        // update Start/Stop button UI
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStartStopButton.setEnabled(true);
                mStartStopButton.setText(R.string.stop_title);
            }
        });
    }

    protected void stopRecording() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {

                // stop each session
                mIMUSession.stopSession();
                authenticate(0);
                mIsRecording.set(false);
                isRecording = false;

                // update screen UI and button
                showToast("Recording stops!");
                resetUI();
            }
        });
    }

    public void startStopAuthentication(View view) {
        //This if branch checks if the policy manager is active.
        if(mgr.isAdminActive(cn)) {

            if (!mIsRecording.get()) {
                int value = 5;

                final CountDownTimer countDownTimer = new CountDownTimer(value * 1000, 1000) {

                    public void onTick(long millisUntilFinished) {
                        mAuthenticationButton.setText("Starting in: " + millisUntilFinished / 1000);
                        mgr.lockNow();
                    }

                    public void onFinish() {
                        // mStartStopButton.setText("done!");
                        // start recording sensor measurements when button is pressed
                        startAuthentication();

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // start interface timer on display

                                mInterfaceTimer = new Timer();
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
                stopAuthentication();

                // stop interface timer on display
                mInterfaceTimer.cancel();
                mLabelInterfaceTime.setText(R.string.ready_title);
                mLabelAuthenticationResult.setVisibility(View.VISIBLE);


            }
            //if device manager isn't active, it will show a prompt to the user explaining what
            //it is and what its used for.
        } else {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.permission_explanation));
            startActivity(intent);
        }
    }





    private void startAuthentication() {


        // start each session
        mIMUSession.startAuthentication();
        mIsRecording.set(true);
        isRecording = true;
        displayAuthenticationNotification();

        // update Start/Stop button UI
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAuthenticationButton.setEnabled(true);
                mStartStopButton.setEnabled(false);
                mAuthenticationButton.setText(R.string.stop_title);
            }
        });
    }

    protected void stopAuthentication() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {

                // stop each session
                mIMUSession.stopAuthentication();


                //LOOK AT ME <----------------------------------------------------------------------------
                //Here probably want to use getter methods to get the float arrays
                //These currently have a size of 260
                float[][] mAcceToFeed = mIMUSession.getAcceToFeed();
                float[][] mGyroToFeed = mIMUSession.getGyroToFeed();
                float[][] mMagnetToFeed = mIMUSession.getMagnetToFeed();

                //Use this part in the authenticatewithsvm method once that is working
                float[] acce;
                float[] gyro;
                float[] magne;

                //Test loop to check
                for (int i = 0; i < 260; i++){
                    acce = mAcceToFeed[i];
                    gyro = mGyroToFeed[i];
                    magne = mMagnetToFeed[i];

                    Log.d("accegyromagne", "i value: " + i);
                    Log.d("accelength","Lenght: " + acce.length);
                    Log.d("accevalues", "Acce: X value: " + acce[0] + " Y value: " + acce[1] + " Z value: " + acce[2] );


                    Log.d("gyrolength","Lenght: " + acce.length);
                    Log.d("gyrovalues", "Gyro: X value: " + gyro[0] + " Y value: " + gyro[1] + " Z value: " + gyro[2] );


                    Log.d("magnelength","Lenght: " + magne.length);
                    Log.d("magnevalues", "Magne: X value: " + magne[0] + " Y value: " + magne[1] + " Z value: " + magne[2] );
                }

                //Here we would pass the array containing float[] arrays to the SVM
                //Each cell in here is an array that contains a X,Y,Z reading
                if(mDecision.equals("Authentication")){
                    authenticate(validateUserWithSVM());
                    showToast("Authentication stops!");
                }else if (mDecision.equals("Rejection")){
                    mLabelAuthenticationResult.setVisibility(View.INVISIBLE);
                    showToast("Denied authentication attempt.");
                }
                mIsRecording.set(false);
                isRecording = false;
                mIMUSession.clearArrayList();

                // update screen UI and button

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
                mAuthenticationButton.setEnabled(true);
                mStartStopButton.setText(R.string.start_title);
                mAuthenticationButton.setText(R.string.authenticate_title);


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
        mAuthenticationButton = (Button) findViewById(R.id.button_authenticate);
        mLabelInterfaceTime = (TextView) findViewById(R.id.label_interface_time);
        mLabelAuthenticationResult = (TextView) findViewById(R.id.label_authentication);
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

    //Call this to display recording notification to our user
    private void displayRecordingNotification(String userName){
        createNotificationChannel();
        //The intent will be responsible of allowing the notification
        //To route us back to the activity and stop the notification from
        //a single click
        Intent stoppingIntent = new Intent(this,MainActivity.class);
        stoppingIntent.putExtra("origin","Recording");
        PendingIntent stoppingPendingIntent = PendingIntent.getActivity(this, 0, stoppingIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action action = new Notification.Action.Builder(Icon.createWithResource(this,R.drawable.circlestop),"Stop",stoppingPendingIntent).build();
        Notification.Builder builder = new Notification.Builder(this,CHANNEL_ID);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setContentTitle("Tap the button -------------------------->");
        //Even though our directory naming starts with 0, for subject viewing clarity
        //the session counter will start at 1 for display purposes only.
        builder.setContentText("Recording session #" + (mSessionCounter + 1));
        builder.addAction(action);
        builder.setAutoCancel(true);
        builder.setContentIntent(stoppingPendingIntent);
        builder.setStyle (new Notification.MediaStyle().setShowActionsInCompactView (0));
        builder.setColor(ContextCompat.getColor(this,R.color.colorBlack));
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.notify(NOTIFICATION_ID,builder.build());
    }

    //Call this to display recording notification to our user
    private void displayAuthenticationNotification(){
        createNotificationChannel();
        //The intent will be responsible of allowing the notification
        //To route us back to the activity and stop the notification from
        //a single click
        Intent authenticatingIntent = new Intent(this,MainActivity.class);
        authenticatingIntent.putExtra("origin", "Authentication");
        Intent rejectingIntent = new Intent(this, MainActivity.class);
        rejectingIntent.putExtra("origin", "Rejection");
        PendingIntent authenticatingPendingIntent = PendingIntent.getActivity(this, 1, authenticatingIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent rejectingPendingIntent = PendingIntent.getActivity(this, 2, rejectingIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action accept = new Notification.Action.Builder(Icon.createWithResource(this,R.drawable.checkshield),"Accept",authenticatingPendingIntent).build();
        Notification.Action reject = new Notification.Action.Builder(Icon.createWithResource(this,R.drawable.xbox),"Reject",rejectingPendingIntent).build();
        Notification.Builder builder = new Notification.Builder(this,CHANNEL_ID);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setContentTitle("Hi, User");
        builder.addAction(accept);
        builder.addAction(reject);
        //Even though our directory naming starts with 0, for subject viewing clarity
        //the session counter will start at 1 for display purposes only.
        builder.setContentText("Authenticate?");
        builder.setAutoCancel(true);
        builder.setContentIntent(authenticatingPendingIntent);
        builder.setStyle (new Notification.MediaStyle().setShowActionsInCompactView (0,1));
        builder.setColor(ContextCompat.getColor(this,R.color.colorBlack));
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(AUTH_NOTIFICATION_ID,builder.build());
    }


    //Need to create a NotificationChannel for devices 8.0 and above.
    private void createNotificationChannel(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
            CharSequence name = "Stop Notification";
            String description = "Stops the data recording";
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID,name,importance);
            notificationChannel.setDescription(description);
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    //In future implementation when we authenticate user this might need modification but for the
    //current data collection purposes this fits our criteria
    protected void onNewIntent(Intent intent){
        //Extras is used in case the architecture changes and the value is passed before the notification


        Bundle extras = intent.getExtras();
        String origin;
        Log.d("onNewIntent","Entered onNewIntent");
        if (extras != null){
            origin = extras.getString("origin","default");
            Log.d("origin","origin value of: " + origin);
            if (origin.equals("Recording")){
                NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if(isRecording){
                    Log.d("RecordingOrigin","Entered Recording origin");
                    startStopRecording(mStartStopButton);
                    notificationManager.cancel(NOTIFICATION_ID);
                }
            } else if (origin.equals("Authentication")){
                mDecision = origin;
                NotificationManager notificationManager1 = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                startStopAuthentication(mAuthenticationButton);
                notificationManager1.cancel(AUTH_NOTIFICATION_ID);
            } else if (origin.equals("Rejection")){
                mDecision = origin;
                NotificationManager notificationManager2 = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                startStopAuthentication(mAuthenticationButton);
                notificationManager2.cancel(AUTH_NOTIFICATION_ID);
            }
        }
    }

    protected void authenticate(int result){
        if (result == 0){
            mLabelAuthenticationResult.setTextColor(Color.RED);
            mLabelAuthenticationResult.setText("REJECTED");
        } else if (result == 1){
            mLabelAuthenticationResult.setTextColor(Color.GREEN);
            mLabelAuthenticationResult.setText("ACCEPTED");
        }
    }

}
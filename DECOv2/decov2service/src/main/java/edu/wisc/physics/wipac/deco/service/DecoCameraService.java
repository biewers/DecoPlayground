package edu.wisc.physics.wipac.deco.service;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.IOException;

public class DecoCameraService extends Service
{
    private static final String TAG = "DecoCameraService";
    private static final int FOREGROUND_ID = 1966;

    private Camera mCamera;
    private Handler mServiceHandler;
    private PowerManager.WakeLock mWakeLock;

    @Override
    public void onCreate()
    {
        Logger.open(this, TAG + ".txt");

        Logger.d(TAG, "onCreate");

        Thread.currentThread().setUncaughtExceptionHandler(
            new Thread.UncaughtExceptionHandler()
            {
                @Override
                public void uncaughtException(Thread thread, Throwable e)
                {
                    Logger.e(TAG, "FATAL ERROR: Uncaught exception!", e);
                }
            }
        );

        HandlerThread thread = new HandlerThread("Deco Camera Service Thread");
        thread.start();
        mServiceHandler = new Handler(thread.getLooper());

        startForeground(FOREGROUND_ID, buildForegroundNotification());

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mWakeLock.acquire();
    }

    @Override
    public void onDestroy()
    {
        Logger.d(TAG, "onDestroy");

        mWakeLock.release();
        mWakeLock = null;

        stopForeground(true);

        if (mCamera != null)
        {
            mCamera.closeCameraDevice();
            mCamera = null;
        }

        Logger.close();
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Logger.d(TAG, "onStartCommand intent " + intent + " flags " + flags + " startId " + startId);

        if (mCamera == null)
        {
            try
            {
                mCamera = new Camera(this, mServiceHandler);
            }
            catch (IOException e)
            {
                Logger.e(TAG, "Failed to initialized camera", e);
                return START_STICKY;
            }

            mCamera.setCameraCaptureStateCallback(
                new CameraCaptureStateCallback()
                {
                    @Override
                    public void onCameraOpen()
                    {
                        // TODO Send a message to the app activity
                    }

                    @Override
                    public void onCameraClosed()
                    {
                        // Make sure the reference is null
                        mCamera = null;
                        // TODO Send a message to the app activity
                    }

                    @Override
                    public void onExposureSet(Long maximumExposure)
                    {
                        // TODO Send a message to the app activity
                    }

                    @Override
                    public void onImageCaptured(ImageInfo imageInfo)
                    {
                        // TODO Send a message to the app activity
                    }

                    @Override
                    public void onCaptureCompleted(Long actualExposure)
                    {
                        // TODO Send a message to the app activity
                    }
                }
            );
            mCamera.openCameraDevice();
        }

        return START_STICKY;
    }

    private Notification buildForegroundNotification()
    {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(getString(R.string.service_main_action));
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification.Builder notificationBuilder = new Notification.Builder(this);

        return notificationBuilder
            .setOngoing(true)
            .setContentTitle("DECO")
            .setContentText("Capturing images")
            .setSmallIcon(R.drawable.ic_launcher)
            .setTicker("DECO")
            .setContentIntent(pendingIntent)
            .build();
    }

    public static boolean isRunning(Context context)
    {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
        {
            if (DecoCameraService.class.getName().equals(service.service.getClassName()))
            {
                Logger.d(TAG, "isRunning true");
                return true;
            }
        }

        Logger.d(TAG, "isRunning false");
        return false;
    }
}

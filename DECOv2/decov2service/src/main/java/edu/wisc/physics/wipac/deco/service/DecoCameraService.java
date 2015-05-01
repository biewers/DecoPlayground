package edu.wisc.physics.wipac.deco.service;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

public class DecoCameraService extends Service
{
    private static final String TAG = "DecoCameraService";
    private static final int FOREGROUND_ID = 1966;

    private Messenger mServiceMessenger;
    private Messenger mActivityMessenger;

    private Notification.Builder mNotificationBuilder;
    private Camera mCamera;
    private Handler mServiceHandler;

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

        mServiceMessenger = new Messenger(new IncomingHandler());

        startForeground(FOREGROUND_ID, buildForegroundNotification());
    }

    @Override
    public void onDestroy()
    {
        Logger.d(TAG, "onDestroy");

        if (mCamera != null)
        {
            mCamera.closeCameraDevice();
            mCamera = null;
        }

        stopForeground(true);

        Logger.close();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Logger.d(TAG, "onStartCommand intent " + intent + " flags " + flags + " startId " + startId);

        if (intent != null)
        {
            mActivityMessenger = intent.getParcelableExtra("Messenger");
            Logger.d(TAG, "mActivityMessenger " + mActivityMessenger);
        }

        if (mCamera == null)
        {
            mCamera = new Camera(this, mServiceHandler);
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
                        Message msg = Message.obtain();
                        msg.getData().putParcelable("imageInfo", imageInfo);
                        try
                        {
                            if (mActivityMessenger != null)
                            {
                                mActivityMessenger.send(msg);
                            }
                        }
                        catch (RemoteException e)
                        {
                            Logger.e(TAG, "Failed to send image captured message to main activity", e);
                        }
                    }

                    @Override
                    public void onCaptureCompleted(Long actualExposure)
                    {
                        Message msg = Message.obtain();
                        msg.getData().putParcelable("imageInfo", new ImageInfo(null, null, actualExposure));
                        try
                        {
                            if (mActivityMessenger != null)
                            {
                                mActivityMessenger.send(msg);
                            }
                        }
                        catch (RemoteException e)
                        {
                            Logger.e(TAG, "Failed to send capture completed message to main activity", e);
                        }
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

        mNotificationBuilder = new Notification.Builder(this);

        return mNotificationBuilder
            .setOngoing(true)
            .setContentTitle("DECO")
            .setContentText("Capturing images")
            .setSmallIcon(R.drawable.ic_launcher)
            .setTicker("DECO")
            .setContentIntent(pendingIntent)
            .build();
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        Logger.d(TAG, "onBind intent " + intent);

        if (intent != null)
        {
            mActivityMessenger = intent.getParcelableExtra("Messenger");
            Logger.d(TAG, "mActivityMessenger " + mActivityMessenger);
        }

        return mServiceMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        Logger.d(TAG, "onUnbind intent " + intent);
        mActivityMessenger = null;
        return true;
    }

    @Override
    public void onRebind(Intent intent)
    {
        Logger.d(TAG, "onRebind intent " + intent);

        if (intent != null)
        {
            mActivityMessenger = intent.getParcelableExtra("Messenger");
            Logger.d(TAG, "mActivityMessenger " + mActivityMessenger);
        }
    }

    private class IncomingHandler extends Handler
    {
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

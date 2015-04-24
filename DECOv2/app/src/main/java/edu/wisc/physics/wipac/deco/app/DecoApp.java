package edu.wisc.physics.wipac.deco.app;

import android.app.Application;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by andrewbiewer on 4/23/15.
 */
public class DecoApp extends Application
{
    private static final String TAG = "DecoApp";
    private static PrintWriter log;
    private static SimpleDateFormat TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public void onCreate()
    {
        super.onCreate();

        try
        {
            File file = new File(Environment.getExternalStorageDirectory(), getResources().getString(R.string.app_name));
            file.mkdirs();

            log = new PrintWriter(
                new FileWriter(
                   new File(file, "log.txt"),
                   true), // append
                true); // autoFlush
        }
        catch (Exception e)
        {
            log = null;
            Log.e(TAG, "Failed to open log.txt");
        }

        log(TAG, "Application started");
    }

    @Override
    public void onTerminate()
    {
        super.onTerminate();
        log(TAG, "Application stopped");
        if (log != null)
        {
            log.close();
        }
    }

    public static void log(String tag, String message)
    {
        if (log != null)
        {
            synchronized (log)
            {
                log.write(TIMESTAMP.format(new Date()) + " " + tag + ": " + message + "\n");
                log.flush();
            }
        }
    }

    public static void log(String tag, String message, Throwable e)
    {
        if (log != null)
        {
            synchronized (log)
            {
                log.write(TIMESTAMP.format(new Date()) + " " + tag + ": " + message + "\n");
                e.printStackTrace(log);
                log.flush();
            }
        }
    }
}

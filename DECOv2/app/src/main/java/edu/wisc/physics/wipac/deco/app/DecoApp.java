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
    private static final LogLevel LOG_LEVEL_MAX = LogLevel.INFO;

    public enum LogLevel
    {
        ERROR("E"), WARNING("W"), INFO("I"), DEBUG("D");

        private String level;
        private LogLevel(String level)
        {
            this.level = level;
        }

        @Override
        public String toString()
        {
            return level;
        }
    };

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

        DecoApp.i(TAG, "Application started");
    }

    @Override
    public void onTerminate()
    {
        super.onTerminate();
        DecoApp.i(TAG, "Application stopped");
        if (log != null)
        {
            log.close();
        }
    }

    public static void log(LogLevel level, String tag, String message)
    {
        log(level, tag, message, null);
    }

    public static void log(LogLevel level, String tag, String message, Throwable e)
    {
        if (log != null && level.ordinal() <= LOG_LEVEL_MAX.ordinal())
        {
            synchronized (log)
            {
                log.write(TIMESTAMP.format(new Date()) + " " + level + "/" + tag + ": " + message + "\n");
                if (e != null)
                {
                    e.printStackTrace(log);
                }
                log.flush();
            }
        }

        // Log to logcat at a minimum
        switch (level)
        {
            case    INFO:
                Log.i(tag, message);
                break;

            case    WARNING:
                Log.w(tag, message);
                break;

            case    DEBUG:
                Log.d(tag, message);
                break;

            case    ERROR:
                Log.e(tag, message, e);
                break;
        }
    }

    public static void i(String tag, String message)
    {
        log(LogLevel.INFO, tag, message);
    }

    public static void w(String tag, String message)
    {
        log(LogLevel.WARNING, tag, message);
    }

    public static void d(String tag, String message)
    {
        log(LogLevel.DEBUG, tag, message);
    }

    public static void e(String tag, String message)
    {
        log(LogLevel.ERROR, tag, message);
    }

    public static void e(String tag, String message, Throwable e)
    {
        log(LogLevel.ERROR, tag, message, e);
    }
}

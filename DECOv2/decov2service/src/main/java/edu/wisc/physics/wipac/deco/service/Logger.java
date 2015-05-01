package edu.wisc.physics.wipac.deco.service;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by andrewbiewer on 4/30/15.
 */
public class Logger
{
    private static final String TAG = "Logger";

    private static PrintWriter log;
    private static SimpleDateFormat TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static final LogLevel LOG_LEVEL_MAX = LogLevel.DEBUG;

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
    }

    public static void open(Context context, String filename)
    {
        try
        {
            File file = new File(Environment.getExternalStorageDirectory(), context.getString(R.string.app_name));
            file.mkdirs();

            log = new PrintWriter(
                    new FileWriter(
                            new File(file, filename),
                            true), // append
                    true); // autoFlush
        }
        catch (Exception e)
        {
            log = null;
            Log.e(TAG, "Failed to open " + filename, e);
        }
    }

    public static void close()
    {
        if (log != null)
        {
            log.close();
            log = null;
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

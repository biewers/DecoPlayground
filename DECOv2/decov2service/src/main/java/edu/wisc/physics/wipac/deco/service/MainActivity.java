package edu.wisc.physics.wipac.deco.service;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity
{
    private static final String TAG = "MainActivity";

    private long mNumImages = 0;
    private long mSizeImages = 0;

    private TextView mTextNumImages;
    private TextView mTextSizeImages;
    private TextView mTextActualExposure;

    private boolean mServiceStarted = false;
    private Button mBtnToggleService;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Logger.open(this, TAG + ".txt");

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

        initializeControls();
    }

    private void initializeControls()
    {
        mTextNumImages = (TextView) findViewById(R.id.textNumImages);
        mTextSizeImages = (TextView) findViewById(R.id.textSizeImages);
        mTextActualExposure = (TextView) findViewById(R.id.textActualExposure);

        mBtnToggleService = (Button) findViewById(R.id.btnToggleService);
        mBtnToggleService.setOnClickListener(
                new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v)
                    {
                        startStopService();
                    }
                }
        );

        if (DecoCameraService.isRunning(this))
        {
            mBtnToggleService.setText("Stop Service");
            mServiceStarted = true;
        }
        else
        {
//            startStopService();
        }
    }

    private void startStopService()
    {
        if (!mServiceStarted)
        {
            Intent intent = new Intent(this, DecoCameraService.class);
            startService(intent);
            mBtnToggleService.setText("Stop Service");
            mServiceStarted = true;
        }
        else
        {
            Intent intent = new Intent(this, DecoCameraService.class);
            stopService(intent);
            mBtnToggleService.setText("Start Service");
            mServiceStarted = false;
        }
    }

    @Override
    protected void onResume()
    {
        Logger.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onPause()
    {
        Logger.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onDestroy()
    {
        Logger.d(TAG, "onDestroy");
        Logger.close();
        super.onDestroy();
    }

    private class IncomingHandler extends Handler
    {
        @Override
        public void handleMessage(Message msg)
        {
            Bundle bundle = msg.getData();
            bundle.setClassLoader(ImageInfo.class.getClassLoader());

            ImageInfo imageInfo = bundle.getParcelable("imageInfo");

            if (imageInfo.getSize() != null)
            {
                mTextNumImages.setText(String.valueOf(++mNumImages));
                mSizeImages += imageInfo.getSize();
                mTextSizeImages.setText(String.valueOf(mSizeImages));
            }

            if (imageInfo.getExposure() != null)
            {
                mTextActualExposure.setText(String.valueOf(imageInfo.getExposure()));
            }
        }
    }

    private class DecoServiceConnection implements ServiceConnection
    {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service)
        {

        }

        @Override
        public void onServiceDisconnected(ComponentName name)
        {

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings)
        {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

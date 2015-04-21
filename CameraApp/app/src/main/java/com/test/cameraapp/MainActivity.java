package com.test.cameraapp;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


public class MainActivity extends Activity
{
    private final String TAG = "MainActivity";

    private Handler mMainHandler;

    private AutoFitTextureView mTextureView;

    /*
    // Preview related fields
    private HandlerThread mPreviewHandlerThread;
    private Handler mPreviewHandler;
    private Surface mPreviewSurface;
    private Size mPreviewSize;
    private CaptureRequest mPreviewCaptureRequest;
    */

    // Still capture related fields
    private static final int READER_MAX_IMAGES = 10;
    private static final int STILL_CAPTURE_PAUSE = 10000; // milliseconds
    private ImageReader mStillReader;
    private Size mStillSize = new Size(640, 480); // default
    private CaptureRequest mStillCaptureRequest;

    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;

    private AtomicBoolean mCameraReady = new AtomicBoolean();
    private AtomicBoolean mPreviewTextureReady = new AtomicBoolean();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMainHandler = null; //new Handler();

        initializeControls();
    }

    private void initializeControls()
    {
        mTextureView = (AutoFitTextureView) findViewById(R.id.textureView);
        mTextureView.setSurfaceTextureListener(
            new TextureView.SurfaceTextureListener()
            {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height)
                {
                    onPreviewTextureAvailable();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height)
                {
                    Log.d(TAG, "Preview texture size changed");
                    onPreviewTextureAvailable();
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface)
                {
                    Log.d(TAG, "Preview texture destroyed");
                    mPreviewTextureReady.set(false);
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
            }
        );
    }

    private CameraDevice.StateCallback mCameraStateCallback =
        new CameraDevice.StateCallback()
        {
            @Override
            public void onOpened(final CameraDevice camera)
            {
                onCameraDeviceOpen(camera);
            }

            @Override
            public void onDisconnected(CameraDevice camera)
            {
                onCameraDeviceDisconnected();
            }

            @Override
            public void onError(CameraDevice camera, int error)
            {
                closeCameraDevice();
                makeToast("Camera device error " + error);
                Log.e(TAG, "Camera device error " + error);
            }
        };

    /**
     * Locates a back facing camera and attempts to open it. Once the camera is open successfully,
     * the {@link #onCameraDeviceOpen(android.hardware.camera2.CameraDevice)} method is invoked.
     */
    private void openCameraDevice()
    {
        if (mCameraDevice != null)
        {
            Log.d(TAG, "Camera already opened");
            return;
        }

        Log.d(TAG, "Opening camera");

        try
        {
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

            String[] cameraIds = cameraManager.getCameraIdList();
            if (cameraIds == null)
            {
                makeToast("No cameras found");
                Log.w(TAG, "No cameras found");
                return;
            }

            for (String cameraId : cameraIds)
            {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK)
                {
                    cameraManager.openCamera(cameraId, mCameraStateCallback, mMainHandler);
                    return;
                }
            }

            makeToast("No back facing camera");
        }
        catch (Exception e)
        {
            makeToast("Failed to open camera");
            Log.e(TAG, "Failed to open camera", e);
        }
    }

    /**
     * This method is invoked once the camera device is opened.
     *
     * @param cameraDevice The camera device which was just opened.
     */
    private void onCameraDeviceOpen(CameraDevice cameraDevice)
    {
        Log.d(TAG, "onCameraDeviceOpen - thread " + Thread.currentThread().getName() + "(" + Thread.currentThread().getId() + ")");
        mCameraDevice = cameraDevice;

        if (determineCameraOutputSize())
        {
            mCameraReady.set(true);
            if (mPreviewTextureReady.get())
            {
                startPreview();
            }
        }
    }

    private boolean determineCameraOutputSize()
    {
        try
        {
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(mCameraDevice.getId());
            StreamConfigurationMap scalerStreamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (scalerStreamConfigurationMap == null)
            {
                makeToast("Unable to determine camera output size");
                Log.e(TAG, "Unable to determine camera output size - CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP is null");
                return false;
            }
            else
            {
                Size[] outputSizes = null;

                /*
                // Preview output size
                outputSizes = scalerStreamConfigurationMap.getOutputSizes(SurfaceTexture.class);
                if (outputSizes == null)
                {
                    makeToast("Unable to determine camera output size");
                    Log.e(TAG, "Unable to determine camera output size - output sizes null");
                    return false;
                }

                mPreviewSize = outputSizes[0];
                Log.d(TAG, "Preview output size " + mPreviewSize);

                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE)
                {
                    mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                }
                else
                {
                    mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }
                */

                // Still capture output size
                outputSizes = scalerStreamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
                if (outputSizes != null && outputSizes.length > 0)
                {
                    mStillSize = outputSizes[0];
                }
                Log.d(TAG, "Still capture output size " + mStillSize);
            }
        }
        catch (Exception e)
        {
            makeToast("Failed to determine camera output size");
            Log.e(TAG, "Failed to determine camera output size", e);
            return false;
        }

        return true;
    }

    private void onPreviewTextureAvailable()
    {
        Log.d(TAG, "onPreviewTextureAvailable - thread " + Thread.currentThread().getName() + "(" + Thread.currentThread().getId() + ")");

        mPreviewTextureReady.set(true);
        if (mCameraReady.get())
        {
            startPreview();
        }
    }

    private void startPreview()
    {
        // TODO Change this
        createCaptureSession();
    }

    /**
     * This method is called once the surface texture of the preview text view is available or changes.
     * Initialize the preview surface and attempt to create a capture session. Once the capture session
     * is successfully created, the {@link #onCameraCaptureSessionConfigured(android.hardware.camera2.CameraCaptureSession)}
     * method is invoked.
     */
    private void createCaptureSession()
    {
        // Create a capture session with a set of Surfaces using CameraManager#createCaptureSession(List, ...)
        try
        {
            mCameraDevice.createCaptureSession(
                getCameraSurfaces(),
                new CameraCaptureSession.StateCallback()
                {
                    @Override
                    public void onConfigured(CameraCaptureSession session)
                    {
                        onCameraCaptureSessionConfigured(session);
                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession session)
                    {
                        makeToast("Failed to configure camera capture session");
                        Log.e(TAG, "Failed to configure camera capture session");
                    }
                },
                mMainHandler); // The StateCallback will be invoked on the main handler's thread
        }
        catch (Exception e)
        {
            makeToast("Unable to create camera capture session");
            Log.e(TAG, "Unable to create camera capture session", e);
        }
    }

    /**
     * This method is invoked once the camera capture session is configured. Create a capture
     * request which runs in a new background thread to capture preview images from the camera.
     *
     * @param session The capture session which was just configured.
     */
    private void onCameraCaptureSessionConfigured(CameraCaptureSession session)
    {
        Log.d(TAG, "onCameraCaptureSessionConfigured");

        mCameraCaptureSession = session;

        try
        {
            CaptureRequest.Builder captureRequestBuilder = null;

            /*
            // Setup preview capture as a repeating request
            captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureRequestBuilder.addTarget(mPreviewSurface);

            mPreviewCaptureRequest = captureRequestBuilder.build();

            mCameraCaptureSession.setRepeatingRequest(
                    mPreviewCaptureRequest,
                    null,
                    getPreviewHandler());
            */

            // Setup still capture
            captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureRequestBuilder.addTarget(mStillReader.getSurface());

            mStillCaptureRequest = captureRequestBuilder.build();

            ImageReader.OnImageAvailableListener readerListener =
                    new ImageReader.OnImageAvailableListener()
                    {
                        @Override
                        public void onImageAvailable(ImageReader reader)
                        {
                            Log.d(TAG, "Still capture image available");
                            Image image = reader.acquireLatestImage();

                            // TODO Do something with the image

                            // You need to close the image or you will exceed READER_MAX_IMAGES
                            image.close();
                            Log.d(TAG, "Acquired image");
                        }
                    };

            HandlerThread handlerThread = new HandlerThread("Still Capture Image Available Listener Handler Thread");
            handlerThread.start();
            Handler handler = new Handler(handlerThread.getLooper());
            mStillReader.setOnImageAvailableListener(readerListener, handler);

            final CameraCaptureSession.CaptureCallback stillCaptureCallback =
                    new CameraCaptureSession.CaptureCallback()
                    {
                        @Override
                        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result)
                        {
                            Log.d(TAG, "Image capture completed");
                        }
                    };

            // Now, setup a simple thread to grab an image every second
            new Thread(
                    new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            HandlerThread handlerThread = new HandlerThread("Still Capture Handler Thread");
                            handlerThread.start();
                            Handler handler = new Handler(handlerThread.getLooper());
                            while (true)
                            {
                                try
                                {
                                    if (mCameraReady.get())
                                    {
                                        Log.d(TAG, "Initiating still capture");
                                        int captureId = mCameraCaptureSession.capture(mStillCaptureRequest, stillCaptureCallback, handler);
                                        Log.d(TAG, "Capture ID " + captureId);
                                    }
                                }
                                catch (Exception e)
                                {
                                    Log.e(TAG, "Failed to capture image", e);
                                    break;
                                }

                                try
                                {
                                    Thread.sleep(STILL_CAPTURE_PAUSE);
                                }
                                catch (Exception e)
                                {
                                }
                            }
                        }
                    }).start();
        }
        catch (Exception e)
        {
            makeToast("Unable to create camera capture request");
            Log.e(TAG, "Unable to create camera capture request", e);
        }
    }

    private Handler getPreviewHandler()
    {
        return null;

        /*
        if (mPreviewHandlerThread == null)
        {
            mPreviewHandlerThread = new HandlerThread("CameraPreview");
        }

        if (!mPreviewHandlerThread.isAlive())
        {
            mPreviewHandlerThread.start();
        }

        if (mPreviewHandler == null)
        {
            mPreviewHandler = new Handler(mPreviewHandlerThread.getLooper());
        }

        return mPreviewHandler;
        */
    }

    /**
     * Returns the list of surfaces the camera should stream images to.
     * Assumes the camera device has been opened and the output size has been determined.
     *
     * @return The list of surfaces to stream images to.
     */
    private List<Surface> getCameraSurfaces()
    {
        Log.d(TAG, "getCameraSurfaces");

        /*
        // Configure the width and height of the surface texture
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

        // Create preview surface
        mPreviewSurface = new Surface(surfaceTexture);
        */

        // Create still reader
        mStillReader = ImageReader.newInstance(mStillSize.getWidth(), mStillSize.getHeight(), ImageFormat.JPEG, READER_MAX_IMAGES);

//      return Arrays.asList(new Surface[] { mPreviewSurface, mStillReader.getSurface() });
        return Arrays.asList(new Surface[] { mStillReader.getSurface() });
    }

    private void onCameraDeviceDisconnected()
    {
        Log.d(TAG, "onCameraDeviceDisconnected");
        closeCameraDevice();
    }

    private void closeCameraDevice()
    {
        Log.d(TAG, "closeCameraDevice");

        mCameraReady.set(false);

        if (mCameraCaptureSession != null)
        {
            Log.d(TAG, "Closing camera capture session");
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }

        if (mCameraDevice != null)
        {
            Log.d(TAG, "Closing camera device");
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        Log.d(TAG, "onPause");

//        closeCameraDevice();
        /*
        try
        {
            Log.d(TAG, "Stopping preview capture");
            mCameraCaptureSession.stopRepeating();
        }
        catch (CameraAccessException e)
        {
            Log.e(TAG, "Failed to stop preview capture", e);
        }
        */
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        Log.d(TAG, "onResume");

        if (!mCameraReady.get())
        {
            openCameraDevice();
        }
        else
        {
            /*
            try
            {
                Log.d(TAG, "Starting preview capture");
                mCameraCaptureSession.setRepeatingRequest(
                        mPreviewCaptureRequest,
                        null,
                        getPreviewHandler());
            }
            catch (CameraAccessException e)
            {
                Log.e(TAG, "Failed to start preview capture", e);
            }
            */
        }
    }

    private void makeToast(String message)
    {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
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

package edu.wisc.physics.wipac.deco.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.hardware.camera2.CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY;
import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_OFF;


public class MainActivity extends Activity
{
    private static final String TAG = "MainActivity";
    private static final SimpleDateFormat IMAGE_FILE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss.SSS");

    private Handler mMainHandler;

    private long mNumImages = 0;
    private long mSizeImages = 0;

    private TextView mTextNumImages;
    private TextView mTextSizeImages;
    private TextView mTextExposure;

    // Still capture related fields
    private static final int READER_MAX_IMAGES = 10;
    private static final int STILL_CAPTURE_PAUSE = 100; // milliseconds
    private ImageReader mStillReader;
    private Size mStillSize = new Size(640, 480); // default
    private CaptureRequest mStillCaptureRequest;

    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;

    private AtomicBoolean mCameraReady = new AtomicBoolean();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMainHandler = null; //new Handler();

        Thread.currentThread().setUncaughtExceptionHandler(
            new Thread.UncaughtExceptionHandler()
            {
                @Override
                public void uncaughtException(Thread thread, Throwable e)
                {
                    DecoApp.e(TAG, "FATAL ERROR: Uncaught exception!", e);
                }
            }
        );

        initializeControls();
    }

    private void initializeControls()
    {
        mTextNumImages = (TextView) findViewById(R.id.textNumImages);
        mTextSizeImages = (TextView) findViewById(R.id.textSizeImages);
        mTextExposure = (TextView) findViewById(R.id.textExposure);
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
                DecoApp.e(TAG, "Camera device error " + error);
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
            DecoApp.d(TAG, "Camera already opened");
            return;
        }

        DecoApp.d(TAG, "Opening camera");

        try
        {
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

            String[] cameraIds = cameraManager.getCameraIdList();
            if (cameraIds == null)
            {
                makeToast("No cameras found");
                DecoApp.w(TAG, "No cameras found");
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
            DecoApp.e(TAG, "Failed to open camera", e);
        }
    }

    /**
     * This method is invoked once the camera device is opened.
     *
     * @param cameraDevice The camera device which was just opened.
     */
    private void onCameraDeviceOpen(CameraDevice cameraDevice)
    {
        DecoApp.d(TAG, "onCameraDeviceOpen - thread " + Thread.currentThread().getName() + "(" + Thread.currentThread().getId() + ")");
        mCameraDevice = cameraDevice;

        if (determineCameraOutputSize())
        {
            mCameraReady.set(true);
            DecoApp.d(TAG, "onCameraDeviceOpen camera ready " + mCameraReady.get());
            createCaptureSession();
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
                DecoApp.e(TAG, "Unable to determine camera output size - CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP is null");
                return false;
            }
            else
            {
                Size[] outputSizes = null;

                // Still capture output size
                outputSizes = scalerStreamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
                if (outputSizes != null && outputSizes.length > 0)
                {
                    mStillSize = outputSizes[0];
                }
                DecoApp.d(TAG, "Still capture output size " + mStillSize);
            }
        }
        catch (Exception e)
        {
            makeToast("Failed to determine camera output size");
            DecoApp.e(TAG, "Failed to determine camera output size", e);
            return false;
        }

        return true;
    }

    /**
     * Attempt to create a capture session. Once the capture session
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
                        DecoApp.e(TAG, "Failed to configure camera capture session");
                    }
                },
                mMainHandler); // The StateCallback will be invoked on the main handler's thread
        }
        catch (Exception e)
        {
            makeToast("Unable to create camera capture session");
            DecoApp.e(TAG, "Unable to create camera capture session", e);
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
        DecoApp.d(TAG, "onCameraCaptureSessionConfigured");

        mCameraCaptureSession = session;

        try
        {
            CaptureRequest.Builder captureRequestBuilder = null;

            // Setup still capture
            captureRequestBuilder = getStillCaptureRequestBuilder();
            captureRequestBuilder.addTarget(mStillReader.getSurface());
            mStillCaptureRequest = captureRequestBuilder.build();

            mCameraCaptureSession.setRepeatingRequest(
                    mStillCaptureRequest,
                    null,
                    null);

            ImageReader.OnImageAvailableListener readerListener =
                    new ImageReader.OnImageAvailableListener()
                    {
                        @Override
                        public void onImageAvailable(ImageReader reader)
                        {
                            DecoApp.d(TAG, "Still capture image available");
                            Image image = reader.acquireLatestImage();

                            // TODO Save for now
                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            byte[] bytes = new byte[buffer.capacity()];
                            buffer.get(bytes);
                            saveImage(bytes);

                            // You need to close the image or you will exceed READER_MAX_IMAGES
                            image.close();
                            DecoApp.d(TAG, "Acquired image");
                        }
                    };

            HandlerThread handlerThread = new HandlerThread("Still Capture Image Available Listener Handler Thread");
            handlerThread.start();
            Handler handler = new Handler(handlerThread.getLooper());
            mStillReader.setOnImageAvailableListener(readerListener, handler);

            /*
            final CameraCaptureSession.CaptureCallback stillCaptureCallback =
                    new CameraCaptureSession.CaptureCallback()
                    {
                        @Override
                        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result)
                        {
                            DecoApp.d(TAG, "Image capture completed");
                        }
                    };

            // Now, setup a simple thread to grab an image occasionally
            new AppThread(
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
                                        DecoApp.d(TAG, "Initiating still capture");
                                        int captureId = mCameraCaptureSession.capture(mStillCaptureRequest, stillCaptureCallback, handler);
                                        DecoApp.d(TAG, "Capture ID " + captureId);
                                    }
                                }
                                catch (Exception e)
                                {
                                    DecoApp.e(TAG, "Failed to capture image", e);
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
            */
        }
        catch (Exception e)
        {
            makeToast("Unable to create camera capture request");
            DecoApp.e(TAG, "Unable to create camera capture request", e);
        }
    }

    private CaptureRequest.Builder getStillCaptureRequestBuilder() throws CameraAccessException
    {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraDevice.getId());

        Long exposure = null;
        int supportedHardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (supportedHardwareLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL)
        {
            // gets the upper exposure time supported by the camera object
            exposure = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE).getUpper();
            DecoApp.i(TAG, "Camera exposure upper bound = " + exposure);
            final Long finalExposure = exposure;
            MainActivity.this.runOnUiThread(
                new Runnable()
                {
                    @Override
                    public void run()
                    {
                        mTextExposure.setText(String.valueOf(finalExposure));
                    }
                }
            );
        }
        else
        {
            DecoApp.i(TAG, "Supported hardware level (" + supportedHardwareLevel + ") does not support exposure time range characteristic");
            MainActivity.this.runOnUiThread(
                new Runnable()
                {
                    @Override
                    public void run()
                    {
                        mTextExposure.setText(getString(R.string.not_supported));
                    }
                }
            );
        }

        CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_OFF);
        if (exposure != null)
        {
            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure);
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        captureRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);
        captureRequestBuilder.set(CaptureRequest.BLACK_LEVEL_LOCK, false);// without it unlocked it might cause issues
        captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, characteristics.get(SENSOR_MAX_ANALOG_SENSITIVITY));

        return captureRequestBuilder;
    }

    private void saveImage(final byte[] imageBytes)
    {
        DecoApp.d(TAG, "Saving image");
        try
        {
            File dir = new File(Environment.getExternalStorageDirectory(), getResources().getString(R.string.app_name));
            dir.mkdirs();

            String imageName = IMAGE_FILE_FORMAT.format(new Date()) + ".jpg";

            File file = new File(dir, imageName);
            DecoApp.d(TAG, "Image " + file.getAbsolutePath());

            FileOutputStream output = new FileOutputStream(file);
            output.write(imageBytes);
            output.close();

            MainActivity.this.runOnUiThread(
                new Runnable()
                {
                    @Override
                    public void run()
                    {
                        mTextNumImages.setText(String.valueOf(++mNumImages));
                        mTextSizeImages.setText(String.valueOf((mSizeImages += imageBytes.length) / 1024) + "kb");
                    }
                });
        }
        catch (Exception e)
        {
            DecoApp.e(TAG, "Failed to save image", e);
        }
    }

    /**
     * Returns the list of surfaces the camera should stream images to.
     * Assumes the camera device has been opened and the output size has been determined.
     *
     * @return The list of surfaces to stream images to.
     */
    private List<Surface> getCameraSurfaces()
    {
        DecoApp.d(TAG, "getCameraSurfaces");

        // Create still reader
        mStillReader = ImageReader.newInstance(mStillSize.getWidth(), mStillSize.getHeight(), ImageFormat.JPEG, READER_MAX_IMAGES);

        return Arrays.asList(new Surface[] { mStillReader.getSurface() });
    }

    private void onCameraDeviceDisconnected()
    {
        DecoApp.d(TAG, "onCameraDeviceDisconnected");
        closeCameraDevice();
    }

    private void closeCameraDevice()
    {
        DecoApp.d(TAG, "closeCameraDevice");

        mCameraReady.set(false);

        if (mCameraCaptureSession != null)
        {
            DecoApp.d(TAG, "Closing camera capture session");
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }

        if (mCameraDevice != null)
        {
            DecoApp.d(TAG, "Closing camera device");
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        DecoApp.d(TAG, "onPause");
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        DecoApp.d(TAG, "onResume camera ready " + mCameraReady.get());

        if (!mCameraReady.get())
        {
            openCameraDevice();
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

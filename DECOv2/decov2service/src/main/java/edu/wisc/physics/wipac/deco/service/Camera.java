package edu.wisc.physics.wipac.deco.service;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.util.Size;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Camera
{
    private static final String TAG = "Camera";

    private static final SimpleDateFormat IMAGE_DIR_FORMAT = new SimpleDateFormat("yyyyMMdd_HH");
    private static final SimpleDateFormat IMAGE_FILE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss.SSS");

    private Context mContext;
    private Handler mHandler;

    private AtomicBoolean mCameraReady = new AtomicBoolean();
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraCaptureStateCallback mCameraCaptureStateCallback;

    // Still capture related fields
    private static final int READER_MAX_IMAGES = 10;
    private static final int STILL_CAPTURE_PAUSE = 1000; // milliseconds
    private ImageReader mStillReader;
    private Size mStillSize = new Size(640, 480); // default
    private CaptureRequest mStillCaptureRequest;

    public Camera(Context context, Handler handler)
    {
        this.mContext = context;
        this.mHandler = handler;
    }

    public void setCameraCaptureStateCallback(CameraCaptureStateCallback cameraCaptureStateCallback)
    {
        this.mCameraCaptureStateCallback = cameraCaptureStateCallback;
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
                    // TODO makeToast("Camera device error " + error);
                    Logger.e(TAG, "Camera device error " + error);
                }
            };

    /**
     * Locates a back facing camera and attempts to open it. Once the camera is open successfully,
     * the {@link #onCameraDeviceOpen(android.hardware.camera2.CameraDevice)} method is invoked.
     */
    public void openCameraDevice()
    {
        if (mCameraDevice != null)
        {
            Logger.d(TAG, "Camera already opened");
            return;
        }

        Logger.i(TAG, "Opening camera");

        try
        {
            CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);

            String[] cameraIds = cameraManager.getCameraIdList();
            if (cameraIds == null)
            {
                // TODO makeToast("No cameras found");
                Logger.w(TAG, "No cameras found");
                return;
            }

            for (String cameraId : cameraIds)
            {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK)
                {
                    cameraManager.openCamera(cameraId, mCameraStateCallback, mHandler);
                    return;
                }
            }

            // TODO makeToast("No back facing camera");
        }
        catch (Exception e)
        {
            // TODO makeToast("Failed to open camera");
            Logger.e(TAG, "Failed to open camera", e);
        }
    }

    /**
     * This method is invoked once the camera device is opened.
     *
     * @param cameraDevice The camera device which was just opened.
     */
    private void onCameraDeviceOpen(CameraDevice cameraDevice)
    {
        Logger.d(TAG, "onCameraDeviceOpen - thread " + Thread.currentThread().getName() + "(" + Thread.currentThread().getId() + ")");
        mCameraDevice = cameraDevice;

        if (determineCameraOutputSize())
        {
            mCameraReady.set(true);
            Logger.d(TAG, "onCameraDeviceOpen camera ready " + mCameraReady.get());
            if (mCameraCaptureStateCallback != null)
            {
                mCameraCaptureStateCallback.onCameraOpen();
            }
            createCaptureSession();
        }
    }

    private boolean determineCameraOutputSize()
    {
        try
        {
            CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(mCameraDevice.getId());
            StreamConfigurationMap scalerStreamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (scalerStreamConfigurationMap == null)
            {
                // TODO makeToast("Unable to determine camera output size");
                Logger.e(TAG, "Unable to determine camera output size - CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP is null");
                return false;
            }
            else
            {

                // Still capture output size
                Size[] outputSizes = scalerStreamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
                if (outputSizes != null && outputSizes.length > 0)
                {
                    mStillSize = outputSizes[0];
                }
                Logger.d(TAG, "Still capture output size " + mStillSize);
            }
        }
        catch (Exception e)
        {
            // TODO makeToast("Failed to determine camera output size");
            Logger.e(TAG, "Failed to determine camera output size", e);
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
                            // TODO makeToast("Failed to configure camera capture session");
                            Logger.e(TAG, "Failed to configure camera capture session");
                        }
                    },
                    mHandler);
        }
        catch (Exception e)
        {
            // TODO makeToast("Unable to create camera capture session");
            Logger.e(TAG, "Unable to create camera capture session", e);
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
        Logger.d(TAG, "onCameraCaptureSessionConfigured");

        mCameraCaptureSession = session;

        try
        {

            // Setup still capture
            CaptureRequest.Builder captureRequestBuilder = getStillCaptureRequestBuilder();
            captureRequestBuilder.addTarget(mStillReader.getSurface());
            mStillCaptureRequest = captureRequestBuilder.build();

            final CameraCaptureSession.CaptureCallback stillCaptureCallback =
                    new CameraCaptureSession.CaptureCallback()
                    {
                        @Override
                        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, final TotalCaptureResult result)
                        {
                            Logger.d(TAG, "Image capture completed");
                            if (mCameraCaptureStateCallback != null)
                            {
                                Long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                                mCameraCaptureStateCallback.onCaptureCompleted(exposureTime);
                            }
                        }
                    };

            ImageReader.OnImageAvailableListener readerListener =
                    new ImageReader.OnImageAvailableListener()
                    {
                        @Override
                        public void onImageAvailable(ImageReader reader)
                        {
                            Logger.d(TAG, "Still capture image available");
                            Image image = reader.acquireLatestImage();

                            // TODO Save for now
                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            byte[] bytes = new byte[buffer.capacity()];
                            buffer.get(bytes);
                            saveImage(bytes);

                            // You need to close the image or you will exceed READER_MAX_IMAGES
                            image.close();
                            Logger.d(TAG, "Acquired image");
                        }
                    };

            mStillReader.setOnImageAvailableListener(readerListener, mHandler);

            // Now, setup a simple thread to grab an image occasionally
            new AppThread(
                    new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            while (true)
                            {
                                try
                                {
                                    if (mCameraReady.get())
                                    {
                                        Logger.d(TAG, "Initiating still capture");
                                        int captureId = mCameraCaptureSession.capture(mStillCaptureRequest, stillCaptureCallback, mHandler);
                                        Logger.d(TAG, "Capture ID " + captureId);
                                    }
                                }
                                catch (Exception e)
                                {
                                    Logger.e(TAG, "Failed to capture image", e);
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
            // TODO makeToast("Unable to create camera capture request");
            Logger.e(TAG, "Unable to create camera capture request", e);
        }
    }

    private CaptureRequest.Builder getStillCaptureRequestBuilder() throws CameraAccessException
    {
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraDevice.getId());

        Long exposure = null;
        int supportedHardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (supportedHardwareLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL)
        {
            // gets the upper exposure time supported by the camera object
            exposure = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE).getUpper();
            Logger.i(TAG, "Camera exposure upper bound = " + exposure + "ns");
            if (mCameraCaptureStateCallback != null)
            {
                mCameraCaptureStateCallback.onExposureSet(exposure);
            }
        }
        else
        {
            Logger.i(TAG, "Supported hardware level (" + supportedHardwareLevel + ") does not support exposure time range characteristic");
            if (mCameraCaptureStateCallback != null)
            {
                mCameraCaptureStateCallback.onExposureSet(null);
            }
        }

        CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraCharacteristics.CONTROL_AE_MODE_OFF);
        if (exposure != null)
        {
            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure);
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        captureRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);
        captureRequestBuilder.set(CaptureRequest.BLACK_LEVEL_LOCK, false);// without it unlocked it might cause issues
        captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, characteristics.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY));

        return captureRequestBuilder;
    }

    private void saveImage(final byte[] imageBytes)
    {
        Logger.d(TAG, "Saving image");
        try
        {
            Date now = new Date();

            File dir = new File(Environment.getExternalStorageDirectory(), mContext.getResources().getString(R.string.app_name));
            dir = new File(dir, IMAGE_DIR_FORMAT.format(now));
            dir.mkdirs();

            String imageName = IMAGE_FILE_FORMAT.format(now) + ".jpg";

            File file = new File(dir, imageName);
            Logger.d(TAG, "Image " + file.getAbsolutePath());

            FileOutputStream output = new FileOutputStream(file);
            output.write(imageBytes);
            output.close();

            if (mCameraCaptureStateCallback != null)
            {
                mCameraCaptureStateCallback.onImageCaptured(new ImageInfo(imageName, Long.valueOf(imageBytes.length), null));
            }
        }
        catch (Exception e)
        {
            Logger.e(TAG, "Failed to save image", e);
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
        Logger.d(TAG, "getCameraSurfaces");

        // Create still reader
        mStillReader = ImageReader.newInstance(mStillSize.getWidth(), mStillSize.getHeight(), ImageFormat.JPEG, READER_MAX_IMAGES);

        return Arrays.asList(mStillReader.getSurface());
    }

    private void onCameraDeviceDisconnected()
    {
        Logger.d(TAG, "onCameraDeviceDisconnected");
        closeCameraDevice();
    }

    public void closeCameraDevice()
    {
        Logger.i(TAG, "Closing camera");

        mCameraReady.set(false);

        if (mCameraCaptureSession != null)
        {
            Logger.d(TAG, "Closing camera capture session");
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }

        if (mCameraDevice != null)
        {
            Logger.d(TAG, "Closing camera device");
            mCameraDevice.close();
            mCameraDevice = null;
        }

        if (mCameraCaptureStateCallback != null)
        {
            mCameraCaptureStateCallback.onCameraClosed();
        }
    }
}

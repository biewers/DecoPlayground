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
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.telephony.TelephonyManager;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class Camera
{
    private static final String TAG = "Camera";

    private static final String CAPTURE_BUILDER_PROP_FILE = "camera-settings.properties";
    private static final SimpleDateFormat IMAGE_DIR_FORMAT = new SimpleDateFormat("yyyyMMdd_HH");
    private static final SimpleDateFormat IMAGE_FILE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss.SSS");

    private Context mContext;
    private Handler mHandler;
    private Properties mCaptureBuilderProperties;

    private HandlerThread mCaptureThread;
    private Handler mCaptureHandler;

    private AtomicBoolean mCameraReady = new AtomicBoolean();
    private CameraDevice mCameraDevice;
    private DecoCameraCharacteristics mDecoCameraCharacteristics;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraCaptureSession.CaptureCallback mStillCaptureCallback;
    private CameraCaptureStateCallback mCameraCaptureStateCallback;

    // Still capture related fields
    private static final int READER_MAX_IMAGES = 10;
    private static final int STILL_CAPTURE_PAUSE = 1000; // milliseconds
    private ImageReader mStillReader;
    private Size mStillSize = new Size(640, 480); // default
    private CaptureRequest mStillCaptureRequest;

    // Location information
    private static final long MIN_TIME_LOCATION_UPDATES      = 5 * 60 * 1000;  // 5mins
    private static final float MIN_DISTANCE_LOCATION_UPDATES = 10;  // meters
    private Location mLocation;

    private AtomicLong mNumSavedImages = new AtomicLong();

    public Camera(Context context, Handler handler) throws IOException
    {
        this.mContext = context;
        this.mHandler = handler;
        mCaptureBuilderProperties = new Properties();
        try
        {
            File dir = new File(Environment.getExternalStorageDirectory(), mContext.getResources().getString(R.string.app_name));
            mCaptureBuilderProperties.load(new FileInputStream(new File(dir, CAPTURE_BUILDER_PROP_FILE)));
        }
        catch (IOException e)
        {
            Logger.e(TAG, "Failed to load " + CAPTURE_BUILDER_PROP_FILE + " file", e);
            throw e;
        }
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

    private LocationListener locationListener =
            new LocationListener()
            {
                @Override
                public void onLocationChanged(Location location)
                {
                    Logger.d(TAG, "Location changed " + location);
                    try
                    {
                        if (mCameraReady.get())
                        {
                            mCameraReady.set(Camera.this.mLocation != null);
                            Camera.this.mLocation = location;
                            CaptureRequest.Builder captureRequestBuilder = Camera.this.getStillCaptureRequestBuilder();
                            mStillCaptureRequest = captureRequestBuilder.build();
                            mCameraReady.set(true);
                        }
                    }
                    catch (Exception e)
                    {
                        Logger.e(TAG, "Failed to update capture request with new location", e);
                    }
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras)
                {
                    Logger.d(TAG, "Location status changed " + provider + " " + status + " " + extras);
                }

                @Override
                public void onProviderEnabled(String provider)
                {
                    Logger.d(TAG, "Location provider enabled " + provider);
                }

                @Override
                public void onProviderDisabled(String provider)
                {
                    Logger.d(TAG, "Location provider disabled " + provider);
                }
            };

    private void setupLocationListener()
    {
        LocationManager locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        criteria.setAccuracy (Criteria.ACCURACY_FINE);
        criteria.setPowerRequirement (Criteria.NO_REQUIREMENT);
        String locationProvider = locationManager.getBestProvider(criteria, true);

        Logger.d(TAG, "Location provider " + locationProvider);
        if (locationProvider != null)
        {
            locationManager.requestLocationUpdates(locationProvider, MIN_TIME_LOCATION_UPDATES, MIN_DISTANCE_LOCATION_UPDATES, locationListener);
            Location lastLocation = locationManager.getLastKnownLocation(locationProvider);
            if (lastLocation != null)
            {
                locationListener.onLocationChanged(lastLocation);
            }
        }
    }

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

        // Get this going right away since it can take GPS a while
        setupLocationListener();

        try
        {
            String cameraId = getCameraId(mContext);
            if (cameraId == null)
            {
                Logger.w(TAG, "No cameras found");
            }
            else
            {
                CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
                cameraManager.openCamera(cameraId, mCameraStateCallback, mHandler);
            }
        }
        catch (Exception e)
        {
            Logger.e(TAG, "Failed to open camera", e);
        }
    }

    public static String getCameraId(Context context) throws CameraAccessException
    {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        String[] cameraIds = cameraManager.getCameraIdList();
        if (cameraIds == null)
        {
            return null;
        }

        for (String cameraId : cameraIds)
        {
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK)
            {
                return cameraId;
            }
        }

        return null;
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

        try
        {
            mDecoCameraCharacteristics = new DecoCameraCharacteristics(mContext, mCameraDevice.getId());
        }
        catch (CameraAccessException e)
        {
            Logger.e(TAG, "Failed to create camera characteristics", e);
        }

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
            mStillCaptureRequest = captureRequestBuilder.build();

            mStillCaptureCallback =
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
                        private AtomicLong mNumImages = new AtomicLong();

                        @Override
                        public void onImageAvailable(ImageReader reader)
                        {
                            captureImage();

                            long imageNum = mNumImages.addAndGet(1);
                            Logger.d(TAG, "Still capture image " + imageNum + " available");

                            long start = System.currentTimeMillis();

                            Image image = reader.acquireLatestImage();

                            // TODO Save for now
                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            byte[] bytes = new byte[buffer.capacity()];
                            buffer.get(bytes);
                            saveImage(bytes);

                            // You need to close the image or you will exceed READER_MAX_IMAGES
                            image.close();
                            long total = (System.currentTimeMillis() - start);

                            Logger.d(TAG, "Acquired image " + imageNum + " in " + total + "ms");
                        }
                    };

            mStillReader.setOnImageAvailableListener(readerListener, mHandler);

            mCaptureThread = new HandlerThread("Image Capture Thread");
            mCaptureThread.start();
            mCaptureHandler = new Handler(mCaptureThread.getLooper());

            captureImage();
        }
        catch (Exception e)
        {
            // TODO makeToast("Unable to create camera capture request");
            Logger.e(TAG, "Unable to create camera capture request", e);
        }
    }

    private void captureImage()
    {
        try
        {
            if (mCameraReady.get())
            {
                Logger.d(TAG, "Initiating still capture");
                int captureId = mCameraCaptureSession.capture(mStillCaptureRequest, mStillCaptureCallback, mCaptureHandler);
                Logger.d(TAG, "Capture ID " + captureId);
            }
        }
        catch (Exception e)
        {
            Logger.e(TAG, "Failed to capture image", e);
        }
    }

    private CaptureRequest.Builder getStillCaptureRequestBuilder() throws CameraAccessException
    {
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraDevice.getId());
        CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        mDecoCameraCharacteristics.setupCaptureRequest(mCaptureBuilderProperties, captureRequestBuilder);

        /*
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

        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraCharacteristics.CONTROL_AE_MODE_OFF);
        if (exposure != null)
        {
            Logger.i(TAG, "Setting exposure time to " + exposure);
            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure);
        }

        Range<Integer> iso_range = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (iso_range != null)
        {
            Logger.i(TAG, "Setting ISO to " + iso_range.getUpper());
            captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso_range.getUpper());
        }
        else
        {
            Logger.i(TAG, "Sensor info sensitivity range is not defined for this device");
            Integer maxAnalogSensitivity = characteristics.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY);
            if (maxAnalogSensitivity != null)
            {
                Logger.i(TAG, "Setting ISO to " + maxAnalogSensitivity);
                captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, maxAnalogSensitivity);
            }
            else
            {
                Logger.i(TAG, "Sensor max sensitivity is not defined for this device");
            }
        }

        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        captureRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);
        captureRequestBuilder.set(CaptureRequest.BLACK_LEVEL_LOCK, false);// without it unlocked it might cause issues
        captureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, Byte.valueOf((byte) 100));
        */

        if (mLocation != null)
        {
            captureRequestBuilder.set(CaptureRequest.JPEG_GPS_LOCATION, mLocation);
        }

        captureRequestBuilder.addTarget(mStillReader.getSurface());

        return captureRequestBuilder;
    }

    private void saveImage(final byte[] imageBytes)
    {
        Logger.d(TAG, "Saving image");
        try
        {
            Date now = new Date();

            final String tmDevice, tmSerial, androidId;
            final TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            tmDevice = "" + tm.getDeviceId();
            tmSerial = "" + tm.getSimSerialNumber();
            androidId = "" + android.provider.Settings.Secure.getString(mContext.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

            UUID deviceUuid = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());
            String deviceId = deviceUuid.toString();

            File dir = new File(Environment.getExternalStorageDirectory(), mContext.getResources().getString(R.string.app_name));
            dir = new File(dir, IMAGE_DIR_FORMAT.format(now)+"_"+deviceId);
            dir.mkdirs();

            String imageName = deviceId+"_"+IMAGE_FILE_FORMAT.format(now) + ".jpg";

            File file = new File(dir, imageName);
            Logger.d(TAG, "Image " + file.getAbsolutePath());

            FileOutputStream output = new FileOutputStream(file);
            output.write(imageBytes);
            output.close();

            Logger.d(TAG, "Saved image");

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

package edu.wisc.physics.wipac.deco.camera.v2;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.util.Size;

import com.test.cameraapp.R;

import edu.wisc.physics.wipac.deco.camera.CameraCaptureSessionState;
import edu.wisc.physics.wipac.deco.camera.CameraDeviceException;
import edu.wisc.physics.wipac.deco.camera.CameraDeviceManager;
import edu.wisc.physics.wipac.deco.camera.CameraDeviceState;
import edu.wisc.physics.wipac.deco.camera.CameraOutputSize;

/**
 * Created by andrewbiewer on 4/13/15.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2DeviceManager extends CameraDeviceManager
{
    private static final String TAG = "Camera2DeviceManager";

    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;

    private CameraDevice.StateCallback mCameraDeviceStateCallback =
        new CameraDevice.StateCallback()
        {
            @Override
            public void onOpened(CameraDevice camera)
            {
                onCameraDeviceOpened(camera);
            }

            @Override
            public void onDisconnected(CameraDevice camera)
            {
                onCameraDeviceDisconnected(camera);
            }

            @Override
            public void onError(CameraDevice camera, int error)
            {
                onCameraDeviceError(camera, error);
            }
        };

    public Camera2DeviceManager(Context context, Handler handler) { super(context, handler); }

    @Override
    public void openCamera() throws CameraDeviceException
    {
        if (mCameraDevice != null)
        {
            Log.e(TAG, "Camera already open");
            throw new CameraDeviceException(getContext().getString(R.string.camera_already_open));
        }

        Log.i(TAG, "Opening camera");
        setCameraDeviceState(CameraDeviceState.OPENING);

        try
        {
            CameraManager cameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);

            String[] cameraIds = cameraManager.getCameraIdList();
            if (cameraIds == null)
            {
                Log.e(TAG, "No cameras found");
                throw new CameraDeviceException(getContext().getString(R.string.no_cameras_found));
            }

            for (String cameraId : cameraIds)
            {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK)
                {
                    cameraManager.openCamera(cameraId, mCameraDeviceStateCallback, getHandler());
                    return;
                }
            }

            Log.e(TAG, "No back facing camera");
            throw new CameraDeviceException(getContext().getString(R.string.no_back_facing_camera));
        }
        catch (CameraDeviceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            Log.e(TAG, "Failed to open camera", e);
            throw new CameraDeviceException(getContext().getString(R.string.failed_to_open_camera), e);
        }
    }

    @Override
    public void closeCamera() throws CameraDeviceException
    {
        if (mCameraDevice != null)
        {
            Log.i(TAG, "Closing camera");
            mCameraDevice.close();
            mCameraDevice = null;
            Log.i(TAG, "Camera closed");
        }

        setCameraDeviceState(CameraDeviceState.CLOSED);
    }

    @Override
    public CameraOutputSize getCameraOutputSize(Class<?> outputClass) throws CameraDeviceException
    {
        if (mCameraDevice == null)
        {
            throw new CameraDeviceException(getContext().getString(R.string.camera_is_closed));
        }

        try
        {
            CameraManager cameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(mCameraDevice.getId());
            StreamConfigurationMap scalerStreamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (scalerStreamConfigurationMap == null)
            {
                Log.e(TAG, "Unable to determine camera output size - CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP is null");
                throw new CameraDeviceException(getContext().getString(R.string.failed_to_determine_output_size));
            }
            else
            {
                Size[] outputSizes = scalerStreamConfigurationMap.getOutputSizes(outputClass);
                if (outputSizes == null)
                {
                    Log.e(TAG, "Unable to determine camera output size - output sizes null");
                    throw new CameraDeviceException(getContext().getString(R.string.failed_to_determine_output_size));
                }

                return new CameraOutputSize(outputSizes[0].getWidth(), outputSizes[0].getHeight());
            }
        }
        catch (CameraAccessException e)
        {
            Log.e(TAG, "Unable to determine camera output size", e);
            throw new CameraDeviceException(getContext().getString(R.string.failed_to_determine_output_size));
        }
    }

    private void onCameraDeviceOpened(CameraDevice cameraDevice)
    {
        Log.d(TAG, "Camera open");
        mCameraDevice = cameraDevice;
        setCameraDeviceState(CameraDeviceState.OPEN);
    }

    private void onCameraDeviceDisconnected(CameraDevice cameraDevice)
    {
        Log.d(TAG, "Camera disconnected");
        try
        {
            closeCamera();
        }
        catch (CameraDeviceException e)
        {
            Log.e(TAG, getContext().getString(R.string.error_closing_camera), e);
        }
    }

    private void onCameraDeviceError(CameraDevice cameraDevice, int error)
    {
        Log.e(TAG, "Camera error - " + error);
        try
        {
            closeCamera();
        }
        catch (CameraDeviceException e)
        {
            Log.e(TAG, getContext().getString(R.string.error_closing_camera), e);
        }
    }

    @Override
    public void createCaptureSession() throws CameraDeviceException
    {
        // Create a capture session with a set of Surfaces using CameraManager#createCaptureSession(List, ...)
        try
        {
            setCameraCaptureSessionState(CameraCaptureSessionState.CONFIGURING);
            mCameraDevice.createCaptureSession(
                    null, // TODO getCameraSurfaces(),
                    new CameraCaptureSession.StateCallback()
                    {
                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession)
                        {
                            onCameraCaptureSessionConfigured(cameraCaptureSession);
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession)
                        {
                            onCameraCaptureSessionConfigureFailed(cameraCaptureSession);
                        }
                    },
                    getHandler());
        }
        catch (Exception e)
        {
            Log.e(TAG, "Unable to create camera capture session", e);
            throw new CameraDeviceException(getContext().getString(R.string.failed_to_create_camera_capture_session));
        }
    }

    private void onCameraCaptureSessionConfigured(CameraCaptureSession cameraCaptureSession)
    {
        Log.i(TAG, "Camera capture session configured");
        mCameraCaptureSession = cameraCaptureSession;
        setCameraCaptureSessionState(CameraCaptureSessionState.CONFIGURED);
    }

    private void onCameraCaptureSessionConfigureFailed(CameraCaptureSession cameraCaptureSession)
    {
        Log.e(TAG, "Failed to configure camera capture session");
        mCameraCaptureSession = cameraCaptureSession;
        setCameraCaptureSessionState(CameraCaptureSessionState.CONFIGURE_FAILED);
    }
}

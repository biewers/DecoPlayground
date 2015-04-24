package edu.wisc.physics.wipac.deco.camera;

import android.content.Context;
import android.os.Build;
import android.os.Handler;

import edu.wisc.physics.wipac.deco.camera.v1.Camera1DeviceManager;
import edu.wisc.physics.wipac.deco.camera.v2.Camera2DeviceManager;

/**
 * Do not use any Camera2 APIs in this class.
 *
 * Created by andrewbiewer on 4/13/15.
 */
public abstract class CameraDeviceManager
{
    private static CameraDeviceManager instance;

    public static CameraDeviceManager getManager(Context context)
    {
        return getManager(context, new Handler());
    }

    public static CameraDeviceManager getManager(Context context, Handler handler)
    {
        if (instance == null)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            {
                instance = new Camera2DeviceManager(context, handler);
            }
            else
            {
                instance = new Camera1DeviceManager(context, handler);
            }
        }

        return instance;
    }

    private Context mContext;
    private Handler mHandler;
    private CameraDeviceState mCameraDeviceState;
    private CameraCaptureSessionState mCameraCaptureSessionState;

    public CameraDeviceManager(Context context, Handler handler)
    {
        mContext = context;
        mHandler = handler;
        mCameraDeviceState = CameraDeviceState.CLOSED;
        mCameraCaptureSessionState = CameraCaptureSessionState.NOT_CONFIGURED;
    }

    protected Context getContext() { return mContext; }
    protected Handler getHandler() { return mHandler; }
    protected void setCameraDeviceState(CameraDeviceState cameraDeviceState) { mCameraDeviceState = cameraDeviceState; }
    protected void setCameraCaptureSessionState(CameraCaptureSessionState cameraCaptureSessionState) { mCameraCaptureSessionState = cameraCaptureSessionState; }

    public CameraDeviceState getCameraDeviceState() { return mCameraDeviceState; }
    public CameraCaptureSessionState getCameraCaptureSessionState() { return mCameraCaptureSessionState; }

    public abstract void openCamera() throws CameraDeviceException;
    public abstract void closeCamera() throws CameraDeviceException;
    public abstract CameraOutputSize getCameraOutputSize(Class<?> outputClass) throws CameraDeviceException;
    public abstract void createCaptureSession() throws CameraDeviceException;
}

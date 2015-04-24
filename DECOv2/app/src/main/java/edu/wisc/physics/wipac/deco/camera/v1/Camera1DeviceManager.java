package edu.wisc.physics.wipac.deco.camera.v1;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Handler;

import edu.wisc.physics.wipac.deco.camera.CameraDeviceException;
import edu.wisc.physics.wipac.deco.camera.CameraDeviceManager;
import edu.wisc.physics.wipac.deco.camera.CameraOutputSize;

/**
 * Created by andrewbiewer on 4/13/15.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class Camera1DeviceManager extends CameraDeviceManager
{
    public Camera1DeviceManager(Context context, Handler handler) { super(context, handler);
    }

    @Override
    public void openCamera()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void closeCamera()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public CameraOutputSize getCameraOutputSize(Class<?> outputClass) throws CameraDeviceException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void createCaptureSession() throws CameraDeviceException
    {
        throw new UnsupportedOperationException();
    }
}

package edu.wisc.physics.wipac.deco.camera;

/**
 * Created by andrewbiewer on 4/13/15.
 */
public class CameraDeviceException extends Exception
{
    public CameraDeviceException(String detailMessage)
    {
        super(detailMessage);
    }

    public CameraDeviceException(String detailMessage, Throwable throwable)
    {
        super(detailMessage, throwable);
    }
}

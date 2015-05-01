package edu.wisc.physics.wipac.deco.service;

/**
 * Created by andrewbiewer on 4/29/15.
 */
public interface CameraCaptureStateCallback
{
    void onCameraOpen();
    void onCameraClosed();
    void onExposureSet(Long maximumExposure);
    void onImageCaptured(ImageInfo imageInfo);
    void onCaptureCompleted(Long actualExposure);
}

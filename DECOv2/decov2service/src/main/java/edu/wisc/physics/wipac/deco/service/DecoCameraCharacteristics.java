package edu.wisc.physics.wipac.deco.service;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Created by andrewbiewer on 6/1/15.
 */
public class DecoCameraCharacteristics
{
    private static final String TAG = "DecoCameraCharacteristi";

    private Context context;
    private Map<String, Characteristic> mapCharacteristicNameToKey = new HashMap<>();

    public DecoCameraCharacteristics(Context context, String cameraId) throws CameraAccessException
    {
        this.context = context;
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
        List<CaptureRequest.Key<?>> captureRequestKeys = cameraCharacteristics.getAvailableCaptureRequestKeys();
        for (CaptureRequest.Key<?> captureRequestKey : captureRequestKeys)
        {
            CaptureRequest.Key<Object> key = (CaptureRequest.Key<Object>) captureRequestKey;
            try
            {
                mapCharacteristicNameToKey.put(captureRequestKey.getName(),
                        new Characteristic(key, getType(captureRequestKey)));
            }
            catch (Exception e)
            {
                Logger.e(TAG, "Failed to setup capture request characteristic map", e);
            }
        }
    }

    public void setupCaptureRequest(Properties decoCameraProps, CaptureRequest.Builder captureRequestBuilder)
    {
        for (String propertyName : decoCameraProps.stringPropertyNames())
        {
            String propertyValue = decoCameraProps.getProperty(propertyName);
            setCharacteristic(captureRequestBuilder, propertyName, propertyValue);
        }
    }

    private void setCharacteristic(CaptureRequest.Builder captureRequestBuilder, String propertyName, String propertyValue)
    {
        Characteristic characteristic = mapCharacteristicNameToKey.get(propertyName);
        if (characteristic != null)
        {
            captureRequestBuilder.set(characteristic.key, convertValue(characteristic.type, propertyValue));
        }
        else
        {
            Logger.e(TAG, "No such property \"" + propertyName + "\"");
        }
    }

    private class Characteristic
    {
        public CaptureRequest.Key<Object> key;
        public Class<?> type;

        public Characteristic(
                CaptureRequest.Key<Object> key,
                Class<?> type)
        {
            this.key = key;
            this.type = type;
        }
    }

    private Class<?> getType(CaptureRequest.Key<?> key) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException
    {
        // OK, so here's the thing. getNativeKey() is not a documented method, but there
        // is no other way to determine the type of the characteristic key...well, other
        // than maintaining a complete list of keys and their types which, quite honestly,
        // is just as ugly IMHO. (BTW, Java reflection rocks.)
        Method getNativeKey = key.getClass().getMethod("getNativeKey");
        Object nativeKey = getNativeKey.invoke(key);
        Method getType = nativeKey.getClass().getMethod("getType");
        Class<?> type =  (Class<?>) getType.invoke(nativeKey);
        Logger.d(TAG, "CaptureRequest.Key " + key.getName() + "[" + type + "]");
        return type;
    }

    private Object convertValue(Class<?> type, String value)
    {
        if (int.class.equals(type))
        {
            return Integer.parseInt(value);
        }
        else if (boolean.class.equals(type))
        {
            return Boolean.parseBoolean(value);
        }
        else if (byte.class.equals(type))
        {
            return Byte.parseByte(value);
        }
        else if (float.class.equals(type))
        {
            return Float.parseFloat(value);
        }

        return value;
    }
}

package edu.wisc.physics.wipac.deco.camera;

/**
 * Created by andrewbiewer on 4/13/15.
 */
public class CameraOutputSize
{
    private int width;
    private int height;

    public CameraOutputSize(int width, int height)
    {
        this.width = width;
        this.height = height;
    }

    public int getWidth()
    {
        return width;
    }

    public int getHeight()
    {
        return height;
    }
}

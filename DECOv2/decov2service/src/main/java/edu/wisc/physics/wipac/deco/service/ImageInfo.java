package edu.wisc.physics.wipac.deco.service;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by andrewbiewer on 4/29/15.
 */
public class ImageInfo implements Parcelable
{
    private String name;
    private Long size;
    private Long exposure;

    public ImageInfo(String name, Long size, Long exposure)
    {
        this.name = name;
        this.size = size;
        this.exposure = exposure;
    }

    public String getName()
    {
        return name;
    }

    public Long getSize()
    {
        return size;
    }

    public Long getExposure()
    {
        return exposure;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        if (name != null)
        {
            sb.append("Image " + name);
        }

        if (size != null)
        {
            if (sb.length() > 0) sb.append(" ");
            sb.append("size " + size);
        }

        if (exposure != null)
        {
            if (sb.length() > 0) sb.append(" ");
            sb.append("exposure " + exposure);
        }

        return sb.toString();
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(name);
        dest.writeValue(size);
        dest.writeValue(exposure);
    }

    public static final Parcelable.Creator<ImageInfo> CREATOR =
        new Parcelable.Creator<ImageInfo>()
        {
            public ImageInfo createFromParcel(Parcel in)
            {
                return new ImageInfo(in);
            }

            public ImageInfo[] newArray(int size)
            {
                return new ImageInfo[size];
            }
        };

    private ImageInfo(Parcel in)
    {
        name = in.readString();
        size = (Long) in.readValue(ImageInfo.class.getClassLoader());
        exposure = (Long) in.readValue(ImageInfo.class.getClassLoader());
    }
}

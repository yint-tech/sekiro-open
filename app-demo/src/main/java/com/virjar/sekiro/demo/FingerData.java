package com.virjar.sekiro.demo;


import android.os.Parcel;
import android.os.Parcelable;

public class FingerData implements Parcelable {
    private String imei = "";
    private String serial = "";
    private double latitude;
    private double longitude;

    public FingerData() {
    }

    public FingerData(Parcel in) {
        imei = in.readString();
        serial = in.readString();
        latitude = in.readDouble();
        longitude = in.readDouble();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(imei);
        dest.writeString(serial);
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<FingerData> CREATOR = new Creator<FingerData>() {
        @Override
        public FingerData createFromParcel(Parcel in) {
            return new FingerData(in);
        }

        @Override
        public FingerData[] newArray(int size) {
            return new FingerData[size];
        }
    };

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
}
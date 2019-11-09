package com.pjinkim.sensors_data_logger;

public class IMUConfig implements java.io.Serializable {

    // properties
    private Boolean mIsFileEnabled = true;
    private String mFolderPrefix = "";
    private String mFolderSuffix = "";


    private int mStartDelay = 0;
    private long mReferenceTimestamp = 0;
    private String mOutputFolder = "";


    // getter and setter
    public Boolean getFileEnabled() {
        return this.mIsFileEnabled;
    }

    public int getStartDelay() {
        return this.mStartDelay;
    }

    public long getReferenceTimestamp() {
        return this.mReferenceTimestamp;
    }

    public String getOutputFolder() {
        return mOutputFolder;
    }

    public void setOutputFolder(final String folder) {
        mOutputFolder = folder;
    }

    public String getFolderPrefix() {
        return this.mFolderPrefix;
    }

    public void setFileEnabled(Boolean v) {
        this.mIsFileEnabled = v;
    }

    public void setStartDelay(int v) {
        this.mStartDelay = v;
    }

    public void setFolderPrefix(String v) {
        this.mFolderPrefix = v;
    }

    public void setReferenceTimestamp(long v) {
        this.mReferenceTimestamp = v;
    }

    //Add _ separator like before when activites and output name is decided.
    // It was in the format "_" + variable
    public void setFolderSuffix(String v){ this.mFolderSuffix = (v); }

    public String getSuffix() {
        return mFolderSuffix;
    }
}

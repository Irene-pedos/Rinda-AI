package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public enum DataOrigin {
    @SerializedName("GEE")
    GEE,
    @SerializedName("SAMPLE_FILE")
    SAMPLE_FILE
}

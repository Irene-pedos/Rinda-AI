package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public enum Role {
    @SerializedName("FARMER")
    FARMER,
    @SerializedName("AGENT")
    AGENT,
    @SerializedName("OFFICER")
    OFFICER,
    @SerializedName("ADMIN")
    ADMIN
}

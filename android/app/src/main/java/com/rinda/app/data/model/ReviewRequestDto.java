package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public class ReviewRequestDto {
    @SerializedName("message")
    private final String message;

    public ReviewRequestDto(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}

package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class ApiProblem {
    @SerializedName("type")
    private String type;

    @SerializedName("title")
    private String title;

    @SerializedName("status")
    private Integer status;

    @SerializedName("detail")
    private String detail;

    @SerializedName("instance")
    private String instance;

    @SerializedName("code")
    private String code;

    @SerializedName("fields")
    private Map<String, String> fields;

    public ApiProblem() {
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public Integer getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public String getInstance() {
        return instance;
    }

    public String getCode() {
        return code != null ? code : "INTERNAL_ERROR";
    }

    public Map<String, String> getFields() {
        return fields;
    }
}

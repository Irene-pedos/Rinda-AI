package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;

public class PageResponse<T> {
    @SerializedName("items")
    private List<T> items;

    @SerializedName("page")
    private int page;

    @SerializedName("size")
    private int size;

    @SerializedName("totalItems")
    private long totalItems;

    @SerializedName("totalPages")
    private int totalPages;

    public PageResponse() {
    }

    public List<T> getItems() {
        return items != null ? items : Collections.emptyList();
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalItems() {
        return totalItems;
    }

    public int getTotalPages() {
        return totalPages;
    }
}

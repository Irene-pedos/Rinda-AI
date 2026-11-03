package com.rinda.backend.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static final int MAX_SIZE = 100;

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        return new PageResponse<>(items, page, size, totalItems, (int) ((totalItems + size - 1) / size));
    }

    public static void check(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAGE_INVALID",
                    "page must be 0 or more, and size between 1 and " + MAX_SIZE + ".");
        }
    }
}

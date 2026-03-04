package com.banking.common.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PagedResponse<T>(
        boolean         success,
        String          message,
        List<T>         data,
        int             page,
        int             size,
        long            totalElements,
        int             totalPages,
        boolean         last,
        String          errorCode,
        LocalDateTime   timestamp
) {
    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
                true, "Success",
                page.getContent(),
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(),
                page.isLast(),
                null, LocalDateTime.now()
        );
    }
}

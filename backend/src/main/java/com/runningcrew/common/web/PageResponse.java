package com.runningcrew.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 목록 응답 공통 래퍼(계약 conventions.md §6, offset 기반).
 *
 * <p>JSON 필드는 snake_case: {@code items, page, size, total_elements, total_pages}.
 *
 * @param <T> 요소 타입(각 목록 계약에서 정의)
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    /** Spring Data {@link Page}를 계약 래퍼로 변환한다. */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}

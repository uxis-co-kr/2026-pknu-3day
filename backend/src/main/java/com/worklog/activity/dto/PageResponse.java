package com.worklog.activity.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 페이지 래퍼 (PRD 7). 목록 응답 중 /activities 만 이 형태를 쓰고 나머지는 배열이다.
 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public static <E, T> PageResponse<T> of(Page<E> source, List<T> items) {
        return new PageResponse<>(items, source.getNumber(), source.getSize(), source.getTotalElements());
    }
}

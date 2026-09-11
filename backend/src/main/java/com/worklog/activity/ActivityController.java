package com.worklog.activity;

import com.worklog.auth.AuthenticatedUser;
import com.worklog.auth.DataScope;
import com.worklog.config.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.worklog.activity.dto.ActivityDetailResponse;
import com.worklog.activity.dto.ActivityResponse;
import com.worklog.activity.dto.PageResponse;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 활동 조회 (PRD 7. /activities ★ — JWT / API Key 둘 다 허용).
 */
@RestController
@RequestMapping("/activities")
public class ActivityController {

    private final ActivityQueryService queryService;

    public ActivityController(ActivityQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public PageResponse<ActivityResponse> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long repoId,
            @RequestParam(required = false) ActivityType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        // MEMBER 는 자기 것만 (DataScope). 주인 없는 활동(미가입 기여자)은 MEMBER 에게 안 보인다.
        Long scoped = DataScope.userIdFor(principal, userId);
        Page<Activity> result = queryService.search(
                new ActivityQueryService.ActivityQuery(date, from, to, scoped, repoId, type, page, size));
        return PageResponse.of(result, result.getContent().stream().map(ActivityResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ActivityDetailResponse get(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        Activity activity = queryService.get(id);
        if (!DataScope.canSee(principal, activity.getUser() == null ? null : activity.getUser().getId())) {
            throw ApiException.notFound("ACTIVITY_NOT_FOUND", "활동을 찾을 수 없습니다.");
        }
        return ActivityDetailResponse.from(activity);
    }
}

package com.worklog.draft;

import com.worklog.auth.AuthenticatedUser;
import com.worklog.config.ApiException;
import com.worklog.draft.dto.DraftDetailResponse;
import com.worklog.draft.dto.DraftSummaryResponse;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 초안 조회·수정·확정 (PRD 7. /drafts — F3b, 담당자 1).
 *
 * <p>생성({@code POST /drafts/generate})은 담당자 2의 {@link DraftGenerateController} 에 있다.
 * 같은 {@code /drafts} 경로를 두 파일이 나눠 맡는다 (PRD 2. 컨플릭트 방지).
 */
@RestController
@RequestMapping("/drafts")
public class DraftController {

    private final DraftService draftService;

    public DraftController(DraftService draftService) {
        this.draftService = draftService;
    }

    /**
     * 초안 목록. {@code date} 하나를 주면 그날, {@code from}·{@code to} 를 주면 그 기간이다.
     *
     * <p>기간 조회는 업무 일지 목록 화면이 쓴다 — 날짜 선택기로 하루씩 넘기지 않고
     * 여러 날을 한 화면에서 본다. 기존 호출부는 {@code date} 를 그대로 쓰면 된다.
     */
    @GetMapping
    public List<DraftSummaryResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) DraftStatus status) {
        if (date == null && (from == null || to == null)) {
            throw ApiException.badRequest(
                    "DATE_REQUIRED", "date 또는 from·to 를 함께 주어야 합니다.");
        }
        if (date == null) {
            return draftService.listBetween(from, to, userId, status);
        }
        return draftService.list(date, userId, status);
    }

    @GetMapping("/{id}")
    public DraftDetailResponse detail(@PathVariable Long id) {
        return draftService.detail(id);
    }

    @PatchMapping("/{id}")
    public DraftDetailResponse update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @RequestBody @jakarta.validation.Valid UpdateRequest request) {
        return draftService.updateContent(id, principal.id(), request.contentMd());
    }

    @PostMapping("/{id}/confirm")
    public DraftDetailResponse confirm(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return draftService.confirm(id, principal.id());
    }

    /** 빈 문자열은 허용한다 — 사용자가 본문을 비워 저장할 수 있다. null 만 막는다. */
    public record UpdateRequest(@NotNull String contentMd) {}
}

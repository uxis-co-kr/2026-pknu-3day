package com.worklog.vscode;

import com.worklog.auth.AuthenticatedUser;
import com.worklog.config.ApiException;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * VS Code 확장 수신·조회 (PRD 7. /vscode/sessions).
 *
 * <p>POST 는 SecurityConfig 에서 {@code X-Api-Key} 로만 열려 있다. JWT 로 부르면 403 이 나므로
 * 컨트롤러에는 별도 설정이 없다. GET 은 대시보드가 쓰는 ★ 경로라 두 수단 모두 받는다.
 */
@RestController
@RequestMapping("/vscode/sessions")
public class VscodeSessionController {

    private final VscodeSessionService service;

    public VscodeSessionController(VscodeSessionService service) {
        this.service = service;
    }

    @PostMapping
    public UpsertResponse upsert(
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestBody @Valid SessionRequest request) {
        return new UpsertResponse(service.upsert(principal.id(), request).getId());
    }

    /**
     * 세션 목록. {@code date} 하나를 주면 그날, {@code from}·{@code to} 를 주면 그 기간이다.
     *
     * <p>기간 조회는 VSCode 내역 화면이 쓴다 — 날짜 선택기로 하루씩 넘기지 않고 달 단위로
     * 본다 (BACKLOG2 §2-3). {@code GET /drafts} 와 같은 규칙이라 부르는 쪽이 헷갈리지 않는다.
     * 기존 호출부는 {@code date} 를 그대로 쓰면 된다.
     *
     * <p>{@code mine=true} 는 <b>부르는 사람 자신</b>의 것만 준다. VS Code 확장이 쓴다 —
     * 확장은 API Key 만 들고 있어 제 {@code userId} 를 모르는데, 빼고 부르면 그날 팀 전원의
     * 기록이 내려간다.
     */
    @GetMapping
    public List<SessionResponse> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "false") boolean mine) {
        if (date == null && (from == null || to == null)) {
            throw ApiException.badRequest("DATE_REQUIRED", "date 또는 from·to 를 함께 주어야 합니다.");
        }
        Long owner = userId;
        if (mine) {
            if (principal == null) {
                throw ApiException.forbidden("AUTH_REQUIRED", "mine=true 는 로그인이 필요합니다.");
            }
            owner = principal.id();
        }
        List<VscodeSession> found = date == null
                ? service.findBetween(from, to, owner)
                : service.findForDay(date, owner);
        return found.stream().map(SessionResponse::from).toList();
    }

    /** PRD 7. — 200 {id}. */
    public record UpsertResponse(Long id) {}
}

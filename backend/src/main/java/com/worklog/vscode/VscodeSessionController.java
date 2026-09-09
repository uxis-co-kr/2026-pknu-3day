package com.worklog.vscode;

import com.worklog.auth.AuthenticatedUser;
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

    @GetMapping
    public List<SessionResponse> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long userId) {
        return service.findForDay(date, userId).stream().map(SessionResponse::from).toList();
    }

    /** PRD 7. — 200 {id}. */
    public record UpsertResponse(Long id) {}
}

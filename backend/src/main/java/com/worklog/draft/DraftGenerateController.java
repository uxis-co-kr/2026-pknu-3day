package com.worklog.draft;

import com.worklog.auth.AuthenticatedUser;
import com.worklog.config.ApiException;
import com.worklog.config.KstDates;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 초안 생성 (PRD 7. POST /drafts/generate — F3a, 담당자 2).
 *
 * <p><b>담당자 1의 {@code DraftController}(조회·수정·확정)와 파일을 나눠 둔다.</b>
 * {@code draft/} 는 두 트랙이 겹치는 유일한 패키지라 같은 파일을 건드리면 충돌한다 (PRD 2).
 */
@RestController
@RequestMapping("/drafts")
public class DraftGenerateController {

    private final DraftGenerator draftGenerator;
    private final PeriodDraftGenerator periodGenerator;

    public DraftGenerateController(DraftGenerator draftGenerator, PeriodDraftGenerator periodGenerator) {
        this.draftGenerator = draftGenerator;
        this.periodGenerator = periodGenerator;
    }

    /**
     * 주간 업무일지 AI 생성 (V15). 그 기간의 하루치 일지를 묶어 다시 쓴다.
     *
     * <p>하루치와 달리 204 가 없다 — 재료가 없으면 400 으로 무엇이 없는지 말한다. 빈 일지를
     * 만들어 두면 사람이 지워야 한다.
     */
    @PostMapping("/generate/weekly")
    public ResponseEntity<GenerateResponse> generateWeekly(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody PeriodRequest request) {
        Long userId = request.userId() == null ? principal.id() : request.userId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GenerateResponse.from(periodGenerator.weekly(userId, request.from(), request.to())));
    }

    /**
     * 저장소별 업무일지 AI 생성 (V15). 그 기간 그 저장소의 커밋·PR 을 묶어 쓴다.
     *
     * @param request {@code mineOnly} 가 true 면 내 활동만, false 면 그 저장소의 팀 전체
     */
    @PostMapping("/generate/repo")
    public ResponseEntity<GenerateResponse> generateRepo(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody PeriodRequest request) {
        Long userId = request.userId() == null ? principal.id() : request.userId();
        if (request.repoId() == null) {
            throw ApiException.badRequest("REPO_REQUIRED", "저장소를 골라 주세요.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(GenerateResponse.from(
                periodGenerator.byRepo(userId, request.repoId(), request.from(), request.to(), request.mineOnly())));
    }

    /** 주간·저장소별 생성 요청. {@code repoId} 는 저장소별에만 쓴다. */
    public record PeriodRequest(LocalDate from, LocalDate to, Long userId, Long repoId, boolean mineOnly) {}

    /**
     * @return 201 + 생성된 초안, 활동이 없으면 204 (PRD F3)
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> generate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody(required = false) GenerateRequest request) {

        LocalDate date = request == null || request.date() == null ? KstDates.today() : request.date();
        // userId 를 생략하면 본인 (PRD 7).
        Long userId = request == null || request.userId() == null ? principal.id() : request.userId();

        Optional<Draft> draft = draftGenerator.generate(userId, date);
        return draft.map(d -> ResponseEntity.status(HttpStatus.CREATED).body(GenerateResponse.from(d)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    public record GenerateRequest(LocalDate date, Long userId) {}

    public record GenerateResponse(
            Long id,
            Long userId,
            LocalDate workDate,
            Integer version,
            String status,
            String contentMd,
            Long[] sourceActivityIds,
            Long[] sourceSessionIds) {

        static GenerateResponse from(Draft d) {
            return new GenerateResponse(
                    d.getId(),
                    d.getUser().getId(),
                    d.getWorkDate(),
                    d.getVersion(),
                    d.getStatus().name(),
                    d.getContentMd(),
                    d.getSourceActivityIds(),
                    d.getSourceSessionIds());
        }
    }
}

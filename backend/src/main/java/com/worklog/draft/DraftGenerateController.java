package com.worklog.draft;

import com.worklog.auth.AuthenticatedUser;
import com.worklog.auth.DataScope;
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
     * 주간 업무일지 AI 생성 (V15). 고른 날이 든 <b>그 주(월~일)</b> 의 하루치 일지를 묶어 다시 쓴다.
     *
     * <p>같은 주에 다시 만들면 새 일지가 아니라 <b>버전이 올라간다</b> — 하루치와 같다.
     * 목록에는 그 주의 최신 것 하나만 남는다.
     *
     * <p>하루치와 달리 204 가 없다 — 재료가 없으면 400 으로 무엇이 없는지 말한다. 빈 일지를
     * 만들어 두면 사람이 지워야 한다.
     */
    @PostMapping("/generate/weekly")
    public ResponseEntity<GenerateResponse> generateWeekly(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody PeriodRequest request) {
        Long userId = targetUser(principal, request.userId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GenerateResponse.from(periodGenerator.weekly(userId, request.week())));
    }

    /**
     * 저장소별 업무일지 AI 생성 (V15). 고른 날이 든 그 주(월~일) 그 저장소의 커밋·PR 을 묶어 쓴다.
     *
     * <p>주간과 같은 단위다 — 저장소마다 주에 하나씩 이어지고, 다시 만들면 버전이 올라간다.
     *
     * <p><b>관리자만 만든다 (9/11 결정).</b> 저장소별은 한 사람의 일지가 아니라 그 저장소에서
     * 팀이 무엇을 했는지를 본다. 사원 개인 화면에 두면 남의 활동까지 묶어 보게 된다.
     *
     * @param request {@code mineOnly} 가 true 면 내 활동만, false 면 그 저장소의 팀 전체
     */
    @PostMapping("/generate/repo")
    public ResponseEntity<GenerateResponse> generateRepo(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody PeriodRequest request) {
        // 저장소별은 관리자만 만든다 (9/11) — 한 사람의 일지가 아니라 그 저장소의 팀 활동이다.
        if (!principal.isAdmin()) {
            throw ApiException.forbidden("ADMIN_ONLY", "저장소별 업무일지는 관리자만 만들 수 있습니다.");
        }
        // 대상은 조회와 같은 자로 고른다 — 생략하면 본인, 남의 id 는 ADMIN 만 (담당자 1).
        Long userId = targetUser(principal, request.userId());
        if (request.repoId() == null) {
            throw ApiException.badRequest("REPO_REQUIRED", "저장소를 골라 주세요.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(GenerateResponse.from(
                periodGenerator.byRepo(userId, request.repoId(), request.week(), request.mineOnly())));
    }

    /**
     * 주간·저장소별 생성 요청.
     *
     * @param date 그 주의 아무 날. 서버가 월~일로 맞춘다. 옛 화면이 보내던 {@code from} 도 받는다
     * @param repoId 저장소별에만 쓴다
     */
    public record PeriodRequest(LocalDate date, LocalDate from, Long userId, Long repoId, boolean mineOnly) {

        LocalDate week() {
            return date != null ? date : from;
        }
    }

    /**
     * @return 201 + 생성된 초안, 활동이 없으면 204 (PRD F3)
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> generate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody(required = false) GenerateRequest request) {

        LocalDate date = request == null || request.date() == null ? KstDates.today() : request.date();
        // userId 를 생략하면 본인 (PRD 7). 남의 id 는 ADMIN 만.
        Long userId = targetUser(principal, request == null ? null : request.userId());

        Optional<Draft> draft = draftGenerator.generate(userId, date);
        return draft.map(d -> ResponseEntity.status(HttpStatus.CREATED).body(GenerateResponse.from(d)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * 누구 이름으로 만들 것인가.
     *
     * <p>생략하면 자기 것이다. <b>남의 id 를 적으면 MEMBER 는 403</b> — 조회는
     * {@link DataScope} 로 막아 두었는데 생성은 열려 있어, 일반 회원이 남의 이름으로 일지를
     * 만들고 <b>그 사람 활동이 담긴 본문까지 응답으로 받아 볼 수 있었다</b> (9/11 점검에서 확인).
     *
     * <p>{@code DataScope.userIdFor} 를 그대로 쓰지 않는 이유는 하나다 — 그쪽은 ADMIN 이
     * 생략하면 null(전원)을 돌려주는데, 생성에는 "전원" 이라는 대상이 없다.
     */
    private static Long targetUser(AuthenticatedUser principal, Long requested) {
        return requested == null ? principal.id() : DataScope.userIdFor(principal, requested);
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

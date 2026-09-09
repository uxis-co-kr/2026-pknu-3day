package com.worklog.draft;

import com.worklog.auth.AuthenticatedUser;
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

    public DraftGenerateController(DraftGenerator draftGenerator) {
        this.draftGenerator = draftGenerator;
    }

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

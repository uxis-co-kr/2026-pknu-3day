package com.worklog.external;

import com.worklog.auth.User;
import com.worklog.draft.Draft;
import com.worklog.draft.DraftRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 시스템(메신저 봇 등)이 API Key 로 가져가는 하루 요약 (PRD F8).
 *
 * <p>Mattermost 슬래시 커맨드 응답에 그대로 쓸 수 있게 사용자별 5줄로 줄인다.
 */
@Service
public class ExternalSummaryService {

    /** 사용자당 노출할 줄 수 (PRD F8). */
    private static final int LINES_PER_USER = 5;

    private final DraftRepository draftRepository;

    public ExternalSummaryService(DraftRepository draftRepository) {
        this.draftRepository = draftRepository;
    }

    @Transactional(readOnly = true)
    public DailySummaryResponse summarize(LocalDate date) {
        List<UserSummary> users = draftRepository.findLatestByWorkDate(date).stream()
                .map(ExternalSummaryService::toSummary)
                .toList();
        return new DailySummaryResponse(date, users);
    }

    private static UserSummary toSummary(Draft draft) {
        User user = draft.getUser();
        return new UserSummary(
                user.getId(),
                user.getLogin(),
                displayName(user),
                draft.getStatus().name(),
                draft.getVersion(),
                highlights(draft.getContentMd()));
    }

    /** "완료한 작업" 항목에서 앞의 몇 줄만. 목록 기호는 떼고 문장만 남긴다. */
    static List<String> highlights(String contentMd) {
        if (contentMd == null) {
            return List.of();
        }
        return contentMd
                .lines()
                .dropWhile(line -> !line.startsWith("## 완료한 작업"))
                .skip(1)
                .takeWhile(line -> line.startsWith("- "))
                .map(line -> line.substring(2).strip())
                .limit(LINES_PER_USER)
                .toList();
    }

    private static String displayName(User user) {
        return user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getLogin();
    }

    public record DailySummaryResponse(LocalDate date, List<UserSummary> users) {}

    public record UserSummary(
            Long userId,
            String login,
            String name,
            String draftStatus,
            Integer draftVersion,
            List<String> highlights) {}
}

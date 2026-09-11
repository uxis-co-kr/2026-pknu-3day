package com.worklog.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityType;
import com.worklog.github.Repo;
import com.worklog.vscode.TodoItem;
import com.worklog.vscode.UncommittedFile;
import com.worklog.vscode.VscodeSession;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DraftTemplateTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 10);

    private static Repo repo() {
        Repo repo = new Repo();
        repo.setFullName("uxis-co-kr/2026-pknu-3day");
        return repo;
    }

    private static Activity commit(String sha, String summary) {
        Activity a = new Activity();
        a.setRepo(repo());
        a.setType(ActivityType.COMMIT);
        a.setExternalId(sha);
        a.setSha(sha);
        a.setTitle("feat: 무언가");
        a.setSummary(summary);
        return a;
    }

    private static Activity pull(ActivityType type, String number, String summary) {
        Activity a = new Activity();
        a.setRepo(repo());
        a.setType(type);
        a.setExternalId(number);
        a.setTitle("PR 제목");
        a.setSummary(summary);
        return a;
    }

    private static VscodeSession session() {
        VscodeSession s = new VscodeSession();
        s.setRemoteUrl("https://github.com/uxis-co-kr/2026-pknu-3day.git");
        s.setBranch("feature/attendance");
        s.setWorkDate(DAY);
        return s;
    }

    @Test
    @DisplayName("PRD F3 의 네 섹션이 순서대로 들어간다")
    void hasAllSections() {
        String md = DraftTemplate.render(DAY, "배태일", List.of(), List.of());

        assertThat(md)
                .startsWith("# 2026-09-10 업무 일지 — 배태일")
                .contains("## 완료한 작업")
                .contains("## 진행 중 / 미커밋")
                .contains("## 계획 / TODO")
                .contains("## 메모");
        assertThat(md.indexOf("## 완료한 작업")).isLessThan(md.indexOf("## 진행 중 / 미커밋"));
        assertThat(md.indexOf("## 계획 / TODO")).isLessThan(md.indexOf("## 메모"));
    }

    @Test
    @DisplayName("커밋 3건이면 완료한 작업에 3줄이 들어간다 (PRD F3 수용 기준)")
    void oneLinePerActivity() {
        String md = DraftTemplate.render(
                DAY,
                "배태일",
                List.of(
                        commit("abc1234567", "출석 API 를 추가했다."),
                        commit("def7654321", "중복 검증을 붙였다."),
                        commit("aaa1111111", "테스트를 보강했다.")),
                List.of());

        List<String> completed = md.lines()
                .dropWhile(l -> !l.startsWith("## 완료한 작업"))
                .skip(1)
                .takeWhile(l -> l.startsWith("- "))
                .toList();

        assertThat(completed).hasSize(3);
        assertThat(completed.get(0))
                .isEqualTo("- [uxis-co-kr/2026-pknu-3day] 출석 API 를 추가했다.  (commit abc1234)");
    }

    @Test
    @DisplayName("PR 은 번호와 머지 여부를 붙인다")
    void formatsPullRequests() {
        String md = DraftTemplate.render(
                DAY,
                "배태일",
                List.of(
                        pull(ActivityType.PR_MERGED, "12", "출석 기능을 병합했다."),
                        pull(ActivityType.PR_OPENED, "13", "리뷰를 요청했다.")),
                List.of());

        assertThat(md)
                .contains("- [uxis-co-kr/2026-pknu-3day] PR #12 머지: 출석 기능을 병합했다.")
                .contains("- [uxis-co-kr/2026-pknu-3day] PR #13 생성: 리뷰를 요청했다.");
    }

    @Test
    @DisplayName("PR 제목에 표기가 이미 들어 있어도 두 번 붙이지 않는다")
    void doesNotDoublePrefix() {
        // 수집기는 GitHub 이 준 제목만 저장하고 표기는 여기서 한 번만 붙인다.
        Activity merged = pull(ActivityType.PR_MERGED, "1", null);
        merged.setTitle("fix: DB 노출 포트를 되돌리고");

        String md = DraftTemplate.render(DAY, "배태일", List.of(merged), List.of());

        assertThat(md).contains("PR #1 머지: fix: DB 노출 포트를 되돌리고");
        assertThat(md).doesNotContain("PR #1 머지: PR #1 머지:");
    }

    @Test
    @DisplayName("요약이 아직 없으면 제목으로 대신한다")
    void fallsBackToTitle() {
        String md = DraftTemplate.render(DAY, "배태일", List.of(commit("abc1234", null)), List.of());

        assertThat(md).contains("feat: 무언가");
    }

    @Test
    @DisplayName("미커밋 세션은 파일 목록으로 한 줄을 만든다")
    void rendersUncommittedFiles() {
        VscodeSession s = session();
        s.setUncommittedFiles(List.of(
                new UncommittedFile("src/api/attendance.ts", 40, 3, "@@"),
                new UncommittedFile("src/api/user.ts", 2, 0, "@@")));

        String md = DraftTemplate.render(DAY, "배태일", List.of(), List.of(s));

        assertThat(md).contains("미커밋 2개 — src/api/attendance.ts, src/api/user.ts");
    }
// TODO: 할일
    @Test
    @DisplayName("계획 문서와 TODO 주석이 계획 섹션에 들어간다")
    void rendersPlansAndTodos() {
        VscodeSession s = session();
        s.setPlanNote("오후에 출석 중복 검증 마무리");
        s.setTodos(List.of(new TodoItem("src/api/attendance.ts", 42, "TODO: 중복 출석 검증")));

        String md = DraftTemplate.render(DAY, "배태일", List.of(), List.of(s));

        assertThat(md)
                .contains("오후에 출석 중복 검증 마무리")
                .contains("- src/api/attendance.ts:42 TODO: 중복 출석 검증");
    }

    @Test
    @DisplayName("계획 문서는 적은 모양 그대로 들어간다 — 문서 한 통이 오늘 계획 하나다")
    void keepsPlanDocumentAsWritten() {
        VscodeSession s = session();
        s.setPlanNote("## 오전\n- 출석 중복 검증 마무리\n  - 테스트 먼저\n\n## 오후\n관리자 콘솔 뼈대");

        String md = DraftTemplate.render(DAY, "배태일", List.of(), List.of(s));

        List<String> plan = md.lines()
                .dropWhile(l -> !l.startsWith("## 계획 / TODO"))
                .skip(1)
                .takeWhile(l -> !l.startsWith("## 메모"))
                .toList();

        assertThat(plan).containsExactly(
                "## 오전", "- 출석 중복 검증 마무리", "  - 테스트 먼저", "", "## 오후", "관리자 콘솔 뼈대", "");
    }

    @Test
    @DisplayName("같은 폴더의 다른 브랜치가 같은 계획 문서를 들고 와도 한 번만 싣는다")
    void writesSharedPlanDocumentOnce() {
        VscodeSession morning = session();
        morning.setPlanNote("출석 중복 검증 마무리");
        VscodeSession afternoon = session();
        afternoon.setBranch("feature/console");
        afternoon.setPlanNote("출석 중복 검증 마무리");

        String md = DraftTemplate.render(DAY, "배태일", List.of(), List.of(morning, afternoon));

        assertThat(md.split("출석 중복 검증 마무리", -1)).hasSize(2);
    }

    @Test
    @DisplayName("비어 있는 섹션도 자리를 남긴다 — 사용자가 직접 채울 수 있게")
    void keepsEmptySections() {
        String md = DraftTemplate.render(DAY, "배태일", List.of(commit("abc", "요약")), List.of());

        assertThat(md).contains("- (미커밋 작업 없음)").contains("- (기록된 계획 없음)");
    }
}

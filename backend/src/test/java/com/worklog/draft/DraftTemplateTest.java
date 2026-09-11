package com.worklog.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityType;
import com.worklog.github.Repo;
import com.worklog.vscode.TodoItem;
import com.worklog.vscode.UncommittedFile;
import com.worklog.vscode.UnpushedCommit;
import com.worklog.vscode.VscodeSession;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    /**
     * 9/11 부터 저장소로 먼저 나눈다. 저장소를 둘 이상 오간 날에 한 덩어리로 늘어놓으면
     * 무엇이 어느 이야기인지 알 수 없다.
     */
    @Test
    @DisplayName("저장소 머리말 아래에 세 섹션이 순서대로 들어간다")
    void groupsByRepo() {
        String md = DraftTemplate.render(DAY, "배태일", List.of(commit("abc1234", "고쳤다")), List.of());

        assertThat(md)
                .startsWith("# 2026-09-10 업무 일지 — 배태일")
                .contains("## uxis-co-kr/2026-pknu-3day")
                .contains("### 완료한 작업")
                .contains("### 진행 중 / 미커밋")
                .contains("### 계획 / TODO")
                .contains("## 메모");
        assertThat(md.indexOf("## uxis-co-kr/2026-pknu-3day")).isLessThan(md.indexOf("### 완료한 작업"));
        assertThat(md.indexOf("### 완료한 작업")).isLessThan(md.indexOf("### 진행 중 / 미커밋"));
        assertThat(md.indexOf("### 계획 / TODO")).isLessThan(md.indexOf("## 메모"));
    }

    @Test
    @DisplayName("저장소가 둘이면 단락도 둘이고, 메모는 맨 끝에 한 번만 있다")
    void oneSectionPerRepo() {
        Repo other = new Repo();
        other.setFullName("Ae-Ti/CodeAtlas");
        Activity elsewhere = commit("bbb2222", "문서를 고쳤다.");
        elsewhere.setRepo(other);

        String md = DraftTemplate.render(
                DAY, "배태일", List.of(commit("abc1234", "출석 API 를 추가했다."), elsewhere), List.of());

        assertThat(md).contains("## Ae-Ti/CodeAtlas").contains("## uxis-co-kr/2026-pknu-3day");
        // 이름 순이라 날마다 순서가 흔들리지 않는다.
        assertThat(md.indexOf("## Ae-Ti/CodeAtlas")).isLessThan(md.indexOf("## uxis-co-kr/2026-pknu-3day"));
        assertThat(md.split("## 메모", -1)).hasSize(2);
        assertThat(md.indexOf("## 메모")).isGreaterThan(md.indexOf("## uxis-co-kr/2026-pknu-3day"));
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
                .dropWhile(l -> !l.startsWith("### 완료한 작업"))
                .skip(1)
                .takeWhile(l -> l.startsWith("- "))
                .toList();

        assertThat(completed).hasSize(3);
        // 저장소는 머리말이 말한다. 줄마다 다시 적으면 같은 말이 세 줄에 걸쳐 되풀이된다.
        assertThat(completed.get(0)).isEqualTo("- 출석 API 를 추가했다.  (commit abc1234)");
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
                .contains("- PR #12 머지: 출석 기능을 병합했다.")
                .contains("- PR #13 생성: 리뷰를 요청했다.");
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

    @Test
    @DisplayName("미푸시 커밋이 진행 중 섹션에 한 줄로 들어간다 — GitHub 수집기가 못 보는 구간이다")
    void rendersUnpushedCommits() {
        VscodeSession s = session();
        s.setUnpushedCommits(List.of(
                new UnpushedCommit("9f2c1ab", "feat: 출석 중복 검증", OffsetDateTime.parse("2026-09-10T11:30:00+09:00")),
                new UnpushedCommit("3be70d4", "refactor: 의존성 정리", OffsetDateTime.parse("2026-09-10T10:00:00+09:00"))));

        String md = DraftTemplate.render(DAY, "배태일", List.of(), List.of(s));

        assertThat(md).contains("미푸시 커밋 2개 — feat: 출석 중복 검증, refactor: 의존성 정리");
        assertThat(md.indexOf("미푸시 커밋")).isLessThan(md.indexOf("### 계획 / TODO"));
    }

    @Test
    @DisplayName("미푸시 커밋이 없으면 그 줄을 만들지 않는다")
    void skipsUnpushedLineWhenNone() {
        String md = DraftTemplate.render(DAY, "배태일", List.of(), List.of(session()));

        assertThat(md).doesNotContain("미푸시 커밋");
    }

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
                .dropWhile(l -> !l.startsWith("### 계획 / TODO"))
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

        // 저장소 단락 안에서 비는 자리는 "- (없음)" 으로 남는다. 섹션마다 문구를 달리 하면
        // 저장소가 늘어날수록 같은 뜻의 말이 여러 가지로 흩어진다.
        assertThat(md.lines().filter(l -> l.equals("- (없음)")).count()).isEqualTo(2);
    }
}

package com.worklog.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityType;
import com.worklog.github.Repo;
import com.worklog.llm.LlmProvider;
import com.worklog.llm.LlmProviderResolver;
import com.worklog.llm.LlmSettingService;
import com.worklog.llm.PromptLoader;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorklogWriterTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 10);

    @Test
    @DisplayName("LLM 이 쓴 본문을 머리말과 함께 돌려준다")
    void usesLlm() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.complete(any())).thenReturn("## 완료한 작업\n- 수집기를 모든 브랜치로 넓혔다 (c37005f)");

        String md = writer(provider).write(1L, DAY, "배태일", List.of(commit()), List.of());

        assertThat(md).startsWith("# 2026-09-10 업무 일지 — 배태일");
        assertThat(md).contains("수집기를 모든 브랜치로 넓혔다");
    }

    @Test
    @DisplayName("LLM 이 실패하면 템플릿으로 돌아간다 — 일지가 아예 안 만들어지면 안 된다")
    void fallsBackToTemplate() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.complete(any())).thenThrow(new IllegalStateException("연결 실패"));

        String md = writer(provider).write(1L, DAY, "배태일", List.of(commit()), List.of());

        assertThat(md).contains("업무 일지 — 배태일");
        assertThat(md).contains("## 완료한 작업");
        assertThat(md).contains("커밋 요약");
    }

    @Test
    @DisplayName("빈 응답도 실패로 보고 템플릿으로 돌아간다")
    void emptyResponseFallsBack() {
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.complete(any())).thenReturn("   ");

        assertThat(writer(provider).write(1L, DAY, "배태일", List.of(commit()), List.of()))
                .contains("## 진행 중 / 미커밋");
    }

    @Test
    @DisplayName("프롬프트에 그날 활동이 실린다")
    void promptCarriesActivities() {
        LlmProvider provider = mock(LlmProvider.class);
        var request = writer(provider).request(DAY, "배태일", List.of(commit()), List.of());

        assertThat(request.user()).contains("2026-09-10").contains("커밋 요약");
        assertThat(request.system()).contains("## 완료한 작업");
    }

    private static WorklogWriter writer(LlmProvider provider) {
        LlmProviderResolver resolver = mock(LlmProviderResolver.class);
        when(resolver.resolve(any())).thenReturn(provider);
        LlmSettingService settings = mock(LlmSettingService.class);
        when(settings.providerOf(anyLong())).thenReturn(null);
        return new WorklogWriter(resolver, settings, new PromptLoader());
    }

    private static Activity commit() {
        Repo repo = new Repo();
        repo.setFullName("uxis-co-kr/2026-pknu-3day");
        Activity a = new Activity();
        a.setId(1L);
        a.setRepo(repo);
        a.setType(ActivityType.COMMIT);
        a.setSha("c37005fabc");
        a.setExternalId("c37005fabc");
        a.setTitle("fix(github): 모든 브랜치");
        a.setSummary("커밋 요약");
        a.setAdditions(10);
        a.setDeletions(2);
        a.setOccurredAt(OffsetDateTime.now());
        return a;
    }
}

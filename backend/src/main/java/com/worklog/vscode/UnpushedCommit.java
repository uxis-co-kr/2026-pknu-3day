package com.worklog.vscode;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * 커밋했지만 아직 원격에 올리지 않은 커밋 하나 (V11, TODO_0910 §3-2).
 *
 * <p>GitHub 수집기는 원격에 없는 커밋을 볼 수 없다. 개발자 입장에서는 분명히 한 일인데 대시보드에도
 * 초안에도 없던 구간이다. 확장이 {@code git log upstream..HEAD} 로 모아 보낸다.
 *
 * <p>커밋 메시지는 사람이 이미 쓴 요약이라 미커밋 파일 목록보다 초안에 더 어울린다.
 *
 * @param sha 짧은 해시여도 된다 (확장은 %h 를 보낸다)
 * @param subject 커밋 제목 줄
 * @param committedAt ISO-8601. jsonb 안이라 문자열로 둔다 ({@link AiSessionSummary} 와 같은 이유)
 */
public record UnpushedCommit(String sha, String subject, String committedAt) {

    /** 파싱되지 않으면 null — 확장이 빈 문자열을 줄 수 있다. */
    public OffsetDateTime committedAtOrNull() {
        if (committedAt == null || committedAt.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(committedAt);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public String shortSha() {
        return sha == null ? "" : sha.length() <= 7 ? sha : sha.substring(0, 7);
    }
}

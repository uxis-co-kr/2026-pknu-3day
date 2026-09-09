package com.worklog.github;

import com.worklog.github.dto.GitHubCommitDto;
import java.util.List;

/**
 * 커밋 diff 를 LLM 에 넣을 만한 크기로 자른다 (PRD F1-4 — 파일당 200줄, 커밋당 3,000자).
 *
 * <p>순수 함수라 GitHub 호출 없이 단위 테스트할 수 있다.
 */
public final class DiffTruncator {

    public static final int MAX_LINES_PER_FILE = 200;
    public static final int MAX_CHARS_PER_COMMIT = 3_000;

    private DiffTruncator() {}

    public static String truncate(List<GitHubCommitDto.File> files) {
        return truncate(files, MAX_LINES_PER_FILE, MAX_CHARS_PER_COMMIT);
    }

    static String truncate(List<GitHubCommitDto.File> files, int maxLines, int maxChars) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (GitHubCommitDto.File file : files) {
            if (out.length() >= maxChars) {
                break;
            }
            // patch 가 없는 경우: 바이너리 파일, 이름만 바뀐 파일, 너무 큰 diff.
            String header = "--- " + file.filename() + "\n";
            String patch = file.patch() == null ? "(diff 없음)\n" : truncateLines(file.patch(), maxLines);
            out.append(header).append(patch);
            if (!patch.endsWith("\n")) {
                out.append('\n');
            }
        }
        if (out.length() > maxChars) {
            out.setLength(maxChars);
            out.append("\n… (생략)");
        }
        return out.toString();
    }

    private static String truncateLines(String patch, int maxLines) {
        String[] lines = patch.split("\n", -1);
        if (lines.length <= maxLines) {
            return patch;
        }
        return String.join("\n", List.of(lines).subList(0, maxLines)) + "\n… (%d줄 생략)".formatted(lines.length - maxLines);
    }
}

package com.worklog.llm;

import org.springframework.stereotype.Component;

/**
 * 실제 LLM 없이 파이프라인을 돌리기 위한 프로바이더 (PRD F2). 1일차 통합에 쓴다.
 *
 * <p>커밋 메시지를 그대로 되돌려 준다: {@code "{메시지} 작업을 수행했습니다. (변경 파일 N개)"}
 */
@Component
public class MockLlmProvider implements LlmProvider {

    public static final String ID = "mock";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String complete(LlmRequest req) {
        String message = firstLine(req.var("message", "").trim());
        String fileCount = req.var("fileCount", "0");
        if (message.isEmpty()) {
            return "커밋 내용을 확인했습니다. (변경 파일 %s개)".formatted(fileCount);
        }
        return "%s 작업을 수행했습니다. (변경 파일 %s개)".formatted(message, fileCount);
    }

    /** 커밋 메시지 본문이 길면 제목 줄만 쓴다. */
    private static String firstLine(String text) {
        int nl = text.indexOf('\n');
        return nl < 0 ? text : text.substring(0, nl).trim();
    }
}

package com.worklog.vscode;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 질문 하나와 그에 대한 답변 (BACKLOG2 §2-3 "질의별 답변 기록").
 *
 * <p>예전에는 질문만 문자열 목록으로 담았다. 무엇을 시켰는지는 남았지만 <b>무엇을 했는지</b>
 * 가 없었다. 답변은 질문보다 훨씬 길어 확장이 앞부분만 잘라 보낸다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiTurn(
        /** ISO-8601. 물어본 시각 */
        String at,
        String prompt,
        /** 아직 답하는 중이면 null. */
        String answer) {}

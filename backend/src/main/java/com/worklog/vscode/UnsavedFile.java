package com.worklog.vscode;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/**
 * vscode_sessions.unsaved_files JSONB 의 원소 — 고쳐 놓고 아직 저장하지 않은 파일.
 *
 * <p><b>git 에 잡히지 않는 유일한 구간이다.</b> 저장하지 않은 내용은 디스크에 없으니
 * diff 에도, 커밋에도, 원격에도 없다. 확장이 편집기에서 보고해 주지 않으면 어디에서도
 * 알 수 없다.
 *
 * <p>예전에는 여기에 <b>저장 이벤트</b>(파일별 저장 횟수·시각)를 담았다. 그런데 VS Code 의
 * 저장 이벤트는 편집기에서 저장할 때만 오므로, 파일을 디스크에 곧바로 쓰는 AI 도구의
 * 변경은 한 건도 남지 않았다 — 사람이 손으로 저장한 것만 모으는 목록이었다.
 *
 * @param path 저장소 기준 상대 경로
 * @param dirtySince 고치기 시작해 저장하지 않은 채 지난 시각. 확장이 다시 켜진 뒤면 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnsavedFile(String path, OffsetDateTime dirtySince) {}

package com.worklog.notify;

/**
 * 알림 전송 추상화 (PRD F7).
 *
 * <p>전송 실패는 본 흐름을 막지 않는다 — 초안 생성이 알림 때문에 실패해서는 안 된다.
 *
 * @return 실제로 보냈으면 true, 설정이 없거나 실패했으면 false
 */
public interface Notifier {

    boolean send(String webhookUrl, String text);
}

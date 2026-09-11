package com.worklog.vscode;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * git 원격 URL 에서 {@code owner/repo} 를 뽑는다. 확장은 워크스페이스의 origin 을 그대로 보내는데,
 * 그 형태가 https / ssh / .git 유무로 제각각이라 여기서 한 가지로 맞춘다 (PRD F6-2).
 *
 * <p>등록되지 않은 리포일 수도 있으므로 실패는 예외가 아니라 빈 값이다. 세션은 repo 없이도 저장된다.
 */
public final class RemoteUrlParser {

    private static final Pattern PATTERN =
            Pattern.compile("(?:^|[/:])([\\w.-]+)/([\\w.-]+?)(?:\\.git)?/?$");

    private RemoteUrlParser() {}

    public static Optional<String> toFullName(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return Optional.empty();
        }
        Matcher m = PATTERN.matcher(remoteUrl.trim());
        if (!m.find()) {
            return Optional.empty();
        }
        return Optional.of(m.group(1) + "/" + m.group(2));
    }
}

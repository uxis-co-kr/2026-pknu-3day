package com.worklog.llm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * {@code classpath:llm/prompts/} 의 프롬프트 텍스트를 읽어 {@code {key}} 자리를 치환한다 (PRD F2).
 *
 * <p>프롬프트를 코드에 박지 않기 위한 장치다. 파일 내용은 기동 후 바뀌지 않으므로 한 번 읽고 캐시한다.
 */
@Component
public class PromptLoader {

    public static final String COMMIT_SUMMARY_SYSTEM = "commit-summary-system";
    public static final String COMMIT_SUMMARY_USER = "commit-summary-user";
    /** AI 대화 한 세션을 요약하는 프롬프트. */
    public static final String AI_SESSION_SUMMARY_SYSTEM = "ai-session-summary-system";
    public static final String AI_SESSION_SUMMARY_USER = "ai-session-summary-user";
    /** 하루치 활동을 묶어 업무 일지를 쓰는 프롬프트 (F-1). */
    public static final String WORKLOG_SYSTEM = "worklog-system";
    public static final String WORKLOG_USER = "worklog-user";

    private static final String BASE_PATH = "llm/prompts/";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)}");

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /** 치환 없이 원본 그대로. */
    public String load(String name) {
        return cache.computeIfAbsent(name, PromptLoader::read);
    }

    /**
     * {@code {repo}}, {@code {message}}, {@code {files}}, {@code {diff}} 등을 채운다.
     * vars 에 없는 자리표시자는 빈 문자열로 지운다 — 프롬프트에 {@code {diff}} 가 그대로 남는 것보다 낫다.
     */
    public String render(String name, Map<String, String> vars) {
        String template = load(name);
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = vars.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String read(String name) {
        ClassPathResource resource = new ClassPathResource(BASE_PATH + name + ".txt");
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 파일을 읽지 못했다: " + resource.getPath(), e);
        }
    }
}

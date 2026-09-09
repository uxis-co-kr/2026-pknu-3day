package com.worklog.llm;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * OpenAI 호환 엔드포인트를 부르는 유일한 실제 프로바이더 (PRD F2).
 *
 * <p>모델을 늘리는 일은 코드가 아니라 {@code worklog.llm.presets} 항목 추가로 끝나야 하므로,
 * 이 클래스는 프리셋 하나당 인스턴스 하나로 만들어진다. {@link #id()} 가 프리셋 이름이다.
 */
public class OpenAiCompatProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatProvider.class);

    /** qwen 계열은 사고 과정을 응답에 섞어 보낸다. 저장 전에 지운다 (PRD F2). */
    private static final Pattern THINK_BLOCK =
            Pattern.compile("<think>.*?</think>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final String id;
    private final String model;
    private final RestClient restClient;

    public OpenAiCompatProvider(String id, LlmProperties.Preset preset) {
        this.id = id;
        this.model = preset.getModel();

        // 27B 급 모델은 3,000자 diff 하나에 50초 넘게 걸리는 일이 있어 프리셋에서 늘릴 수 있게 뒀다.
        Duration timeout = Duration.ofSeconds(Math.max(preset.getTimeoutSeconds(), 1));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) timeout.toMillis());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(stripTrailingSlash(preset.getBaseUrl()))
                .requestFactory(factory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                // ollama 기반 엔드포인트(qwen3)는 JSON 을 application/octet-stream 으로 내려보낸다.
                // 선언된 타입을 그대로 믿으면 디코딩에 실패하므로 타입과 무관하게 JSON 으로 읽는다.
                .messageConverters(converters -> converters.add(0, lenientJsonConverter()));
        // 사내 엔드포인트는 키를 요구하지 않을 수 있다.
        if (preset.getApiKey() != null && !preset.getApiKey().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + preset.getApiKey());
        }
        this.restClient = builder.build();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String complete(LlmRequest req) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", req.system()),
                        Map.of("role", "user", "content", req.user())),
                "temperature", req.temperature(),
                "max_tokens", req.maxTokens(),
                "stream", false);

        Map<?, ?> response = restClient
                .post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        return stripThinking(extractContent(response));
    }

    /** 기동 시 연결·모델을 확인한다. 실패해도 예외를 던지지 않고 false 만 돌려준다. */
    public boolean verify() {
        try {
            Map<?, ?> models = restClient.get().uri("/models").retrieve().body(Map.class);
            List<String> ids = modelIds(models);
            if (!ids.contains(model)) {
                log.warn("프리셋 {} 의 모델 {} 이 서버 목록에 없다. 사용 가능한 모델: {}", id, model, ids);
                return false;
            }
            log.info("프리셋 {} 검증 성공 — 모델 {}", id, model);
            return true;
        } catch (Exception e) {
            log.warn("프리셋 {} 검증 실패: {}", id, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> modelIds(Map<?, ?> response) {
        if (response == null || !(response.get("data") instanceof List<?> data)) {
            return List.of();
        }
        return data.stream()
                .filter(Map.class::isInstance)
                .map(m -> String.valueOf(((Map<String, Object>) m).get("id")))
                .toList();
    }

    @SuppressWarnings("unchecked")
    static String extractContent(Map<?, ?> response) {
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("LLM 응답에 choices 가 없다.");
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice)
                || !(choice.get("message") instanceof Map<?, ?> message)) {
            throw new IllegalStateException("LLM 응답 형식이 예상과 다르다.");
        }
        Object content = ((Map<String, Object>) message).get("content");
        if (content == null) {
            throw new IllegalStateException("LLM 응답에 content 가 없다.");
        }
        return String.valueOf(content);
    }

    /** {@code <think>} 블록과 닫히지 않은 꼬리를 지운다. */
    static String stripThinking(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = THINK_BLOCK.matcher(text).replaceAll("");
        int dangling = cleaned.toLowerCase().indexOf("<think>");
        if (dangling >= 0) {
            cleaned = cleaned.substring(0, dangling);
        }
        return cleaned.strip();
    }

    private static MappingJackson2HttpMessageConverter lenientJsonConverter() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setSupportedMediaTypes(
                List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL));
        return converter;
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            throw new IllegalArgumentException("프리셋에 base-url 이 없다.");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

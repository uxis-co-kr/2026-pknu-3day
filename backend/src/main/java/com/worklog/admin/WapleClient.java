package com.worklog.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 사내 회원 조회 API 호출 (TODO_0910 §1-1).
 *
 * <p>응답은 {@code { status, message, data: { <이름>: [...] } }} 모양이다.
 * 사내망이 닿지 않거나 설정이 없으면 예외를 던지지 않고 빈 목록을 돌려준다 —
 * 관리자 콘솔의 다른 영역까지 같이 죽으면 안 된다.
 */
@Component
public class WapleClient {

    private static final Logger log = LoggerFactory.getLogger(WapleClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WapleProperties properties;
    private final RestClient restClient;

    public WapleClient(WapleProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TIMEOUT.toMillis());
        factory.setReadTimeout((int) TIMEOUT.toMillis());

        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
            builder.baseUrl(stripTrailingSlash(properties.getBaseUrl()));
        }
        // 사내 API 가 콘텐츠 타입을 정확히 주지 않는 경우가 있어 타입과 무관하게 JSON 으로 읽는다.
        MappingJackson2HttpMessageConverter json = new MappingJackson2HttpMessageConverter();
        json.setSupportedMediaTypes(
                List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL));
        builder.messageConverters(converters -> converters.add(0, json));
        builder.defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE);

        this.restClient = builder.build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /** 사용중인 회사 목록. */
    public List<Company> companies() {
        return get("/core/v1/companies", "companies", Company.class);
    }

    /**
     * 재직중 사원 목록.
     *
     * <p>{@code worklog.waple.fallback-employees} 에 적은 항목은 <b>실제 목록 뒤에 덧붙는다</b> —
     * 사내 API 가 없을 때는 그것만, 있을 때는 실제 사원 다음에 온다. 아직 와플에 등록되지 않은
     * 사람으로 흐름을 시험하기 위한 <b>임시 수단</b>이다. 시험이 끝나면 설정을 비운다.
     */
    public List<Employee> employees(long coSeq) {
        List<Employee> extra = fallbackEmployees();
        if (!isConfigured()) {
            return extra;
        }
        List<Employee> real =
                get("/core/v1/companies/%d/employees".formatted(coSeq), "employees", Employee.class);
        if (extra.isEmpty()) {
            return real;
        }
        // 실제 사원과 번호가 겹치면 실제 쪽을 남긴다.
        java.util.Set<Long> taken =
                real.stream().map(Employee::empSeq).collect(java.util.stream.Collectors.toSet());
        List<Employee> merged = new java.util.ArrayList<>(real);
        extra.stream().filter(e -> !taken.contains(e.empSeq())).forEach(merged::add);
        log.info("사내 사원 {}명에 임시 항목 {}건을 덧붙인다.", real.size(), merged.size() - real.size());
        return merged;
    }

    /** {@code 9999:조웅식,9998:배태일} 을 사원 목록으로 읽는다. */
    List<Employee> fallbackEmployees() {
        String raw = properties.getFallbackEmployees();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Employee> parsed = new java.util.ArrayList<>();
        for (String entry : raw.split(",")) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                parsed.add(new Employee(Long.parseLong(parts[0].trim()), parts[1].trim()));
            } catch (NumberFormatException e) {
                log.warn("사원 목록 항목을 읽지 못했다: {}", entry);
            }
        }
        return parsed;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> get(String path, String dataKey, Class<T> type) {
        if (!isConfigured()) {
            log.debug("와플 API 설정이 없어 빈 목록을 돌려준다. ({})", path);
            return List.of();
        }
        try {
            Map<?, ?> body = restClient
                    .get()
                    .uri(path)
                    .header("X-Api-Key", properties.getApiKey())
                    .retrieve()
                    .body(Map.class);

            if (body == null || !(body.get("data") instanceof Map<?, ?> data)) {
                log.warn("와플 응답에 data 가 없다: {}", path);
                return List.of();
            }
            if (!(data.get(dataKey) instanceof List<?> items)) {
                log.warn("와플 응답에 data.{} 가 없다: {}", dataKey, path);
                return List.of();
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return items.stream().map(i -> mapper.convertValue(i, type)).toList();
        } catch (Exception e) {
            // 사내망 밖이거나 키가 틀린 경우. 콘솔의 나머지 영역은 계속 보여야 한다.
            log.warn("와플 API 호출 실패 ({}): {}", path, e.getMessage());
            return List.of();
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Company(Long coSeq, String coNm) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Employee(Long empSeq, String empNm) {}
}

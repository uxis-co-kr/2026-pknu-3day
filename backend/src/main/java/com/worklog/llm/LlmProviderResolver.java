package com.worklog.llm;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@code worklog.llm.provider} 값으로 프로바이더 빈을 고른다 (PRD F2).
 *
 * <p>모르는 값이면 서비스를 죽이지 않고 경고 후 mock 으로 폴백한다.
 */
@Component
public class LlmProviderResolver {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderResolver.class);

    private final Map<String, LlmProvider> byId;
    private final LlmProvider fallback;
    private final String configuredId;

    public LlmProviderResolver(List<LlmProvider> providers, LlmProperties properties) {
        this.byId = providers.stream()
                .collect(Collectors.toMap(LlmProvider::id, Function.identity(), (a, b) -> a));
        this.fallback = byId.get(MockLlmProvider.ID);
        this.configuredId = properties.getProvider();
        if (fallback == null) {
            throw new IllegalStateException("MockLlmProvider 빈이 없다. 폴백이 불가능하다.");
        }
        if (!byId.containsKey(configuredId)) {
            log.warn(
                    "worklog.llm.provider={} 에 해당하는 프로바이더가 없다. mock 으로 폴백한다. 등록된 프로바이더: {}",
                    configuredId,
                    byId.keySet());
        } else {
            log.info("LLM 프로바이더: {}", configuredId);
        }
    }

    /** 설정된 프로바이더. 없으면 mock. */
    public LlmProvider resolve() {
        return byId.getOrDefault(configuredId, fallback);
    }

    /** 사용자별 오버라이드(P2, F9)용. 이름이 없으면 설정값으로 되돌아간다. */
    public LlmProvider resolve(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return resolve();
        }
        LlmProvider provider = byId.get(providerId);
        if (provider == null) {
            log.warn("알 수 없는 프로바이더 {} — 설정값 {} 로 폴백한다.", providerId, configuredId);
            return resolve();
        }
        return provider;
    }
}

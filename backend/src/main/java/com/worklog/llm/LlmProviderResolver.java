package com.worklog.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * {@code worklog.llm.provider} 값으로 프로바이더를 고른다 (PRD F2).
 *
 * <p>프리셋은 설정에서 개수가 정해지므로 {@code @Bean} 메서드로 미리 선언할 수 없다. 여기서
 * 프리셋 수만큼 {@link OpenAiCompatProvider} 를 만들어 등록한다 — 새 모델을 붙이는 일이
 * 코드 수정 없이 프리셋 추가로 끝나야 한다는 요구(PRD F2)를 지키기 위해서다.
 *
 * <p>선택된 프로바이더는 기동 시 {@code /models} 로 한 번 검증하고, 실패하면 경고 후 mock 으로
 * 폴백한다. 사내 LLM 이 죽어 있어도 서비스는 떠야 한다.
 */
@Component
public class LlmProviderResolver {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderResolver.class);

    private final Map<String, LlmProvider> byId = new LinkedHashMap<>();
    private final LlmProvider fallback;
    private final String configuredId;
    private final boolean configuredUsable;

    @Autowired
    public LlmProviderResolver(List<LlmProvider> beans, LlmProperties properties) {
        this(beans, properties, OpenAiCompatProvider::new);
    }

    /** 프로바이더 생성을 주입받는 생성자 — 테스트에서 실제 HTTP 를 타지 않게 한다. */
    LlmProviderResolver(List<LlmProvider> beans, LlmProperties properties, PresetFactory factory) {
        beans.forEach(provider -> byId.putIfAbsent(provider.id(), provider));

        properties.getPresets().forEach((name, preset) -> {
            try {
                byId.put(name, factory.create(name, preset));
            } catch (Exception e) {
                log.warn("프리셋 {} 을 만들지 못했다: {}", name, e.getMessage());
            }
        });

        this.fallback = byId.get(MockLlmProvider.ID);
        if (fallback == null) {
            throw new IllegalStateException("MockLlmProvider 빈이 없다. 폴백이 불가능하다.");
        }

        this.configuredId = properties.getProvider();
        LlmProvider configured = byId.get(configuredId);
        if (configured == null) {
            log.warn(
                    "worklog.llm.provider={} 에 해당하는 프로바이더가 없다. mock 으로 폴백한다. 등록된 프로바이더: {}",
                    configuredId,
                    byId.keySet());
            this.configuredUsable = false;
            return;
        }
        this.configuredUsable = verify(configured);
        if (configuredUsable) {
            log.info("LLM 프로바이더: {}", configuredId);
        } else {
            log.warn("프로바이더 {} 를 쓸 수 없어 mock 으로 폴백한다.", configuredId);
        }
    }

    private static boolean verify(LlmProvider provider) {
        return !(provider instanceof OpenAiCompatProvider openAi) || openAi.verify();
    }

    /** 설정된 프로바이더. 없거나 검증에 실패했으면 mock. */
    public LlmProvider resolve() {
        return configuredUsable ? byId.getOrDefault(configuredId, fallback) : fallback;
    }

    /** 사용자별 오버라이드(P2, F9)용. 이름이 없거나 모르면 설정값으로 되돌아간다. */
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

    /**
     * 고를 수 있는 프로바이더 이름 — 화면의 라디오 목록 (PRD F9).
     *
     * <p>설정 파일에 프리셋을 더하면 여기에도 자동으로 늘어난다.
     */
    public java.util.List<String> availableIds() {
        return java.util.List.copyOf(byId.keySet());
    }

    /** 그 이름의 프로바이더가 등록돼 있는지. */
    public boolean supports(String providerId) {
        return providerId != null && byId.containsKey(providerId);
    }

    /** 설정에서 고른 프로바이더 이름. 검증에 실패해 mock 으로 폴백했더라도 설정값 그대로다. */
    public String configuredId() {
        return configuredId;
    }

    /** 프리셋 이름과 설정으로 프로바이더를 만든다. */
    @FunctionalInterface
    interface PresetFactory {
        LlmProvider create(String id, LlmProperties.Preset preset);
    }
}

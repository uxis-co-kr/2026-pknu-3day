package com.worklog.llm;

import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.config.ApiException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자별 LLM 프로바이더 설정 (PRD F9b — P2).
 *
 * <p>커스텀 엔드포인트는 받지 않는다. 등록된 프리셋 중에서만 고른다.
 */
@Service
public class LlmSettingService {

    private final UserLlmSettingRepository settingRepository;
    private final UserRepository userRepository;
    private final LlmProviderResolver resolver;
    private final LlmProperties properties;

    public LlmSettingService(
            UserLlmSettingRepository settingRepository,
            UserRepository userRepository,
            LlmProviderResolver resolver,
            LlmProperties properties) {
        this.settingRepository = settingRepository;
        this.userRepository = userRepository;
        this.resolver = resolver;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public LlmSettingView get(Long userId) {
        Optional<UserLlmSetting> setting = settingRepository.findByUserId(userId);
        String provider = setting.map(UserLlmSetting::getProvider).orElse(resolver.configuredId());
        return new LlmSettingView(
                provider, modelOf(provider), setting.isPresent(), resolver.availableIds());
    }

    /** 프로바이더 이름만 저장한다. model 은 프리셋에서 끌어오므로 사용자가 정하지 않는다. */
    @Transactional
    public LlmSettingView update(Long userId, String provider) {
        if (!resolver.supports(provider)) {
            throw ApiException.badRequest(
                    "INVALID_PARAMETER",
                    "provider 값이 올바르지 않습니다: %s. 가능한 값: %s"
                            .formatted(provider, String.join(", ", resolver.availableIds())));
        }
        UserLlmSetting setting = settingRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository
                    .findById(userId)
                    .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
            UserLlmSetting created = new UserLlmSetting();
            created.setUser(user);
            return created;
        });
        setting.setProvider(provider);
        setting.setModel(modelOf(provider));
        settingRepository.save(setting);

        return new LlmSettingView(provider, modelOf(provider), true, resolver.availableIds());
    }

    /** 요약 파이프라인이 활동 소유자의 설정을 물어볼 때 쓴다. 없으면 null → 전역 설정. */
    @Transactional(readOnly = true)
    public String providerOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return settingRepository.findByUserId(userId).map(UserLlmSetting::getProvider).orElse(null);
    }

    private String modelOf(String provider) {
        LlmProperties.Preset preset = properties.getPresets().get(provider);
        // mock 은 프리셋이 없다.
        return preset == null ? null : preset.getModel();
    }

    /**
     * @param overridden 사용자가 직접 고른 값인지. false 면 전역 설정을 보여 주는 것이다.
     */
    public record LlmSettingView(
            String provider, String model, boolean overridden, List<String> available) {}
}

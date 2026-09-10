package com.worklog.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.config.ApiException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LlmSettingServiceTest {

    private static final Long USER_ID = 1L;

    private UserLlmSettingRepository settingRepository;
    private LlmProviderResolver resolver;
    private LlmSettingService service;

    @BeforeEach
    void setUp() {
        settingRepository = mock(UserLlmSettingRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        resolver = mock(LlmProviderResolver.class);

        when(resolver.availableIds()).thenReturn(List.of("mock", "gemma4", "qwen3"));
        when(resolver.configuredId()).thenReturn("gemma4");
        when(resolver.supports("qwen3")).thenReturn(true);
        when(resolver.supports("gemma4")).thenReturn(true);
        when(resolver.supports("mock")).thenReturn(true);

        User user = new User();
        user.setId(USER_ID);
        user.setLogin("UngsikJo");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(settingRepository.save(any(UserLlmSetting.class))).thenAnswer(i -> i.getArgument(0));

        LlmProperties properties = new LlmProperties();
        LlmProperties.Preset qwen = new LlmProperties.Preset();
        qwen.setModel("qwen3.8:27b");
        properties.getPresets().put("qwen3", qwen);
        LlmProperties.Preset gemma = new LlmProperties.Preset();
        gemma.setModel("google/gemma-4-26b-a4b-qat");
        properties.getPresets().put("gemma4", gemma);

        service = new LlmSettingService(settingRepository, userRepository, resolver, properties);
    }

    private static UserLlmSetting setting(String provider) {
        UserLlmSetting s = new UserLlmSetting();
        s.setProvider(provider);
        return s;
    }

    @Test
    @DisplayName("설정이 없으면 전역 설정을 보여 주고 overridden 은 false")
    void showsGlobalWhenNotOverridden() {
        when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        var view = service.get(USER_ID);

        assertThat(view.provider()).isEqualTo("gemma4");
        assertThat(view.model()).isEqualTo("google/gemma-4-26b-a4b-qat");
        assertThat(view.overridden()).isFalse();
        assertThat(view.available()).containsExactly("mock", "gemma4", "qwen3");
    }

    @Test
    @DisplayName("사용자가 고른 값이 있으면 그것을 보여 주고 overridden 은 true")
    void showsUserChoice() {
        when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting("qwen3")));

        var view = service.get(USER_ID);

        assertThat(view.provider()).isEqualTo("qwen3");
        assertThat(view.model()).isEqualTo("qwen3.8:27b");
        assertThat(view.overridden()).isTrue();
    }

    @Test
    @DisplayName("저장하면 프리셋의 모델명도 함께 채운다 — 사용자가 모델을 정하지 않는다 (PRD F9)")
    void savesProviderWithPresetModel() {
        when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        var view = service.update(USER_ID, "qwen3");

        assertThat(view.provider()).isEqualTo("qwen3");
        assertThat(view.model()).isEqualTo("qwen3.8:27b");
        assertThat(view.overridden()).isTrue();
    }

    @Test
    @DisplayName("이미 설정이 있으면 새로 만들지 않고 덮어쓴다")
    void updatesExisting() {
        UserLlmSetting existing = setting("gemma4");
        when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        service.update(USER_ID, "qwen3");

        assertThat(existing.getProvider()).isEqualTo("qwen3");
    }

    @Test
    @DisplayName("등록되지 않은 프로바이더는 가능한 값을 알려주며 400 — 저장하지 않는다")
    void rejectsUnknownProvider() {
        assertThatThrownBy(() -> service.update(USER_ID, "gpt-9"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("가능한 값: mock, gemma4, qwen3");
        verify(settingRepository, never()).save(any());
    }

    @Test
    @DisplayName("mock 은 프리셋이 없어 모델명이 null 이다")
    void mockHasNoModel() {
        when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.update(USER_ID, "mock").model()).isNull();
    }

    @Test
    @DisplayName("요약 파이프라인이 묻는 providerOf — 설정이 없으면 null, 사용자가 없으면 null")
    void providerOf() {
        when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting("qwen3")));

        assertThat(service.providerOf(USER_ID)).isEqualTo("qwen3");
        assertThat(service.providerOf(null)).isNull();

        when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        assertThat(service.providerOf(USER_ID)).isNull();
    }
}

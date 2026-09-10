package com.worklog.llm;

import com.worklog.auth.AuthenticatedUser;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LLM 프로바이더 설정 (PRD 7. GET/PUT /settings/llm — P2, F9b).
 */
@RestController
@RequestMapping("/settings/llm")
public class LlmSettingController {

    private final LlmSettingService settingService;

    public LlmSettingController(LlmSettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping
    public LlmSettingService.LlmSettingView get(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return settingService.get(principal.id());
    }

    @PutMapping
    public LlmSettingService.LlmSettingView update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody @jakarta.validation.Valid LlmSettingRequest request) {
        return settingService.update(principal.id(), request.provider());
    }

    public record LlmSettingRequest(@NotBlank String provider) {}
}

package com.worklog.auth;

import com.worklog.config.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 현재 사용자 (PRD 7. GET /me ★ — JWT / API Key 둘 다 허용).
 */
@RestController
public class MeController {

    private final UserRepository userRepository;

    public MeController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        User user = userRepository
                .findById(principal.id())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        return new MeResponse(user.getId(), user.getLogin(), user.getName(), user.getAvatarUrl());
    }

    /** 담당자 1의 목업 JSON 과 필드명이 같아야 한다 (PRD 7. 대표 응답 스키마). */
    public record MeResponse(Long id, String login, String name, String avatarUrl) {}
}

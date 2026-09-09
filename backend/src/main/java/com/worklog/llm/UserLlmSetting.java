package com.worklog.llm;

import com.worklog.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 사용자별 LLM 프로바이더 오버라이드 (PRD F9, P2). 요약 생성 시 사용자 설정 → 전역 설정 순으로 적용한다.
 */
@Entity
@Table(name = "user_llm_settings")
@Getter
@Setter
@NoArgsConstructor
public class UserLlmSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** 프리셋 이름 — "mock" | "gemma4" | "qwen3". */
    @Column(nullable = false, length = 50)
    private String provider;

    @Column(length = 200)
    private String model;

    @Column(columnDefinition = "text")
    private String endpoint;

    @Column(name = "api_key_enc", columnDefinition = "text")
    private String apiKeyEnc;
}

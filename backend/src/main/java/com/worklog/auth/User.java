package com.worklog.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * GitHub OAuth 로 로그인한 사용자. github_id 를 기준으로 UPSERT 한다 (PRD F5).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_id", nullable = false, unique = true)
    private Long githubId;

    @Column(nullable = false, length = 100)
    private String login;

    @Column(length = 200)
    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    /** GitHub access token. 평문 저장 금지 — AES 암호문만 넣는다 (PRD 11). */
    @Column(name = "github_token_enc")
    private String githubTokenEnc;

    /** 관리자 콘솔 접근 제어 (TODO_0910 §1-3). 기본은 일반 회원이다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.MEMBER;

    /**
     * 사내 회원(와플)과 잇는 열쇠 (TODO_0910 §1-1).
     *
     * <p>회사가 둘인데 이름이 같고 동명이인 사원도 있어, 사원을 특정하려면 둘 다 있어야 한다.
     * 아직 잇지 않은 계정은 null 이다.
     */
    @Column(name = "co_seq")
    private Long coSeq;

    @Column(name = "emp_seq")
    private Long empSeq;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}

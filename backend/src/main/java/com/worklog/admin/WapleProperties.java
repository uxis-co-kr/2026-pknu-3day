package com.worklog.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 사내 회원 조회 API(와플) 설정 (TODO_0910 §1-1).
 *
 * <p>사내망 주소이고 헤더에 키가 들어가므로 <b>프론트에서 직접 부를 수 없다.</b>
 * 백엔드가 대신 부르고 우리 API 로 중계한다. 값은 {@code backend/.env} 에만 둔다.
 */
@ConfigurationProperties(prefix = "worklog.waple")
public class WapleProperties {

    private String baseUrl;
    private String apiKey;

    /** 사원 목록을 읽어 올 회사. 회사가 둘인데 이름이 같아 번호로 정한다 (TODO_0910 §5-1). */
    private Long companySeq;

    /**
     * 사내 API 가 없을 때 대신 쓸 사원 목록 — <b>임시 수단</b>이다.
     *
     * <p>{@code 9999:조웅식,9998:배태일} 형식. 사내망 밖에서 회원 흐름을 시험하려고 둔다.
     * {@link #isConfigured()} 가 true 면(진짜 API 가 있으면) 이 값은 쓰이지 않는다.
     * 와플 연동이 끝나면 설정과 함께 지운다.
     */
    private String fallbackEmployees = "";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Long getCompanySeq() {
        return companySeq;
    }

    public void setCompanySeq(Long companySeq) {
        this.companySeq = companySeq;
    }

    public String getFallbackEmployees() {
        return fallbackEmployees;
    }

    public void setFallbackEmployees(String fallbackEmployees) {
        this.fallbackEmployees = fallbackEmployees;
    }

    /** 주소와 키가 모두 있어야 부를 수 있다. 없으면 화면은 "설정되지 않음"으로 뜬다. */
    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }
}

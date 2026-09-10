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

    /** 주소와 키가 모두 있어야 부를 수 있다. 없으면 화면은 "설정되지 않음"으로 뜬다. */
    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }
}

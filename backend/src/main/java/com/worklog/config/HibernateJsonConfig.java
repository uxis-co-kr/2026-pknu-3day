package com.worklog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * JSONB 컬럼(vscode_sessions.*) 매핑에 Spring 이 구성한 ObjectMapper 를 쓰게 한다.
 *
 * <p>기본값은 Hibernate 가 자체 생성한 ObjectMapper 라 JavaTimeModule 이 없어
 * {@link com.worklog.vscode.EditTimelineEntry} 의 OffsetDateTime 이 ISO 문자열로 직렬화되지 않는다.
 */
@Configuration
public class HibernateJsonConfig implements HibernatePropertiesCustomizer {

    private final ObjectMapper objectMapper;

    public HibernateJsonConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(
                AvailableSettings.JSON_FORMAT_MAPPER, new JacksonJsonFormatMapper(objectMapper));
    }
}

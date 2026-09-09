package com.worklog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 수집·요약을 API 응답과 분리해 돌리기 위한 설정 (PRD 11).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {}

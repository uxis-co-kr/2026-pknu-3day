package com.worklog.llm;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLlmSettingRepository extends JpaRepository<UserLlmSetting, Long> {

    Optional<UserLlmSetting> findByUserId(Long userId);
}

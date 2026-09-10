package com.worklog.chat;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatBotSettingRepository extends JpaRepository<ChatBotSetting, Long> {

    Optional<ChatBotSetting> findFirstByOrderByIdAsc();
}

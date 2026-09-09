package com.worklog.notify;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NotifySettingRepository extends JpaRepository<NotifySetting, Long> {

    Optional<NotifySetting> findByUserId(Long userId);

    /** user_id 가 null 인 행은 DB 에서 1건으로 제한돼 있다 (V1 스키마). */
    @Query("select n from NotifySetting n where n.user is null")
    Optional<NotifySetting> findGlobal();
}

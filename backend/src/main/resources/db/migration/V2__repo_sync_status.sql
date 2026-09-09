-- 리포 동기화 결과를 남긴다.
-- 리포 관리 화면의 상태 배지(정상/진행 중/실패)가 FAILED 를 표시하려면
-- 마지막 결과가 어딘가 저장돼 있어야 한다 (PRD 7. GET /repos syncStatus).
-- SYNCING 은 수집기의 진행 중 집합에서 판단하므로 저장하지 않는다.

ALTER TABLE repos ADD COLUMN last_sync_status VARCHAR(20) NOT NULL DEFAULT 'OK';
ALTER TABLE repos ADD COLUMN last_sync_error  TEXT;

ALTER TABLE repos ADD CONSTRAINT ck_repos_sync_status
    CHECK (last_sync_status IN ('OK', 'FAILED'));

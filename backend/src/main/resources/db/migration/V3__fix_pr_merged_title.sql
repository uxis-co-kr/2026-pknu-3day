-- PR_MERGED 활동의 제목에서 "PR #N 머지: " 접두사를 떼어낸다.
--
-- 수집기가 표기까지 붙여 저장하는 바람에 화면과 초안 템플릿이 한 번 더 붙여
-- "PR #1 머지: PR #1 머지: ..." 로 겹쳐 나왔다. 제목은 GitHub 이 준 값만 담고
-- 표기는 읽는 쪽이 만든다 (PRD 7. type·externalId 로 구분 가능).
--
-- 접두사가 여러 번 겹쳐 있을 수 있어 정규식으로 반복 제거한다.
UPDATE activities
SET title = regexp_replace(title, '^(PR #[0-9]+ 머지: )+', '')
WHERE type = 'PR_MERGED'
  AND title ~ '^PR #[0-9]+ 머지: ';

-- 요약은 오염된 제목을 입력으로 만들어졌으므로 다시 생성한다.
-- 요약 파이프라인이 PENDING 을 집어 다음 주기에 채운다 (PRD F2).
UPDATE activities
SET summary = NULL,
    summary_status = 'PENDING',
    summary_retries = 0
WHERE type = 'PR_MERGED';

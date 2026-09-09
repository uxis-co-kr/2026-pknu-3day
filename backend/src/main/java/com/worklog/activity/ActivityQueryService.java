package com.worklog.activity;

import com.worklog.config.ApiException;
import com.worklog.config.KstDates;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활동 조회 (PRD 7. GET /activities).
 *
 * <p>필터가 서로 독립적이라 Specification 으로 조합한다. 날짜는 KST 하루 구간으로 바꿔
 * {@code occurred_at} 과 비교한다 (결정 ⑬).
 */
@Service
public class ActivityQueryService {

    private static final int MAX_SIZE = 200;

    private final ActivityRepository activityRepository;

    public ActivityQueryService(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @Transactional(readOnly = true)
    public Page<Activity> search(ActivityQuery query) {
        Specification<Activity> spec = Specification.where(fetchRelations());

        LocalDate from = query.from();
        LocalDate to = query.to();
        if (query.date() != null) {
            from = query.date();
            to = query.date();
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "from 이 to 보다 뒤입니다.");
        }
        if (from != null) {
            OffsetDateTime start = KstDates.startOf(from);
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), start));
        }
        if (to != null) {
            OffsetDateTime end = KstDates.endOf(to);
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("occurredAt"), end));
        }
        if (query.userId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("user").get("id"), query.userId()));
        }
        if (query.repoId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("repo").get("id"), query.repoId()));
        }
        if (query.type() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), query.type()));
        }

        PageRequest pageRequest = PageRequest.of(
                Math.max(query.page(), 0),
                Math.clamp(query.size(), 1, MAX_SIZE),
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        return activityRepository.findAll(spec, pageRequest);
    }

    @Transactional(readOnly = true)
    public Activity get(Long id) {
        return activityRepository
                .findDetailById(id)
                .orElseThrow(() -> ApiException.notFound("ACTIVITY_NOT_FOUND", "활동을 찾을 수 없습니다."));
    }

    /**
     * repo·user 를 함께 읽는다. 응답 매핑은 트랜잭션 밖에서 일어나므로 지연 로딩이면 터진다.
     * count 쿼리에는 fetch 를 걸 수 없어 결과 타입이 Activity 일 때만 적용한다.
     */
    private static Specification<Activity> fetchRelations() {
        return (root, query, cb) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("repo", jakarta.persistence.criteria.JoinType.LEFT);
                root.fetch("user", jakarta.persistence.criteria.JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }

    /** 조회 조건. 컨트롤러가 쿼리 파라미터를 그대로 담아 넘긴다. */
    public record ActivityQuery(
            LocalDate date,
            LocalDate from,
            LocalDate to,
            Long userId,
            Long repoId,
            ActivityType type,
            int page,
            int size) {}
}

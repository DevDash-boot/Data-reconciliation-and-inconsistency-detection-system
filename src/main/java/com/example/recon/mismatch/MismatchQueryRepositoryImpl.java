package com.example.recon.mismatch;

import com.example.recon.common.PageResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;

/**
 * 조건이 선택적인 검색을 Criteria 로 만든다.
 * keyword 는 "앞부분 일치"(LIKE 'abc%') 라서 match_key 인덱스를 쓸 수 있다.
 * 정렬은 id 내림차순(최신순). 목록 쿼리는 job 을 함께 읽어(fetch) 화면에서 N+1 이 생기지 않게 한다.
 */
public class MismatchQueryRepositoryImpl implements MismatchQueryRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public PageResult<Mismatch> search(MismatchSearchCriteria c, int page, int size) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Mismatch> cq = cb.createQuery(Mismatch.class);
        Root<Mismatch> root = cq.from(Mismatch.class);
        root.fetch("job");
        cq.select(root).where(predicates(cb, root, c)).orderBy(cb.desc(root.get("id")));
        List<Mismatch> content = em.createQuery(cq)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Mismatch> countRoot = countQuery.from(Mismatch.class);
        countQuery.select(cb.count(countRoot)).where(predicates(cb, countRoot, c));
        long total = em.createQuery(countQuery).getSingleResult();

        return PageResult.of(content, page, size, total);
    }

    private Predicate[] predicates(CriteriaBuilder cb, Root<Mismatch> root, MismatchSearchCriteria c) {
        List<Predicate> list = new ArrayList<>();
        if (c.jobId() != null) {
            list.add(cb.equal(root.get("job").get("id"), c.jobId()));
        }
        if (c.type() != null) {
            list.add(cb.equal(root.get("type"), c.type()));
        }
        if (c.status() != null) {
            list.add(cb.equal(root.get("status"), c.status()));
        }
        if (c.keyword() != null && !c.keyword().isBlank()) {
            list.add(cb.like(root.<String>get("matchKey"), escapeLike(c.keyword().trim()) + "%", '\\'));
        }
        return list.toArray(new Predicate[0]);
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

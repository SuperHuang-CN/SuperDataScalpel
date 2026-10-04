package cn.superhuang.data.scalpel.business.quality.repository;

import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityStatisticsItem;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import java.util.List;

/** One shared latest-result join keeps overview and drill-down scopes identical. */
@Repository
public class ModelQualityStatisticsRepository {
    private static final String VALID = "r.executionMode='REAL' and r.status='SUCCESS' and r.qualityConclusion is not null and r.qualityRuleSnapshotAt is not null and r.endedAt is not null";
    private static final String SOURCE = """
        from DataModel m left join TaskRun r on r.qualityTargetModelId=m.id and
        """ + VALID + """
         and not exists (select n.id from TaskRun n where n.qualityTargetModelId=m.id
          and n.executionMode='REAL' and n.status='SUCCESS' and n.qualityConclusion is not null
          and n.qualityRuleSnapshotAt is not null and n.endedAt is not null
          and (n.endedAt>r.endedAt or (n.endedAt=r.endedAt and n.id>r.id)))
        where m.status='PUBLISHED'
        """;
    private static final String LATEST_FAILED = """
        exists (select f.id from TaskRun f where f.qualityTargetModelId=m.id and f.executionMode='REAL'
         and f.status in ('FAILED','TIMED_OUT')
         and not exists (select n.id from TaskRun n where n.qualityTargetModelId=m.id and n.executionMode='REAL'
          and (n.queuedAt>f.queuedAt or (n.queuedAt=f.queuedAt and n.id>f.id))))
        """;
    private final EntityManager em;
    public ModelQualityStatisticsRepository(EntityManager em) { this.em=em; }
    public List<Object[]> groups() {
        return em.createQuery("select r.qualityConclusion, count(m), min(r.endedAt), max(r.endedAt) " + SOURCE
            + " group by r.qualityConclusion", Object[].class).getResultList();
    }
    public long latestFailed() {
        return em.createQuery("select count(m) " + SOURCE + " and " + LATEST_FAILED, Long.class).getSingleResult();
    }
    private static String filter(String result) {
        return switch (result) {
            case "PASSED" -> " and r.qualityConclusion='PASSED'";
            case "FAILED" -> " and r.qualityConclusion='FAILED'";
            case "NONE" -> " and r.id is null";
            case "EXECUTION_FAILED" -> " and " + LATEST_FAILED;
            default -> "";
        };
    }
    public long count(String result) {
        return em.createQuery("select count(m) " + SOURCE + filter(result), Long.class).getSingleResult();
    }
    public List<ModelQualityStatisticsItem> items(String result, org.springframework.data.domain.Pageable pageable) {
        return em.createQuery("select new cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityStatisticsItem("
            + "m.id,m.name,r.id,r.qualityConclusion,r.endedAt,r.qualityRuleSnapshotAt," + LATEST_FAILED + ") "
            + SOURCE + filter(result) + " order by m.name,m.id", ModelQualityStatisticsItem.class)
            .setFirstResult(Math.toIntExact(pageable.getOffset())).setMaxResults(pageable.getPageSize()).getResultList();
    }
}

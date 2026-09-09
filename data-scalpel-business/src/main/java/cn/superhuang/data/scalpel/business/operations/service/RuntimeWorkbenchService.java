package cn.superhuang.data.scalpel.business.operations.service;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import cn.superhuang.data.scalpel.business.operations.repository.*;
import cn.superhuang.data.scalpel.business.operations.web.request.RuntimeRunFilter;
import cn.superhuang.data.scalpel.business.operations.web.response.*;
import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.business.task.repository.*;
import cn.superhuang.data.scalpel.business.compute.domain.*;
import cn.superhuang.data.scalpel.business.compute.repository.ComputeEngineRepository;
import cn.superhuang.data.scalpel.business.compute.web.response.ComputeEngineRuntimeOverviewResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RuntimeWorkbenchService {
    private static final List<TaskRunStatus> ACTIVE = List.of(TaskRunStatus.QUEUED, TaskRunStatus.RUNNING, TaskRunStatus.CANCEL_REQUESTED, TaskRunStatus.STOP_REQUESTED);
    private static final String BATCH = "(r.taskType is null or r.taskType not in ('SPARK_STREAMING_CANVAS','SPARK_STREAMING_JAR'))";
    private final TaskRunRepository runs;
    private final DataTaskRepository tasks;
    private final TaskStreamingDeploymentRepository deployments;
    private final TaskStreamingQueryRepository queries;
    private final ComputeEngineRepository engines;
    private final EngineObservationRepository observations;
    private final AlertIncidentRepository incidents;
    private final AlertSignalRepository signals;
    private final AlertDeliveryRepository deliveries;
    private final OperationsAccess access;
    private final SearchEngine search;
    private final OperationsProperties properties;
    private final EntityManager em;
    private final ObjectMapper json;
    public RuntimeWorkbenchService(TaskRunRepository runs, DataTaskRepository tasks, TaskStreamingDeploymentRepository deployments,
        TaskStreamingQueryRepository queries, ComputeEngineRepository engines, EngineObservationRepository observations,
        AlertIncidentRepository incidents, AlertSignalRepository signals, AlertDeliveryRepository deliveries, OperationsAccess access,
        SearchEngine search, OperationsProperties properties, EntityManager em, ObjectMapper json) {
        this.runs = runs; this.tasks = tasks; this.deployments = deployments; this.queries = queries; this.engines = engines;
        this.observations = observations; this.incidents = incidents; this.signals = signals; this.deliveries = deliveries;
        this.access = access; this.search = search; this.properties = properties; this.em = em; this.json = json;
    }
    public RuntimeOverviewResponse overview(Instant from, Instant to) {
        var actor = access.actor(); Instant now = Instant.now();
        Instant end = to == null ? now : to, start = from == null ? end.minus(Duration.ofHours(24)) : from;
        validateWindow(start, end);
        RuntimeTaskMetrics taskMetrics = null;
        if (actor.has("task.view")) {
            var current = statusCounts("r.executionMode = 'REAL' and r.status in :active", Map.of("active", ACTIVE));
            var completed = statusCounts("r.executionMode = 'REAL' and " + BATCH + " and r.endedAt >= :from and r.endedAt < :to", Map.of("from", start, "to", end));
            long denominator = completed.getOrDefault(TaskRunStatus.SUCCESS, 0L) + completed.getOrDefault(TaskRunStatus.FAILED, 0L) + completed.getOrDefault(TaskRunStatus.TIMED_OUT, 0L);
            long quality = count("select count(r) from TaskRun r where r.executionMode='REAL' and r.status='SUCCESS' and r.qualityConclusion='FAILED' and r.endedAt>=:from and r.endedAt<:to", Map.of("from", start, "to", end));
            boolean daily = Duration.between(start, end).compareTo(Duration.ofDays(3)) > 0;
            String bucketExpression = "truncate(r.endedAt, " + (daily ? "day" : "hour") + ")";
            var buckets = em.createQuery("select " + bucketExpression + ", r.status, count(r) from TaskRun r where r.executionMode='REAL' and " + BATCH
                    + " and r.endedAt>=:from and r.endedAt<:to group by " + bucketExpression + ", r.status order by " + bucketExpression, Object[].class)
                    .setParameter("from", start).setParameter("to", end).getResultList();
            var grouped = new HashMap<Instant, Map<TaskRunStatus, Long>>();
            buckets.forEach(row -> grouped.computeIfAbsent((Instant) row[0], key -> new EnumMap<>(TaskRunStatus.class))
                    .put((TaskRunStatus) row[1], ((Number) row[2]).longValue()));
            var trend = new ArrayList<RuntimeTrendResponse>();
            var unit = daily ? java.time.temporal.ChronoUnit.DAYS : java.time.temporal.ChronoUnit.HOURS;
            for (Instant cursor = start.truncatedTo(unit); cursor.isBefore(end); cursor = cursor.plus(1, unit)) {
                Instant left = cursor.isBefore(start) ? start : cursor;
                Instant right = cursor.plus(1, unit).isAfter(end) ? end : cursor.plus(1, unit);
                var counts = grouped.getOrDefault(cursor, Map.of(TaskRunStatus.SUCCESS, 0L));
                counts.forEach((status, amount) -> trend.add(new RuntimeTrendResponse(left, right, status, amount)));
            }
            taskMetrics = new RuntimeTaskMetrics(current, completed, quality, denominator == 0 ? null : (double) completed.getOrDefault(TaskRunStatus.SUCCESS, 0L) / denominator, trend);
        }
        RuntimeEngineMetrics engineMetrics = null;
        if (actor.has("compute.engine.view")) {
            String join = " from ComputeEngine e left join EngineObservation o on o.engineId=e.id ";
            String active = " e.registrationState='ACTIVE' ";
            var parameters = Map.<String, Object>of("fresh", now.minus(properties.observationStaleAfter()));
            long total = engines.count(), activated = count("select count(e)" + join + "where" + active, Map.of());
            long unreachable = count("select count(e)" + join + "where" + active + "and o.observedAt>=:fresh and o.state='UNREACHABLE'", parameters);
            long notReady = count("select count(e)" + join + "where" + active + "and o.observedAt>=:fresh and o.state='REACHABLE' and o.dependenciesReady=false", parameters);
            long unknown = count("select count(e)" + join + "where" + active + "and (o.observedAt is null or o.observedAt<:fresh or o.state='UNKNOWN' or (o.state='REACHABLE' and o.dependenciesReady is null))", parameters);
            engineMetrics = new RuntimeEngineMetrics(total, activated, unreachable, notReady, unknown);
        }
        long alerts = incidents.count((r, q, b) -> b.and(r.get("ruleType").in(actor.visibleTypes()), b.notEqual(r.get("status"), AlertHandlingStatus.CLOSED)));
        Long pending = actor.has("alert.manage") ? signals.count((r, q, b) -> b.and(b.equal(r.get("status"), AlertSignalStatus.PENDING), r.get("ruleType").in(actor.visibleTypes()))) : null;
        Long failed = actor.has("alert.manage") ? deliveries.count((r, q, b) -> b.and(b.equal(r.get("status"), AlertDeliveryStatus.FAILED),
                b.or(r.get("ruleType").in(actor.visibleTypes()), b.isNull(r.get("ruleType"))))) : null;
        return new RuntimeOverviewResponse(start, end, now, taskMetrics, engineMetrics, alerts, pending, failed);
    }
    public PageResponse<RuntimeRunResponse> runs(SearchRequest request, RuntimeRunFilter filter) {
        access.actor().require("task.view");
        Instant to = filter.to() == null ? Instant.now() : filter.to(), from = filter.from() == null ? to.minus(Duration.ofHours(24)) : filter.from();
        if (!filter.activeOnly()) validateWindow(from, to);
        String timeField = filter.timeField() == null ? "queuedAt" : filter.timeField();
        if (!List.of("queuedAt", "endedAt").contains(timeField)) throw bad("时间字段仅支持提交时间或结束时间");
        Specification<TaskRun> scope = (r, q, b) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(b.equal(r.get("executionMode"), filter.mode() == null ? TaskRunExecutionMode.REAL : filter.mode()));
            if (filter.batchOnly()) predicates.add(b.or(b.isNull(r.get("taskType")),
                    b.not(r.get("taskType").in(TaskType.SPARK_STREAMING_CANVAS, TaskType.SPARK_STREAMING_JAR))));
            if (filter.activeOnly()) predicates.add(r.get("status").in(ACTIVE));
            else { predicates.add(b.greaterThanOrEqualTo(r.get(timeField), from)); predicates.add(b.lessThan(r.get(timeField), to)); }
            if (filter.taskName() != null && !filter.taskName().isBlank() || filter.directoryId() != null) {
                var sq = q.subquery(UUID.class); var t = sq.from(DataTask.class);
                var match = new ArrayList<jakarta.persistence.criteria.Predicate>();
                match.add(b.equal(t.get("id"), r.get("taskId")));
                if (filter.taskName() != null && !filter.taskName().isBlank()) match.add(b.like(b.lower(t.get("name")), "%" + escape(filter.taskName().trim().toLowerCase(Locale.ROOT)) + "%", '\\'));
                if (filter.directoryId() != null) match.add(b.equal(t.get("directoryId"), filter.directoryId()));
                sq.select(t.get("id")).where(match.toArray(jakarta.persistence.criteria.Predicate[]::new)); predicates.add(b.exists(sq));
            }
            return b.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var p = search.search(request, TaskRun.class, runs, scope, RuntimeRunProjection.class);
        var taskMap = tasks.findAllById(p.getContent().stream().map(RuntimeRunProjection::taskId).distinct().toList()).stream().collect(Collectors.toMap(DataTask::getId, Function.identity()));
        var engineNames = engineNames(p.getContent().stream().map(RuntimeRunProjection::computeEngineId).filter(Objects::nonNull).toList());
        return page(p, r -> {
            var task = taskMap.get(r.taskId());
            return new RuntimeRunResponse(r.id(), r.taskId(), r.parentRunId(), r.workflowNodeId(), task == null ? "已删除任务" : task.getName(), task == null ? null : task.getDirectoryId(), task != null,
                    r.taskType() == null ? TaskType.LOCAL_SQL : r.taskType(), r.computeEngineId(), engineNames.get(r.computeEngineId()), r.streamingDeploymentId(),
                    r.status(), r.executionMode(), r.triggerType(), r.queuedAt(), r.startedAt(), r.endedAt(), r.qualityConclusion(), r.qualityFailedRules(), r.errorCode(), r.errorDiagnosticId());
        });
    }
    public PageResponse<RuntimeStreamingResponse> streaming(SearchRequest request) {
        access.actor().require("task.view");
        Specification<TaskStreamingDeployment> scope = (r, q, b) -> {
            var newer = q.subquery(UUID.class); var n = newer.from(TaskStreamingDeployment.class);
            newer.select(n.get("id")).where(b.equal(n.get("taskId"), r.get("taskId")), b.equal(n.get("executionMode"), StreamingDeploymentExecutionMode.REAL),
                    b.or(b.greaterThan(n.get("definitionVersion"), r.get("definitionVersion")), b.and(b.equal(n.get("definitionVersion"), r.get("definitionVersion")), b.greaterThan(n.get("checkpointGeneration"), r.get("checkpointGeneration")))));
            return b.and(b.equal(r.get("executionMode"), StreamingDeploymentExecutionMode.REAL),
                    b.or(r.get("actualState").in(StreamingDeploymentActualState.STARTING, StreamingDeploymentActualState.RUNNING, StreamingDeploymentActualState.STOPPING), b.not(b.exists(newer))));
        };
        var p = search.search(request, TaskStreamingDeployment.class, deployments, scope);
        var taskMap = tasks.findAllById(p.getContent().stream().map(TaskStreamingDeployment::getTaskId).distinct().toList()).stream().collect(Collectors.toMap(DataTask::getId, Function.identity()));
        var engineNames = engineNames(p.getContent().stream().map(TaskStreamingDeployment::getComputeEngineId).filter(Objects::nonNull).toList());
        var queryMap = queries.findAllByDeploymentIdIn(p.getContent().stream().map(TaskStreamingDeployment::getId).toList()).stream().collect(Collectors.groupingBy(TaskStreamingQuery::getDeploymentId));
        return page(p, d -> {
            var t = taskMap.get(d.getTaskId());
            return new RuntimeStreamingResponse(d.getId(), d.getTaskId(), t == null ? "已删除任务" : t.getName(), t == null ? null : t.getType(), t != null,
                    d.getCurrentRunId(), d.getComputeEngineId(), engineNames.get(d.getComputeEngineId()), d.getActualState(), d.getDesiredState(), d.getStartedAt(),
                    d.getStoppedAt(), d.getLastProgressAt(), d.getLastProgressAt() == null || d.getLastProgressAt().plus(properties.observationStaleAfter()).isBefore(Instant.now()),
                    d.getSourceKind(), d.getCursorLagMillis(), d.getLastPollAt(), queryMap.getOrDefault(d.getId(), List.of()).stream().map(RuntimeStreamingQueryResponse::from).toList(),
                    d.getLastErrorAt(), d.getLastErrorAt() == null ? null : "最近实时运行异常结束，请查看运行详情");
        });
    }
    public PageResponse<RuntimeEngineResponse> engines(SearchRequest request) {
        access.actor().require("compute.engine.view");
        var p = search.search(request, ComputeEngine.class, engines);
        var obs = observations.findAllByEngineIdIn(p.getContent().stream().map(ComputeEngine::getId).toList()).stream().collect(Collectors.toMap(EngineObservation::getEngineId, Function.identity()));
        return page(p, e -> {
            var o = obs.get(e.getId());
            boolean stale = o == null || o.getObservedAt() == null || o.getObservedAt().plus(properties.observationStaleAfter()).isBefore(Instant.now()) || e.getRegistrationState() != ComputeEngineRegistrationState.ACTIVE;
            return new RuntimeEngineResponse(e.getId(), e.getName(), e.getExpectedBackendType(), e.getRegistrationState(),
                    stale ? EngineObservationState.UNKNOWN : o.getState(), stale ? null : o.getDependenciesReady(), stale,
                    o == null ? null : o.getAttemptedAt(), o == null ? null : o.getObservedAt(), o == null ? null : o.getLastHealthyAt(),
                    stale ? "没有当前有效观测" : o.getSummary(), o == null || o.getSnapshotJson() == null ? null : json.readValue(o.getSnapshotJson(), ComputeEngineRuntimeOverviewResponse.class));
        });
    }
    private Map<TaskRunStatus, Long> statusCounts(String where, Map<String, ?> parameters) {
        var query = em.createQuery("select r.status, count(r) from TaskRun r where " + where + " group by r.status", Object[].class);
        parameters.forEach(query::setParameter); Map<TaskRunStatus, Long> result = new EnumMap<>(TaskRunStatus.class);
        query.getResultList().forEach(row -> result.put((TaskRunStatus) row[0], ((Number) row[1]).longValue())); return result;
    }
    private long count(String hql, Map<String, ?> parameters) {
        var query = em.createQuery(hql, Long.class); parameters.forEach(query::setParameter); return query.getSingleResult();
    }
    private Map<UUID, String> engineNames(Collection<UUID> ids) {
        return engines.findAllById(ids).stream().collect(Collectors.toMap(ComputeEngine::getId, ComputeEngine::getName));
    }
    private static <T, R> PageResponse<R> page(org.springframework.data.domain.Page<T> p, Function<T, R> mapper) {
        return new PageResponse<>(p.getContent().stream().map(mapper).toList(), p.getTotalElements(), p.getTotalPages(), p.getNumber(), p.getSize());
    }
    private static void validateWindow(Instant from, Instant to) {
        if (!from.isBefore(to)) throw bad("开始时间必须早于结束时间");
        if (Duration.between(from, to).compareTo(Duration.ofDays(90)) > 0) throw bad("单次查询时间跨度不能超过 90 天");
    }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}

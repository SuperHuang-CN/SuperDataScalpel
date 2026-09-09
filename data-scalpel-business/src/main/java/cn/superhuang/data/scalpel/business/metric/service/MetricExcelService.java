package cn.superhuang.data.scalpel.business.metric.service;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.metric.domain.*;
import cn.superhuang.data.scalpel.business.metric.repository.DataMetricRepository;
import cn.superhuang.data.scalpel.business.metric.repository.MetricReleaseRepository;
import cn.superhuang.data.scalpel.business.metric.web.request.*;
import cn.superhuang.data.scalpel.business.metric.web.response.*;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class MetricExcelService {
    private static final Map<MetricKind, String> KINDS = Map.of(MetricKind.ATOMIC, "原子指标", MetricKind.DERIVED, "派生指标", MetricKind.COMPOSITE, "复合指标");
    private static final Map<MetricDefinition.Period, String> PERIODS = Map.of(
            MetricDefinition.Period.NONE, "无周期", MetricDefinition.Period.DAY, "日度", MetricDefinition.Period.WEEK, "周度",
            MetricDefinition.Period.MONTH, "月度", MetricDefinition.Period.QUARTER, "季度", MetricDefinition.Period.YEAR, "年度");
    private static final Map<MetricDefinition.ValueFormat, String> FORMATS = Map.of(
            MetricDefinition.ValueFormat.NUMBER, "普通数值", MetricDefinition.ValueFormat.RATIO, "比值百分比", MetricDefinition.ValueFormat.PERCENT_VALUE, "百分数值");
    private final MetricExcelCodec codec;
    private final MetricManagementService management;
    private final MetricHealthService health;
    private final DataMetricRepository metrics;
    private final MetricReleaseRepository releases;
    private final DirectoryService directories;
    private final SearchEngine search;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final EntityManager entityManager;

    public MetricExcelService(MetricExcelCodec codec, MetricManagementService management, MetricHealthService health,
                              DataMetricRepository metrics, MetricReleaseRepository releases, DirectoryService directories,
                              SearchEngine search, ObjectMapper mapper, Validator validator, EntityManager entityManager) {
        this.codec = codec; this.management = management; this.health = health; this.metrics = metrics;
        this.releases = releases; this.directories = directories; this.search = search; this.mapper = mapper;
        this.validator = validator; this.entityManager = entityManager;
    }

    public MetricExcelFile template() { return codec.template(); }

    public MetricExcelFile export(ExportMetricsRequest request) {
        if (request.mode() == MetricExportMode.DRAFT && !MetricAccess.has("metric.manage") && !MetricAccess.has("metric.publish"))
            throw new org.springframework.security.access.AccessDeniedException("导出草稿需要维护或发布权限");
        var paths = directories.pathIndex(DirectoryScope.METRIC);
        List<Map<String, String>> rows = new ArrayList<>();
        List<UUID> ids = request.ids() == null ? List.of() : request.ids().stream().distinct().toList();
        if (!ids.isEmpty()) {
            var selected = metrics.findAllById(ids);
            if (selected.size() != ids.size()) throw conflict("部分勾选指标已删除，请刷新列表");
            if (request.mode() == MetricExportMode.PUBLISHED && selected.stream().anyMatch(m -> m.getCurrentReleaseId() == null))
                throw conflict("勾选指标中存在未发布指标，请导出草稿或调整选择");
        }
        for (int page = 0; ; page++) {
            var result = search.search(new SearchRequest(request.search(), page, 500, "code"), DataMetric.class, metrics,
                    (root, query, cb) -> cb.and(ids.isEmpty() ? cb.conjunction() : root.get("id").in(ids),
                            request.mode() == MetricExportMode.PUBLISHED ? cb.isNotNull(root.get("currentReleaseId")) : cb.conjunction()));
            if (result.getTotalElements() > MetricExcelCodec.MAX_ROWS) throw bad("单次最多导出1000项，请缩小筛选范围");
            for (var metric : result.getContent()) {
                var path = paths.resolve(metric.getDirectoryId());
                if (!path.resolved()) throw conflict("指标“" + metric.getName() + "”目录已失效，请先调整目录");
                MetricDefinition definition = definition(metric.getDraftDefinition());
                String version = "";
                if (metric.getCurrentReleaseId() != null) {
                    var release = releases.findById(metric.getCurrentReleaseId()).orElseThrow(() -> conflict("发布版本已缺失"));
                    version = Integer.toString(release.getVersion());
                    if (request.mode() == MetricExportMode.PUBLISHED) definition = definition(release.getDefinitionSnapshot());
                }
                Map<String, String> row = values(metric, definition, path.path());
                row.put("metricId", metric.getId().toString());
                row.put("draftFingerprint", request.mode() == MetricExportMode.DRAFT ? metric.getDraftFingerprint() : "");
                row.put("basicsFingerprint", request.mode() == MetricExportMode.DRAFT ? basicsFingerprint(metric) : "");
                row.put("status", switch (metric.getStatus()) { case DRAFT -> "草稿"; case PUBLISHED -> "已发布"; case DISABLED -> "已停用"; });
                row.put("releaseVersion", version); rows.add(row);
            }
            if (!result.hasNext()) break;
        }
        if (!ids.isEmpty() && rows.size() != ids.size()) throw conflict("部分勾选指标不再符合筛选条件，请刷新列表后重新选择");
        if (rows.isEmpty()) throw bad("当前范围没有可导出的" + (request.mode() == MetricExportMode.PUBLISHED ? "发布版本" : "指标"));
        String suffix = request.mode() == MetricExportMode.DRAFT ? "草稿" : "发布口径";
        return new MetricExcelFile("DataScalpel-指标" + suffix + "-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + ".xlsx",
                codec.write(request.mode().name(), rows));
    }

    /** Read-only: parsing and planning never persist draft or staging records. */
    public MetricImportPreviewResponse preview(MultipartFile file) { return plan(codec.read(file), false).response(); }

    /** Rebuilds diagnostics from the uploaded workbook; does not trust client-supplied errors. */
    public MetricExcelFile errors(MultipartFile file) { return codec.errors(preview(file)); }

    @Transactional
    public MetricImportResultResponse importFile(MultipartFile file, String expectedPreviewFingerprint) {
        Plan plan = plan(codec.read(file), true);
        if (!plan.response().fingerprint().equals(expectedPreviewFingerprint)) throw conflict("文件或系统资料已变化，请重新预览后确认导入");
        if (!plan.response().canImport()) throw conflict("文件存在错误，未导入任何指标；请下载错误清单修正后重试");
        try {
            for (PreparedRow row : plan.prepared()) {
                if (row.action().equals("UNCHANGED")) continue;
                DataMetric existing = row.existing();
                UUID id;
                String expectedDraft;
                if (existing == null) {
                    id = management.create(row.basics()).id();
                    expectedDraft = management.require(id).getDraftFingerprint();
                } else {
                    id = existing.getId(); expectedDraft = existing.getDraftFingerprint();
                    var b = row.basics();
                    management.update(id, new UpdateMetricRequest(b.name(), b.kind(), b.directoryId(), b.ownerName(), b.summary()));
                }
                management.saveDraft(id, new UpdateMetricDefinitionRequest(row.definition(), expectedDraft));
            }
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "指标编码或目录在导入期间发生变化，整批未导入，请重新预览", e);
        }
        return new MetricImportResultResponse(plan.response().createCount(), plan.response().updateCount(), plan.response().unchangedCount());
    }

    private Plan plan(MetricExcelCodec.ParsedWorkbook workbook, boolean lock) {
        var paths = directories.pathIndex(DirectoryScope.METRIC);
        Map<String, DataMetric> existingByCode = new HashMap<>();
        Map<String, Long> counts = new HashMap<>();
        for (var row : workbook.rows()) counts.merge(row.get("code"), 1L, Long::sum);
        // Consistent lock order; refresh avoids using entities loaded before another transaction committed.
        counts.keySet().stream().sorted().forEach(code -> metrics.findByCode(code).ifPresent(m -> {
            if (lock) entityManager.refresh(m, LockModeType.PESSIMISTIC_WRITE);
            existingByCode.put(code, m);
        }));
        List<PreparedRow> prepared = new ArrayList<>();
        List<MetricImportRowResponse> responseRows = new ArrayList<>();
        List<Object> expectedState = new ArrayList<>();
        int creates = 0, updates = 0, unchanged = 0, errors = 0, warnings = 0;
        for (var row : workbook.rows()) {
            List<MetricImportIssueResponse> issues = new ArrayList<>(row.issues());
            String code = row.get("code");
            DataMetric existing = existingByCode.get(code);
            if (counts.get(code) > 1) issue(row, issues, "code", "同一文件内指标编码重复", true);
            MetricKind kind = enumeration(row, issues, "kind", KINDS, MetricKind.DERIVED, false);
            MetricDefinition.Period period = enumeration(row, issues, "statisticalPeriod", PERIODS, MetricDefinition.Period.NONE, true);
            MetricDefinition.ValueFormat format = enumeration(row, issues, "valueFormat", FORMATS, MetricDefinition.ValueFormat.NUMBER, true);
            int places = 2;
            if (!row.get("decimalPlaces").isEmpty()) try {
                places = new java.math.BigDecimal(row.get("decimalPlaces")).intValueExact();
            } catch (NumberFormatException | ArithmeticException e) { issue(row, issues, "decimalPlaces", "请填写0至10的整数", true); }
            var directory = paths.resolve(row.get("directoryPath"));
            if (!directory.resolved()) issue(row, issues, "directoryPath", directory.issue().replace("模型目录", "指标目录"), true);
            var basics = new CreateMetricRequest(code, row.get("name"), kind, directory.directoryId(), optional(row.get("ownerName")), optional(row.get("summary")));
            MetricDefinition prior = existing == null ? MetricDefinition.empty() : definition(existing.getDraftDefinition());
            var next = new MetricDefinition(optional(row.get("businessMeaning")), optional(row.get("calculation")), optional(row.get("statisticalScope")),
                    optional(row.get("timeDescription")), optional(row.get("sourceGrain")), optional(row.get("grainDescription")), optional(row.get("unit")),
                    period, optional(row.get("periodFormat")), optional(row.get("nullHandling")), optional(row.get("aggregationDescription")),
                    optional(row.get("updateDescription")), places, format, prior.binding(), prior.references());
            validate(row, issues, basics); validate(row, issues, next);
            if (existing == null) {
                if (!row.get("metricId").isEmpty() || !row.get("draftFingerprint").isEmpty() || !row.get("basicsFingerprint").isEmpty())
                    issue(row, issues, "code", "导出的指标已不存在或编码被修改；新增指标请使用空白模板行", true);
            } else {
                if (!existing.getId().toString().equals(row.get("metricId"))) issue(row, issues, "metricId", "更新已有指标须使用该指标的草稿导出行，且不能修改编码或隐藏标识", true);
                if (!existing.getDraftFingerprint().equals(row.get("draftFingerprint"))) issue(row, issues, "draftFingerprint", "草稿摘要缺失或草稿已变更，请重新导出草稿后整理", true);
                if (!basicsFingerprint(existing).equals(row.get("basicsFingerprint"))) issue(row, issues, "basicsFingerprint", "资料摘要缺失或资料、状态已变更，请重新导出草稿后整理", true);
                if (existing.getCurrentReleaseId() != null && existing.getKind() != kind) issue(row, issues, "kind", "首次发布后不能修改指标类型", true);
            }
            Map<String, String> before = existing == null ? Map.of() : values(existing, prior, paths.resolve(existing.getDirectoryId()).path());
            DataMetric candidate = DataMetric.create(code);
            candidate.update(basics.name(), kind, basics.directoryId(), basics.ownerName(), basics.summary());
            Map<String, String> after = values(candidate, next, directory.path());
            List<MetricImportChangeResponse> changes = new ArrayList<>();
            for (var column : MetricExcelCodec.COLUMNS.subList(0, 20)) {
                String a = before.getOrDefault(column.key(), ""), b = after.getOrDefault(column.key(), "");
                if (!Objects.equals(a, b)) changes.add(new MetricImportChangeResponse(column.title(), a, b));
            }
            boolean blocked = issues.stream().anyMatch(MetricImportIssueResponse::blocking);
            if (!blocked) for (var problem : health.inspect(candidate, next, health.references(next), null).issues())
                issue(row, issues, problem.path().startsWith("binding") || problem.path().startsWith("references") ? "保留的绑定/参考资料" : problem.path(), problem.message(), false);
            String action = existing == null ? "CREATE" : changes.isEmpty() ? "UNCHANGED" : "UPDATE";
            if (blocked) action = "ERROR";
            switch (action) { case "CREATE" -> creates++; case "UPDATE" -> updates++; case "UNCHANGED" -> unchanged++; default -> errors++; }
            warnings += (int) issues.stream().filter(i -> !i.blocking()).count();
            prepared.add(new PreparedRow(existing, basics, next, action));
            responseRows.add(new MetricImportRowResponse(row.rowNumber(), code, row.get("name"), action, List.copyOf(changes), List.copyOf(issues)));
            expectedState.add(Arrays.asList(row.rowNumber(), code, existing == null ? null : existing.getId(),
                    existing == null ? null : existing.getDraftFingerprint(), existing == null ? null : basicsFingerprint(existing), directory.directoryId()));
        }
        String fingerprint = hash(mapper.writeValueAsString(Arrays.asList(workbook.fileDigest(), expectedState)));
        var response = new MetricImportPreviewResponse(fingerprint, responseRows.size(), creates, updates, unchanged, errors, warnings, errors == 0, List.copyOf(responseRows));
        return new Plan(response, prepared);
    }

    private void validate(MetricExcelCodec.SheetRow row, List<MetricImportIssueResponse> issues, Object input) {
        validator.validate(input).stream().sorted(Comparator.comparing(v -> v.getPropertyPath().toString())).forEach(v -> {
            String key = v.getPropertyPath().toString();
            String message = switch (key) {
                case "code" -> "编码须以小写字母开头，仅含小写字母、数字和下划线，最长64位";
                case "name" -> "名称不能为空且不能超过100字";
                case "decimalPlaces" -> "请填写0至10的整数";
                default -> "内容不符合字段长度或格式要求，请参照模板“填写说明”";
            };
            issue(row, issues, key, message, true);
        });
    }

    private static <E extends Enum<E>> E enumeration(MetricExcelCodec.SheetRow row, List<MetricImportIssueResponse> issues,
                                                     String key, Map<E, String> labels, E defaultValue, boolean allowEmpty) {
        String raw = row.get(key);
        if (raw.isEmpty() && allowEmpty) return defaultValue;
        for (var entry : labels.entrySet()) if (entry.getKey().name().equals(raw) || entry.getValue().equals(raw)) return entry.getKey();
        issue(row, issues, key, "请选择：" + String.join("、", labels.values()), true);
        return defaultValue;
    }
    private MetricDefinition definition(String json) { return mapper.readValue(json, MetricDefinition.class); }
    private String basicsFingerprint(DataMetric metric) {
        return hash(mapper.writeValueAsString(Arrays.asList(metric.getId(), metric.getCode(), metric.getName(), metric.getKind(),
                metric.getDirectoryId(), metric.getOwnerName(), metric.getSummary(), metric.getStatus(), metric.getCurrentReleaseId())));
    }
    private static Map<String, String> values(DataMetric m, MetricDefinition d, String directory) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("code", m.getCode()); values.put("name", m.getName()); values.put("kind", KINDS.get(m.getKind()));
        values.put("directoryPath", directory); values.put("ownerName", m.getOwnerName()); values.put("summary", m.getSummary());
        values.put("businessMeaning", d.businessMeaning()); values.put("calculation", d.calculation()); values.put("statisticalScope", d.statisticalScope());
        values.put("sourceGrain", d.sourceGrain()); values.put("statisticalPeriod", PERIODS.get(d.statisticalPeriod()));
        values.put("timeDescription", d.timeDescription()); values.put("grainDescription", d.grainDescription()); values.put("periodFormat", d.periodFormat());
        values.put("unit", d.unit()); values.put("nullHandling", d.nullHandling()); values.put("aggregationDescription", d.aggregationDescription());
        values.put("updateDescription", d.updateDescription()); values.put("decimalPlaces", d.decimalPlaces().toString()); values.put("valueFormat", FORMATS.get(d.valueFormat()));
        values.replaceAll((key, value) -> Objects.toString(value, "")); return values;
    }
    private static void issue(MetricExcelCodec.SheetRow row, List<MetricImportIssueResponse> issues, String key, String message, boolean blocking) {
        issues.add(new MetricImportIssueResponse(row.rowNumber(), MetricExcelCodec.label(key), message, blocking));
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String hash(String value) { return MetricExcelCodec.digest(value.getBytes(StandardCharsets.UTF_8)); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private record PreparedRow(DataMetric existing, CreateMetricRequest basics, MetricDefinition definition, String action) {}
    private record Plan(MetricImportPreviewResponse response, List<PreparedRow> prepared) {}
}

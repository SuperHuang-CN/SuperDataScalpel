package cn.superhuang.data.scalpel.business.metric;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.metric.domain.*;
import cn.superhuang.data.scalpel.business.metric.repository.*;
import cn.superhuang.data.scalpel.business.metric.service.*;
import cn.superhuang.data.scalpel.business.metric.web.request.*;
import cn.superhuang.data.scalpel.business.metric.web.response.MetricHealthResponse;
import cn.superhuang.data.scalpel.business.metric.web.response.MetricResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MetricExcelTest {
    private final MetricExcelCodec codec = new MetricExcelCodec();
    private final MetricManagementService management = mock(MetricManagementService.class);
    private final MetricHealthService health = mock(MetricHealthService.class);
    private final DataMetricRepository metrics = mock(DataMetricRepository.class);
    private final MetricReleaseRepository releases = mock(MetricReleaseRepository.class);
    private final DirectoryService directories = mock(DirectoryService.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final SearchEngine search = mock(SearchEngine.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ValidatorFactory validators = Validation.buildDefaultValidatorFactory();
    private MetricExcelService service;
    private DataMetric metric;
    private MetricDefinition original;

    @BeforeEach
    void prepare() {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "", List.of(new SimpleGrantedAuthority("metric.manage"), new SimpleGrantedAuthority("metric.view"))));
        service = new MetricExcelService(codec, management, health, metrics, releases, directories, search, mapper, validators.getValidator(), entityManager);
        var paths = mock(DirectoryService.DirectoryPathIndex.class);
        when(directories.pathIndex(DirectoryScope.METRIC)).thenReturn(paths);
        when(paths.resolve((String) any())).thenAnswer(i -> new DirectoryService.DirectoryPathResolution(null, i.getArgument(0), null));
        when(paths.resolve((UUID) isNull())).thenReturn(new DirectoryService.DirectoryPathResolution(null, "", null));
        when(health.references(any())).thenReturn(List.of());
        when(health.inspect(any(), any(), anyList(), isNull())).thenReturn(new MetricHealthResponse(true, "VALID", List.of()));
        UUID modelId = UUID.randomUUID();
        var binding = new MetricDefinition.Binding(modelId, UUID.randomUUID(), null, List.of(), List.of(), List.of());
        var reference = new MetricDefinition.Reference(MetricDefinition.ResourceKind.MODEL, UUID.randomUUID(), null, null, "来源", null);
        original = new MetricDefinition("已有含义", "已有口径", null, null, null, null, "件", MetricDefinition.Period.NONE,
                null, null, null, null, 2, MetricDefinition.ValueFormat.NUMBER, binding, List.of(reference));
        metric = DataMetric.create("existing_metric");
        ReflectionTestUtils.setField(metric, "id", UUID.randomUUID());
        metric.update("已有指标", MetricKind.DERIVED, null, "负责人", "简介");
        metric.saveDraft(mapper.writeValueAsString(original), "draft-baseline");
        when(metrics.findByCode("existing_metric")).thenReturn(Optional.of(metric));
        when(search.search(any(SearchRequest.class), eq(DataMetric.class), eq(metrics), any(Specification.class))).thenReturn(new PageImpl<>(List.of(metric)));
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); validators.close(); }

    @Test
    void templateContainsInstructionsDropdownsAndNoImportableExample() throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(codec.template().bytes()))) {
            var sheet = workbook.getSheet("指标");
            assertEquals(0, sheet.getLastRowNum());
            assertEquals(3, sheet.getDataValidations().size());
            assertTrue(sheet.isColumnHidden(20));
            assertNotNull(workbook.getSheet("填写说明"));
        }
        assertThrows(ResponseStatusException.class, () -> service.preview(file(codec.template().bytes())));
    }

    @Test
    void newIncompleteRowsCanPreviewAndDuplicateCodesBlockWholeBatch() {
        var row = Map.of("code", "new_metric", "name", "新指标", "kind", "派生指标");
        var accepted = service.preview(file(codec.write("DRAFT", List.of(row))));
        assertTrue(accepted.canImport()); assertEquals(1, accepted.createCount());
        var duplicateFile = file(codec.write("DRAFT", List.of(row, row)));
        var duplicate = service.preview(duplicateFile);
        assertFalse(duplicate.canImport()); assertEquals(2, duplicate.errorCount());
        assertThrows(ResponseStatusException.class, () -> service.importFile(duplicateFile, duplicate.fingerprint()));
        verifyNoInteractions(management);
    }

    @Test
    void editedExportUpdatesDraftAndPreservesBindingsAndReferences() throws Exception {
        var source = exportedDraft();
        var changed = file(edit(source, "计算口径", "修改后的计算口径"));
        var preview = service.preview(changed);
        assertTrue(preview.canImport()); assertEquals(1, preview.updateCount());
        assertEquals("已有口径", preview.rows().getFirst().changes().getFirst().before());
        var result = service.importFile(changed, preview.fingerprint());
        assertEquals(1, result.updated());
        verify(entityManager).refresh(metric, LockModeType.PESSIMISTIC_WRITE);
        var saved = ArgumentCaptor.forClass(UpdateMetricDefinitionRequest.class);
        verify(management).saveDraft(eq(metric.getId()), saved.capture());
        assertEquals(original.binding(), saved.getValue().definition().binding());
        assertEquals(original.references(), saved.getValue().definition().references());
        assertEquals("修改后的计算口径", saved.getValue().definition().calculation());
        verify(management, never()).publish(any(), any(), any());
        verify(management, never()).disable(any());
        assertEquals(MetricStatus.DRAFT, metric.getStatus());
    }

    @Test
    void newMetricConfirmationCreatesOnlyDraftWithDefaults() {
        var source = file(codec.write("DRAFT", List.of(Map.of("code", "new_metric", "name", "新指标", "kind", "原子指标"))));
        var preview = service.preview(source);
        var created = DataMetric.create("new_metric");
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(created, "id", id);
        created.saveDraft(mapper.writeValueAsString(MetricDefinition.empty()), "empty-baseline");
        var response = mock(MetricResponse.class);
        when(response.id()).thenReturn(id);
        when(management.create(any())).thenReturn(response);
        when(management.require(id)).thenReturn(created);

        assertEquals(1, service.importFile(source, preview.fingerprint()).created());
        var saved = ArgumentCaptor.forClass(UpdateMetricDefinitionRequest.class);
        verify(management).saveDraft(eq(id), saved.capture());
        assertEquals(MetricDefinition.empty(), saved.getValue().definition());
        assertEquals("empty-baseline", saved.getValue().expectedDraftFingerprint());
        verify(management, never()).publish(any(), any(), any());
        verify(management, never()).disable(any());
    }

    @Test
    void blankOptionalCellsClearOnlyCorrespondingMetadata() throws Exception {
        var source = file(edit(edit(exportedDraft(), "简介", ""), "计算口径", ""));
        var preview = service.preview(source);
        assertTrue(preview.canImport());
        service.importFile(source, preview.fingerprint());
        var basics = ArgumentCaptor.forClass(UpdateMetricRequest.class);
        verify(management).update(eq(metric.getId()), basics.capture());
        assertNull(basics.getValue().summary());
        assertEquals(metric.getOwnerName(), basics.getValue().ownerName());
        var saved = ArgumentCaptor.forClass(UpdateMetricDefinitionRequest.class);
        verify(management).saveDraft(eq(metric.getId()), saved.capture());
        assertNull(saved.getValue().definition().calculation());
        assertEquals(original.binding(), saved.getValue().definition().binding());
        assertEquals(original.references(), saved.getValue().definition().references());
    }

    @Test
    void staleOfflineDraftAndPreviewChangesCannotOverwrite() throws Exception {
        byte[] exported = exportedDraft();
        var incoming = file(edit(exported, "简介", "新的简介"));
        var preview = service.preview(incoming);
        assertTrue(preview.canImport());
        metric.saveDraft(metric.getDraftDefinition(), "someone-else-edited");
        assertFalse(service.preview(incoming).canImport());
        assertThrows(ResponseStatusException.class, () -> service.importFile(incoming, preview.fingerprint()));
        verifyNoInteractions(management);
    }

    @Test
    void editedFileAndChangedBasicsRequireNewPreview() throws Exception {
        byte[] exported = exportedDraft();
        var first = file(edit(exported, "简介", "版本A"));
        var preview = service.preview(first);
        var second = file(edit(exported, "简介", "版本B"));
        assertThrows(ResponseStatusException.class, () -> service.importFile(second, preview.fingerprint()));
        metric.update("别人修改的名称", metric.getKind(), null, "负责人", "简介");
        assertFalse(service.preview(first).canImport());
        verifyNoInteractions(management);
    }

    @Test
    void unchangedExportIsNoOpAndMissingBaselineCannotUpdateExistingCode() {
        var unchanged = service.preview(file(exportedDraft()));
        assertTrue(unchanged.canImport()); assertEquals(1, unchanged.unchangedCount());
        var manual = service.preview(file(codec.write("DRAFT", List.of(Map.of("code", metric.getCode(), "name", "新名字", "kind", "派生指标")))));
        assertFalse(manual.canImport());
    }

    @Test
    void publishedExportUsesReleaseAndCannotBeImported() throws Exception {
        var releaseDefinition = new MetricDefinition("发布含义", "发布口径", null, null, null, null, "件", MetricDefinition.Period.NONE,
                null, null, null, null, 2, MetricDefinition.ValueFormat.NUMBER, null, List.of());
        var release = MetricRelease.create(metric.getId(), 1, mapper.writeValueAsString(releaseDefinition), "[]", "fp", "admin", null);
        UUID releaseId = UUID.randomUUID();
        ReflectionTestUtils.setField(release, "id", releaseId);
        metric.publish(releaseId); when(releases.findById(releaseId)).thenReturn(Optional.of(release));
        byte[] bytes = service.export(new ExportMetricsRequest(MetricExportMode.PUBLISHED, List.of(), null)).bytes();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals("发布口径", workbook.getSheet("指标").getRow(1).getCell(column("计算口径")).getStringCellValue());
        }
        assertThrows(ResponseStatusException.class, () -> service.preview(file(bytes)));
        var changedType = service.preview(file(edit(exportedDraft(), "指标类型*", "原子指标")));
        assertFalse(changedType.canImport());
        assertTrue(changedType.rows().getFirst().issues().stream().anyMatch(i -> i.message().contains("首次发布后")));
        metric.disable();
        var editedDisabled = file(edit(exportedDraft(), "计算口径", "停用期间修改口径"));
        var preview = service.preview(editedDisabled);
        service.importFile(editedDisabled, preview.fingerprint());
        assertEquals(MetricStatus.DISABLED, metric.getStatus());
        verify(management, never()).publish(any(), any(), any());
    }

    @Test
    void roundedDisplayCannotHideInvalidDecimalPlaces() throws Exception {
        byte[] bytes = codec.write("DRAFT", List.of(Map.of("code", "new_metric", "name", "新指标", "kind", "派生指标", "decimalPlaces", "2")));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes)); var out = new ByteArrayOutputStream()) {
            workbook.getSheet("指标").getRow(1).getCell(column("显示小数位")).setCellValue(2.5);
            workbook.write(out); bytes = out.toByteArray();
        }
        var preview = service.preview(file(bytes));
        assertFalse(preview.canImport());
        assertTrue(preview.rows().getFirst().issues().stream().anyMatch(i -> i.column().equals("显示小数位")));
    }

    @Test
    void selectionCannotBeSilentlyDroppedAndViewOnlyCannotExportDrafts() {
        var another = DataMetric.create("another_metric");
        UUID anotherId = UUID.randomUUID();
        ReflectionTestUtils.setField(another, "id", anotherId);
        var ids = List.of(metric.getId(), anotherId);
        when(metrics.findAllById(ids)).thenReturn(List.of(metric, another));
        assertThrows(ResponseStatusException.class, () -> service.export(new ExportMetricsRequest(MetricExportMode.DRAFT, ids, null)));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "viewer", "", List.of(new SimpleGrantedAuthority("metric.view"))));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, this::exportedDraft);
    }

    @Test
    void formulaCellsProduceRowAndColumnErrorsWithoutEvaluation() throws Exception {
        byte[] bytes = codec.write("DRAFT", List.of(Map.of("code", "new_metric", "name", "新指标", "kind", "派生指标")));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes)); var out = new ByteArrayOutputStream()) {
            workbook.getSheet("指标").getRow(1).getCell(column("计算口径")).setCellFormula("1+1");
            workbook.write(out); bytes = out.toByteArray();
        }
        var preview = service.preview(file(bytes));
        assertFalse(preview.canImport());
        var issue = preview.rows().getFirst().issues().getFirst();
        assertEquals(2, issue.rowNumber()); assertEquals("计算口径", issue.column());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(codec.errors(preview).bytes()))) {
            assertEquals("计算口径", workbook.getSheet("错误清单").getRow(1).getCell(2).getStringCellValue());
        }
    }

    private byte[] exportedDraft() { return service.export(new ExportMetricsRequest(MetricExportMode.DRAFT, List.of(), null)).bytes(); }
    private static MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file", "metrics.xlsx", MetricExcelCodec.CONTENT_TYPE, bytes); }
    private static int column(String title) {
        for (int i = 0; i < MetricExcelCodec.COLUMNS.size(); i++) if (MetricExcelCodec.COLUMNS.get(i).title().equals(title)) return i;
        throw new IllegalArgumentException(title);
    }
    private static byte[] edit(byte[] bytes, String title, String value) throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes)); var out = new ByteArrayOutputStream()) {
            workbook.getSheet("指标").getRow(1).getCell(column(title)).setCellValue(value);
            workbook.write(out); return out.toByteArray();
        }
    }
}

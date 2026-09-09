package cn.superhuang.data.scalpel.business.metric;

import cn.superhuang.data.scalpel.business.metric.domain.*;
import cn.superhuang.data.scalpel.business.metric.repository.*;
import cn.superhuang.data.scalpel.business.metric.service.*;
import cn.superhuang.data.scalpel.business.model.domain.*;
import cn.superhuang.data.scalpel.business.model.repository.*;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.web.error.CodedProblemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetricBindingTest {
    private final DataModelRepository models = mock(DataModelRepository.class);
    private final DataModelFieldRepository fields = mock(DataModelFieldRepository.class);
    private final DataMetricRepository metrics = mock(DataMetricRepository.class);
    private final MetricReferenceRepository references = mock(MetricReferenceRepository.class);
    private final UUID modelId = UUID.randomUUID();
    private final UUID valueId = UUID.randomUUID();
    private final DataModel model = mock(DataModel.class);
    private final DataModelField value = mock(DataModelField.class);
    private final DataMetric metric = mock(DataMetric.class);
    private MetricHealthService service;

    @BeforeEach
    void prepare() {
        service = new MetricHealthService(models, fields, metrics, mock(MetricReleaseRepository.class), mock(DataServiceRepository.class));
        when(models.findById(modelId)).thenReturn(Optional.of(model));
        when(models.findByIdForUpdate(modelId)).thenReturn(Optional.of(model));
        when(model.getName()).thenReturn("月度办件");
        when(model.getCode()).thenReturn("monthly_cases");
        when(model.getStorageDataSourceId()).thenReturn(UUID.randomUUID());
        when(model.getSchemaName()).thenReturn("mart");
        when(model.getPhysicalTableName()).thenReturn("monthly_cases");
        when(model.getPhysicalTableMode()).thenReturn(PhysicalTableMode.MANAGED);
        when(model.getStatus()).thenReturn(DataModelStatus.PUBLISHED);
        when(fields.findById(valueId)).thenReturn(Optional.of(value));
        when(value.getModelId()).thenReturn(modelId);
        when(value.getCode()).thenReturn("completed_count");
        when(value.getName()).thenReturn("办结件数");
        when(value.getFieldType()).thenReturn(PlatformDataType.LONG);
        when(metric.getOwnerName()).thenReturn("业务科室");
    }

    @Test
    void validBindingRejectsForeignFieldsAndNonNumericValues() {
        var definition = definition(List.of());
        assertTrue(service.inspect(metric, definition, service.references(definition), null).canPublish());
        when(value.getModelId()).thenReturn(UUID.randomUUID());
        assertFalse(service.inspect(metric, definition, service.references(definition), null).canPublish());
        when(value.getModelId()).thenReturn(modelId);
        when(value.getFieldType()).thenReturn(PlatformDataType.STRING);
        assertTrue(service.inspect(metric, definition, service.references(definition), null).issues().stream()
                .anyMatch(issue -> issue.code().equals("METRIC_VALUE_TYPE_INVALID")));
    }

    @Test
    void namesDoNotInvalidateButCodeTypeAndPhysicalLocationChangesDo() {
        var definition = definition(List.of());
        var published = service.references(definition);
        when(model.getName()).thenReturn("新的模型名称");
        when(model.getSchemaVersion()).thenReturn(99);
        when(value.getName()).thenReturn("新的字段名称");
        assertTrue(service.inspect(metric, definition, service.references(definition), published).canPublish());
        when(value.getCode()).thenReturn("renamed_count");
        assertEquals("INVALID", service.inspect(metric, definition, service.references(definition), published).bindingStatus());
        when(value.getCode()).thenReturn("completed_count");
        when(value.getFieldType()).thenReturn(PlatformDataType.DOUBLE);
        assertEquals("INVALID", service.inspect(metric, definition, service.references(definition), published).bindingStatus());
        when(value.getFieldType()).thenReturn(PlatformDataType.LONG);
        when(model.getPhysicalTableName()).thenReturn("moved_table");
        assertEquals("INVALID", service.inspect(metric, definition, service.references(definition), published).bindingStatus());
    }

    @Test
    void longTableConditionsValidateDecimalIntegerCapacityAndExactScale() {
        when(value.getFieldType()).thenReturn(PlatformDataType.DECIMAL);
        when(value.getPrecision()).thenReturn(5);
        when(value.getScale()).thenReturn(2);
        for (String number : List.of("0", "999.99", "-999.99", "1.2300")) {
            var d = definition(List.of(new MetricDefinition.FixedFilter(valueId, MetricDefinition.Operator.EQ, number)));
            assertTrue(service.inspect(metric, d, service.references(d), null).canPublish(), number);
        }
        for (String number : List.of("1000", "0.001", "1E3", "invalid")) {
            var d = definition(List.of(new MetricDefinition.FixedFilter(valueId, MetricDefinition.Operator.EQ, number)));
            assertFalse(service.inspect(metric, d, service.references(d), null).canPublish(), number);
        }
    }

    @Test
    void fieldRemovalOnlyBlocksActuallyProtectedReferences() {
        var guard = new MetricReferenceGuard(references, metrics, models);
        UUID unrelated = UUID.randomUUID();
        guard.assertFieldsRemovable(modelId, List.of(unrelated));
        var reference = MetricReference.create(UUID.randomUUID(), "CURRENT", "binding.valueFieldId",
                MetricDefinition.ResourceKind.MODEL_FIELD, valueId, modelId, null, true);
        when(references.findProtected(List.of(valueId))).thenReturn(List.of(reference));
        assertThrows(CodedProblemException.class, () -> guard.assertFieldsRemovable(modelId, List.of(valueId)));
        verifyNoInteractions(metrics);
    }

    private MetricDefinition definition(List<MetricDefinition.FixedFilter> filters) {
        var binding = new MetricDefinition.Binding(modelId, valueId, null, List.of(), List.of(), filters);
        return new MetricDefinition("累计办件", "按业务编号去重计数", "已办结记录", null, null,
                "每行一个地区", "件", MetricDefinition.Period.NONE, null, "无记录为零", "地区可相加",
                null, 0, MetricDefinition.ValueFormat.NUMBER, binding, List.of());
    }
}

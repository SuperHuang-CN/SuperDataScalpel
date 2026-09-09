package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import cn.superhuang.data.scalpel.business.service.domain.SpatialDataServiceDefinition;
import cn.superhuang.data.scalpel.business.service.domain.SpatialStyleMode;
import cn.superhuang.data.scalpel.business.service.domain.SpatialStyleSyncStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpatialStyleV4ResetTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resetsLegacyOnlineDraftOnlyOnceWithoutChangingAppliedVersion() {
        for (String json : new String[]{"{\"schemaVersion\":1}", "{\"schemaVersion\":2}", "{\"schemaVersion\":3}", "invalid json"}) {
            var definition = definition();
            definition.saveStyleDocument(json, true);
            definition.completeStyleSync(definition.getStyleVersion());
            int before = definition.getStyleVersion();
            var appliedAt = definition.getStyleAppliedAt();
            assertThat(SpatialStyleV4ResetConfiguration.requiresReset(definition, mapper)).isTrue();
            definition.resetCartographyDocument(defaultJson());
            assertThat(definition.getStyleVersion()).isEqualTo(before + 1);
            assertThat(definition.getAppliedStyleVersion()).isEqualTo(before);
            assertThat(definition.getStyleAppliedAt()).isEqualTo(appliedAt);
            assertThat(definition.getStyleSyncStatus()).isEqualTo(SpatialStyleSyncStatus.OUT_OF_SYNC);
            assertThat(SpatialStyleV4ResetConfiguration.requiresReset(definition, mapper)).isFalse();
        }
    }

    @Test
    void resetsSimpleDraftWithoutMarkingItApplied() {
        var definition = definition();
        definition.saveSimpleStyle("{}", false);
        int before = definition.getStyleVersion();
        assertThat(SpatialStyleV4ResetConfiguration.requiresReset(definition, mapper)).isTrue();
        definition.resetCartographyDocument(defaultJson());
        assertThat(definition.getSimpleStyleJson()).isNull();
        assertThat(definition.getStyleMode()).isEqualTo(SpatialStyleMode.CARTOGRAPHY);
        assertThat(definition.getStyleVersion()).isEqualTo(before + 1);
        assertThat(definition.getStyleSyncStatus()).isEqualTo(SpatialStyleSyncStatus.NOT_APPLIED);
        assertThat(definition.getAppliedStyleVersion()).isNull();
        assertThat(SpatialStyleV4ResetConfiguration.requiresReset(definition, mapper)).isFalse();
    }

    @Test
    void keepsActiveUploadAndItsSyncStateWhileReplacingOnlineDraft() {
        var definition = definition();
        definition.saveSimpleStyle("{}", false);
        definition.saveUploadedSld("custom.sld", "<原文>中文 &amp; text</原文>", true);
        definition.completeStyleSync(definition.getStyleVersion());
        definition.failStyleSync(definition.getStyleVersion(), "保留错误");
        int before = definition.getStyleVersion();
        var appliedAt = definition.getStyleAppliedAt();
        definition.resetCartographyDocument(defaultJson());
        assertThat(definition.getStyleMode()).isEqualTo(SpatialStyleMode.UPLOADED_SLD);
        assertThat(definition.getUploadedSldText()).isEqualTo("<原文>中文 &amp; text</原文>");
        assertThat(definition.getSldFileName()).isEqualTo("custom.sld");
        assertThat(definition.getStyleVersion()).isEqualTo(before);
        assertThat(definition.getAppliedStyleVersion()).isEqualTo(before);
        assertThat(definition.getStyleAppliedAt()).isEqualTo(appliedAt);
        assertThat(definition.getStyleSyncError()).isEqualTo("保留错误");
        assertThat(definition.getStyleSyncStatus()).isEqualTo(SpatialStyleSyncStatus.SYNC_FAILED);
        assertThat(definition.getSimpleStyleJson()).isNull();
        assertThat(SpatialStyleV4ResetConfiguration.requiresReset(definition, mapper)).isFalse();
    }

    private String defaultJson() {
        return mapper.writeValueAsString(SpatialStyleDocument.defaults(SpatialStyleDocument.GeometryFamily.POLYGON));
    }

    private static SpatialDataServiceDefinition definition() {
        return SpatialDataServiceDefinition.create(UUID.randomUUID(), UUID.randomUUID());
    }
}

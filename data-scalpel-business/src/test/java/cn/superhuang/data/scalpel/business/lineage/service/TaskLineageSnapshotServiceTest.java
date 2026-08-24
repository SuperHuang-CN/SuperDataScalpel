package cn.superhuang.data.scalpel.business.lineage.service;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageOutputFieldEffect;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TaskLineageSnapshotServiceTest {

    @Test
    void completeFlowIgnoresUnknownSourceFieldsFromAnotherPartialFlow() {
        List<TaskLineageSnapshotDraft.FieldDraft> fields = List.of(
                field("complete-output", LineageOutputFieldEffect.CONSTANT),
                field("partial-output", LineageOutputFieldEffect.WRITTEN_UNKNOWN_SOURCE)
        );

        assertThat(TaskLineageSnapshotService.hasUnknownOutputSource(
                Set.of("complete-output"), fields)).isFalse();
        assertThat(TaskLineageSnapshotService.hasUnknownOutputSource(
                Set.of("partial-output"), fields)).isTrue();
    }

    private static TaskLineageSnapshotDraft.FieldDraft field(
            String assetKey,
            LineageOutputFieldEffect effect
    ) {
        return new TaskLineageSnapshotDraft.FieldDraft(
                assetKey, assetKey + "-field", null,
                "field_code", "Field", 0, effect
        );
    }
}

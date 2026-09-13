package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UnionConfigurationContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mergeLayersRulesRoundTripAndMissingFieldKeepsLegacySemantics() {
        UnionConfiguration legacy = mapper.readValue("""
                {
                  "inputTableNames":["base","merge"],
                  "outputTableName":"merged",
                  "mode":"ALL"
                }
                """, UnionConfiguration.class);
        assertNull(legacy.mergingTables());

        UnionConfiguration explicit = new UnionConfiguration(
                List.of("base", "merge"),
                "merged",
                UnionMode.ALL,
                List.of(new UnionMergeTable("merge", List.of(
                        new UnionMergeFieldRule("status", UnionMergeFieldAction.MATCH, "code"),
                        new UnionMergeFieldRule("temporary", UnionMergeFieldAction.REMOVE, null),
                        new UnionMergeFieldRule("amount", UnionMergeFieldAction.RENAME, "merge_amount")
                )))
        );
        assertEquals(
                explicit,
                mapper.readValue(mapper.writeValueAsString(explicit), UnionConfiguration.class)
        );

        UnionConfiguration defaults = new UnionConfiguration(
                List.of("base", "merge"), "merged", UnionMode.ALL, List.of());
        assertEquals(defaults, mapper.readValue(
                mapper.writeValueAsString(defaults), UnionConfiguration.class));
    }
}

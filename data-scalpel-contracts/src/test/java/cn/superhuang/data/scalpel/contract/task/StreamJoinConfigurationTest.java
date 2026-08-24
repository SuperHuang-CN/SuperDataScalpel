package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StreamJoinConfigurationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void roundTripsOutputProjectionAndDefensivelyCopiesArrays() throws Exception {
        List<JoinOutputColumn> outputColumns = new ArrayList<>();
        outputColumns.add(new JoinOutputColumn(
                JoinOutputColumnSource.LEFT, "event_time", "occurred_at", true));
        outputColumns.add(new JoinOutputColumn(
                JoinOutputColumnSource.RIGHT, "id", "dim_customer_id", true));
        StreamJoinConfiguration source = new StreamJoinConfiguration(
                "order_events",
                "dim_customer",
                "enriched_events",
                StreamJoinType.LEFT,
                List.of(new JoinCondition("customer_id", JoinOperator.EQUALS, "id")),
                outputColumns
        );
        outputColumns.clear();

        StreamJoinConfiguration parsed = objectMapper.readValue(
                objectMapper.writeValueAsString(source),
                StreamJoinConfiguration.class
        );

        assertEquals(source, parsed);
        assertEquals(2, parsed.outputColumns().size());
    }

    @Test
    void convenienceConstructorCreatesAnEmptyProjectionDraft() {
        StreamJoinConfiguration configuration = new StreamJoinConfiguration(
                "order_events",
                "dim_customer",
                "enriched_events",
                StreamJoinType.INNER,
                List.of(new JoinCondition("customer_id", JoinOperator.EQUALS, "id"))
        );

        assertEquals(List.of(), configuration.outputColumns());
    }

    @Test
    void rejectsUnknownOutputColumnSide() {
        String json = """
                {
                  "leftTableName": "order_events",
                  "rightTableName": "dim_customer",
                  "outputTableName": "enriched_events",
                  "joinType": "LEFT",
                  "conditions": [],
                  "outputColumns": [{
                    "sourceSide": "MIDDLE",
                    "sourceColumnName": "id",
                    "outputColumnName": "id",
                    "included": true
                  }]
                }
                """;

        assertThrows(Exception.class, () -> objectMapper.readValue(json, StreamJoinConfiguration.class));
    }
}

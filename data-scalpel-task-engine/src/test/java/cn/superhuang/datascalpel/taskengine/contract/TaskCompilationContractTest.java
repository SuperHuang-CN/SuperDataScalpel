package cn.superhuang.datascalpel.taskengine.contract;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JoinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.RenameNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationRequest;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.http.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskCompilationContractTest {
    private final ObjectMapper objectMapper = JsonSupport.strictObjectMapper();

    @Test
    void usesSharedPlatformDataTypeWithoutChangingJsonValues() throws Exception {
        assertEquals(PlatformDataType.STRING, objectMapper.readValue("\"STRING\"", PlatformDataType.class));
        assertEquals("\"STRING\"", objectMapper.writeValueAsString(PlatformDataType.STRING));
        assertEquals(PlatformDataType.class, CanvasColumnSchema.class.getRecordComponents()[1].getType());
    }

    @Test
    void readsAndWritesTheStableDiscriminatedCanvasContract() throws Exception {
        TaskCompilationRequest request = objectMapper.readValue(example(), TaskCompilationRequest.class);

        assertInstanceOf(JdbcInputNodeDefinition.class, request.task().definition().nodes().get(0));
        assertInstanceOf(JoinNodeDefinition.class, request.task().definition().nodes().get(2));
        assertInstanceOf(JdbcOutputNodeDefinition.class, request.task().definition().nodes().get(3));

        String roundTrip = objectMapper.writeValueAsString(request);
        assertTrue(roundTrip.contains("\"type\":\"JDBC_INPUT\""));
        assertTrue(roundTrip.contains("\"type\":\"JOIN\""));
        assertTrue(roundTrip.contains("\"type\":\"JDBC_OUTPUT\""));
    }

    @Test
    void rejectsUnknownNodeTypesAndUnknownFields() throws Exception {
        String valid = example();
        assertThrows(InvalidTypeIdException.class, () -> objectMapper.readValue(
                valid.replaceFirst("JDBC_INPUT", "UNKNOWN_INPUT"), TaskCompilationRequest.class));
        assertThrows(UnrecognizedPropertyException.class, () -> objectMapper.readValue(
                valid.replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"x6Shape\": \"rect\""),
                TaskCompilationRequest.class));
    }

    @Test
    void rejectsDuplicateJsonProperties() throws Exception {
        String duplicateRequestId = example().replaceFirst(
                "\"requestId\": \"9e43bb7f-532d-41f5-9f85-3e06aabb51c8\"",
                "\"requestId\": \"9e43bb7f-532d-41f5-9f85-3e06aabb51c8\", \"requestId\": \"9e43bb7f-532d-41f5-9f85-3e06aabb51c8\""
        );

        assertThrows(JsonProcessingException.class,
                () -> objectMapper.readValue(duplicateRequestId, TaskCompilationRequest.class));
    }

    @Test
    void readsAndWritesModelNodesWithoutLeakingRuntimeFields() throws Exception {
        String modelNodes = """
                {
                  "schemaVersion": 1,
                  "schemaMinorVersion": 1,
                  "nodes": [
                    {
                      "id": "1c436443-4cc0-4fe8-b748-4c02143eb602",
                      "type": "MODEL_INPUT",
                      "name": "订单模型输入",
                      "layout": {"x": 10, "y": 20, "width": 240, "height": 120},
                      "configuration": {"modelId": "45a1f1bd-c381-45eb-a39b-924fc65122ac"}
                    },
                    {
                      "id": "693f6c6b-8978-470f-bab7-cf411eb6e874",
                      "type": "MODEL_OUTPUT",
                      "name": "订单模型输出",
                      "layout": {"x": 320, "y": 20, "width": 240, "height": 120},
                      "configuration": {
                        "sourceTableName": "orders",
                        "targetModelId": "e8b93333-d6ee-4c8b-b5af-df5c90c9620d",
                        "writeMode": "APPEND",
                        "columnMappingMode": "BY_NAME",
                        "columnMappings": []
                      }
                    }
                  ],
                  "edges": [{
                    "id": "e97b9448-401c-40c7-86cf-e683e39fb56b",
                    "sourceNodeId": "1c436443-4cc0-4fe8-b748-4c02143eb602",
                    "targetNodeId": "693f6c6b-8978-470f-bab7-cf411eb6e874"
                  }]
                }
                """;

        CanvasDefinition definition = objectMapper.readValue(modelNodes, CanvasDefinition.class);

        assertEquals(1, definition.schemaVersion());
        assertEquals(1, definition.schemaMinorVersion());
        assertInstanceOf(ModelInputNodeDefinition.class, definition.nodes().get(0));
        assertInstanceOf(ModelOutputNodeDefinition.class, definition.nodes().get(1));
        String roundTrip = objectMapper.writeValueAsString(definition);
        assertTrue(roundTrip.contains("\"type\":\"MODEL_INPUT\""));
        assertTrue(roundTrip.contains("\"type\":\"MODEL_OUTPUT\""));
        assertTrue(!roundTrip.contains("nodeType"));
        assertTrue(!roundTrip.contains("shape"));
        assertTrue(!roundTrip.contains("ports"));
    }

    @Test
    void readsAndWritesRenameAsTheOneDotTwoProcessorContract() throws Exception {
        String json = """
                {
                  "schemaVersion": 1,
                  "schemaMinorVersion": 2,
                  "nodes": [{
                    "id": "1c436443-4cc0-4fe8-b748-4c02143eb602",
                    "type": "RENAME",
                    "name": "订单重命名",
                    "layout": {"x": 10, "y": 20, "width": 240, "height": 120},
                    "configuration": {
                      "sourceTableName": "orders",
                      "outputTableName": "source_orders",
                      "columnMappings": [{
                        "sourceColumnName": "id",
                        "targetColumnName": "order_id"
                      }]
                    }
                  }],
                  "edges": []
                }
                """;

        CanvasDefinition definition = objectMapper.readValue(json, CanvasDefinition.class);

        assertEquals(2, definition.schemaMinorVersion());
        RenameNodeDefinition rename = assertInstanceOf(RenameNodeDefinition.class, definition.nodes().getFirst());
        assertEquals("source_orders", rename.configuration().outputTableName());
        assertEquals("order_id", rename.configuration().columnMappings().getFirst().targetColumnName());
        assertTrue(objectMapper.writeValueAsString(definition).contains("\"type\":\"RENAME\""));
    }

    @Test
    void readsAndWritesFileDatasetInputAsTheOneDotFourContract() throws Exception {
        String json = """
                {
                  "schemaVersion": 1,
                  "schemaMinorVersion": 4,
                  "nodes": [{
                    "id": "1c436443-4cc0-4fe8-b748-4c02143eb602",
                    "type": "FILE_DATASET_INPUT",
                    "name": "订单文件输入",
                    "layout": {"x": 10, "y": 20, "width": 240, "height": 120},
                    "configuration": {
                      "fileDatasetTableId": "45a1f1bd-c381-45eb-a39b-924fc65122ac"
                    }
                  }],
                  "edges": []
                }
                """;

        CanvasDefinition definition = objectMapper.readValue(json, CanvasDefinition.class);

        assertEquals(4, definition.schemaMinorVersion());
        FileDatasetInputNodeDefinition input = assertInstanceOf(
                FileDatasetInputNodeDefinition.class,
                definition.nodes().getFirst()
        );
        assertEquals(
                "45a1f1bd-c381-45eb-a39b-924fc65122ac",
                input.configuration().fileDatasetTableId()
        );
        String roundTrip = objectMapper.writeValueAsString(definition);
        assertTrue(roundTrip.contains("\"type\":\"FILE_DATASET_INPUT\""));
        assertTrue(!roundTrip.contains("objectKey"));
        assertTrue(!roundTrip.contains("parsingOptions"));
    }

    @Test
    void readsAndWritesKafkaInlineValueSchemasAsTheOneDotFiveContract() throws Exception {
        String json = """
                {
                  "schemaVersion": 1,
                  "schemaMinorVersion": 5,
                  "nodes": [
                    {
                      "id": "1c436443-4cc0-4fe8-b748-4c02143eb602",
                      "type": "KAFKA_INPUT",
                      "name": "订单事件输入",
                      "layout": {"x": 10, "y": 20, "width": 240, "height": 120},
                      "configuration": {
                        "dataSourceId": "45a1f1bd-c381-45eb-a39b-924fc65122ac",
                        "topic": "order-events",
                        "valueSchema": {
                          "columns": [{
                            "name": "event_id",
                            "fieldType": "LONG",
                            "length": null,
                            "precision": null,
                            "scale": null,
                            "nullable": false,
                            "comment": "事件 ID"
                          }]
                        },
                        "outputTableName": "order_events",
                        "startingOffsets": "LATEST"
                      }
                    },
                    {
                      "id": "693f6c6b-8978-470f-bab7-cf411eb6e874",
                      "type": "KAFKA_OUTPUT",
                      "name": "订单事件输出",
                      "layout": {"x": 320, "y": 20, "width": 240, "height": 120},
                      "configuration": {
                        "sourceTableName": "order_events",
                        "dataSourceId": "45a1f1bd-c381-45eb-a39b-924fc65122ac",
                        "topic": "order-events-normalized",
                        "valueSchema": {
                          "columns": [{
                            "name": "event_id",
                            "fieldType": "LONG",
                            "length": null,
                            "precision": null,
                            "scale": null,
                            "nullable": false,
                            "comment": null
                          }]
                        },
                        "keyColumnName": "event_id",
                        "columnMappingMode": "BY_NAME",
                        "columnMappings": []
                      }
                    }
                  ],
                  "edges": [{
                    "id": "e97b9448-401c-40c7-86cf-e683e39fb56b",
                    "sourceNodeId": "1c436443-4cc0-4fe8-b748-4c02143eb602",
                    "targetNodeId": "693f6c6b-8978-470f-bab7-cf411eb6e874"
                  }]
                }
                """;

        CanvasDefinition definition = objectMapper.readValue(json, CanvasDefinition.class);

        assertEquals(5, definition.schemaMinorVersion());
        KafkaInputNodeDefinition input = assertInstanceOf(
                KafkaInputNodeDefinition.class,
                definition.nodes().getFirst()
        );
        assertEquals("event_id", input.configuration().valueSchema().columns().getFirst().name());
        KafkaOutputNodeDefinition output = assertInstanceOf(
                KafkaOutputNodeDefinition.class,
                definition.nodes().get(1)
        );
        assertEquals(PlatformDataType.LONG, output.configuration().valueSchema().columns().getFirst().fieldType());
        String roundTrip = objectMapper.writeValueAsString(definition);
        assertTrue(roundTrip.contains("\"valueSchema\""));
        assertTrue(!roundTrip.contains("valueModelId"));
        assertTrue(!roundTrip.contains("modelId"));
    }

    @Test
    void readsAndWritesStrictFileOutputFormatsAsTheOneDotSixContract() throws Exception {
        String json = """
                {
                  "schemaVersion": 1,
                  "schemaMinorVersion": 6,
                  "nodes": [{
                    "id": "1c436443-4cc0-4fe8-b748-4c02143eb602",
                    "type": "FILE_OUTPUT",
                    "name": "订单文件输出",
                    "layout": {"x": 10, "y": 20, "width": 240, "height": 120},
                    "configuration": {
                      "sourceTableName": "orders",
                      "dataSourceId": "45a1f1bd-c381-45eb-a39b-924fc65122ac",
                      "targetPath": "exports/orders",
                      "conflictPolicy": "FAIL_IF_EXISTS",
                      "formatOptions": {
                        "type": "CSV",
                        "header": true,
                        "delimiter": ",",
                        "quote": "\\\"",
                        "escape": "\\\\",
                        "nullValue": ""
                      }
                    }
                  }],
                  "edges": []
                }
                """;

        CanvasDefinition definition = objectMapper.readValue(json, CanvasDefinition.class);

        FileOutputNodeDefinition output = assertInstanceOf(
                FileOutputNodeDefinition.class, definition.nodes().getFirst());
        assertEquals("exports/orders", output.configuration().targetPath());
        assertInstanceOf(FileOutputFormatOptions.Csv.class, output.configuration().formatOptions());
        String roundTrip = objectMapper.writeValueAsString(definition);
        assertTrue(roundTrip.contains("\"type\":\"FILE_OUTPUT\""));
        assertTrue(roundTrip.contains("\"conflictPolicy\":\"FAIL_IF_EXISTS\""));
        assertTrue(roundTrip.contains("\"type\":\"CSV\""));
        assertTrue(!roundTrip.contains("accessKey"));
        assertTrue(!roundTrip.contains("secretKey"));
    }

    @Test
    void readsAndWritesTheStrictModelMetadataSnapshot() throws Exception {
        String json = """
                {
                  "dataSources": [{
                    "id": "11111111-1111-4111-8111-111111111111",
                    "enabled": true,
                    "connectionKind": "JDBC",
                    "purposes": ["SOURCE", "DISTRIBUTION"],
                    "tables": []
                  }],
                  "models": [{
                    "id": "4bbd56c6-5c4f-4af7-8860-5adecc1c29bd",
                    "code": "order_detail",
                    "name": "订单明细",
                    "schemaVersion": 3,
                    "status": "PUBLISHED",
                    "physicalTableMode": "MANAGED",
                    "dataSourceId": "11111111-1111-4111-8111-111111111111",
                    "catalogName": "warehouse",
                    "schemaName": "public",
                    "physicalTableName": "dwd_order_detail",
                    "columns": [{
                      "name": "id", "fieldType": "LONG", "length": null,
                      "precision": null, "scale": null, "nullable": false,
                      "defaultValue": null, "autoIncrement": false, "generated": false,
                      "comment": "主键"
                    }]
                  }]
                }
                """;

        MetadataSnapshot snapshot = objectMapper.readValue(json, MetadataSnapshot.class);

        assertEquals("order_detail", snapshot.models().getFirst().code());
        MetadataSnapshot roundTrip = objectMapper.readValue(
                objectMapper.writeValueAsString(snapshot),
                MetadataSnapshot.class
        );
        assertEquals(snapshot.models(), roundTrip.models());
        assertEquals(snapshot.dataSources().getFirst().id(), roundTrip.dataSources().getFirst().id());
        assertEquals(snapshot.dataSources().getFirst().purposes(), roundTrip.dataSources().getFirst().purposes());
    }

    private static String example() throws Exception {
        return Files.readString(Path.of("examples/valid-canvas-compilation.json"));
    }

}

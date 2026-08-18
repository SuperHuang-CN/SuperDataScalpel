package cn.superhuang.datascalpel.taskengine.contract;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryConstructNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryConstructSource;
import cn.superhuang.data.scalpel.contract.task.GeometryBufferNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryExplodeNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryRepairNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometrySerializeNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryValidateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcQueryInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcSnapshotSyncOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.JoinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelSnapshotSyncOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.RenameNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialMeasureNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialMeasurement;
import cn.superhuang.data.scalpel.contract.task.SpatialClipNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregationKind;
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
import java.util.List;

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
                  "schemaMinorVersion": 28,
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
                        "columnMappings": [{
                          "sourceColumnName": "id",
                          "targetColumnName": "id"
                        }]
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
        assertEquals(28, definition.schemaMinorVersion());
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
    void readsAndWritesSnapshotSyncOutputsAsTheOneDotTwentyEightContract() throws Exception {
        String json = """
                {
                  "schemaVersion": 1,
                  "schemaMinorVersion": 28,
                  "nodes": [
                    {
                      "id": "11111111-1111-4111-8111-111111111111",
                      "type": "JDBC_SNAPSHOT_SYNC_OUTPUT",
                      "name": "JDBC 快照同步",
                      "layout": {"x": 0, "y": 0, "width": 260, "height": 120},
                      "configuration": {
                        "sourceTableName": "reservoir_source",
                        "dataSourceId": "55859069-6387-4390-b850-104845ee5370",
                        "targetTableName": "reservoir",
                        "keyColumns": ["reservoir_code"],
                        "columnMappings": [{
                          "sourceColumnName": "reservoir_code",
                          "targetColumnName": "reservoir_code"
                        }],
                        "deletePolicy": {
                          "action": "DELETE",
                          "maxDeleteRows": 1000,
                          "maxDeleteRatio": 0.2
                        }
                      }
                    },
                    {
                      "id": "22222222-2222-4222-8222-222222222222",
                      "type": "MODEL_SNAPSHOT_SYNC_OUTPUT",
                      "name": "模型快照同步",
                      "layout": {"x": 320, "y": 0, "width": 260, "height": 120},
                      "configuration": {
                        "sourceTableName": "district_source",
                        "targetModelId": "805c80b3-959e-4690-90d3-5c2d613864c1",
                        "keyColumns": ["district_code"],
                        "columnMappings": [{
                          "sourceColumnName": "code",
                          "targetColumnName": "district_code"
                        }],
                        "deletePolicy": {
                          "action": "KEEP",
                          "maxDeleteRows": null,
                          "maxDeleteRatio": null
                        }
                      }
                    }
                  ],
                  "edges": []
                }
                """;

        CanvasDefinition definition = objectMapper.readValue(json, CanvasDefinition.class);

        JdbcSnapshotSyncOutputNodeDefinition jdbc = assertInstanceOf(
                JdbcSnapshotSyncOutputNodeDefinition.class, definition.nodes().get(0));
        ModelSnapshotSyncOutputNodeDefinition model = assertInstanceOf(
                ModelSnapshotSyncOutputNodeDefinition.class, definition.nodes().get(1));
        assertEquals(0.2D, jdbc.configuration().deletePolicy().maxDeleteRatio());
        assertEquals(List.of("district_code"), model.configuration().keyColumns());
        String roundTrip = objectMapper.writeValueAsString(definition);
        assertTrue(roundTrip.contains("\"type\":\"JDBC_SNAPSHOT_SYNC_OUTPUT\""));
        assertTrue(roundTrip.contains("\"type\":\"MODEL_SNAPSHOT_SYNC_OUTPUT\""));
        assertTrue(roundTrip.contains("\"action\":\"DELETE\""));
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
    void readsAndWritesSpatialFoundationNodesAsTheOneDotTwentyOneContract() throws Exception {
        String json = """
                {
                  "schemaVersion": 1,
                  "schemaMinorVersion": 21,
                  "nodes": [
                    {
                      "id": "11111111-1111-4111-8111-111111111111",
                      "type": "GEOMETRY_CONSTRUCT",
                      "name": "构造",
                      "layout": {"x": 0, "y": 0, "width": 250, "height": 120},
                      "configuration": {
                        "sourceTableName": "raw",
                        "outputTableName": "geometry_table",
                        "outputColumnName": "shape",
                        "source": {"kind": "WKT", "columnName": "wkt"},
                        "targetGeometry": {
                          "kind": "POLYGON",
                          "crs": {"authority": "EPSG", "code": 4326},
                          "dimension": "XY"
                        }
                      }
                    },
                    {
                      "id": "22222222-2222-4222-8222-222222222222",
                      "type": "GEOMETRY_VALIDATE",
                      "name": "校验",
                      "layout": {"x": 300, "y": 0, "width": 250, "height": 120},
                      "configuration": {
                        "sourceTableName": "geometry_table",
                        "outputTableName": "validated",
                        "geometryColumnName": "shape",
                        "validColumnName": "is_valid",
                        "reasonColumnName": null
                      }
                    },
                    {
                      "id": "33333333-3333-4333-8333-333333333333",
                      "type": "SPATIAL_MEASURE",
                      "name": "测量",
                      "layout": {"x": 600, "y": 0, "width": 250, "height": 120},
                      "configuration": {
                        "sourceTableName": "validated",
                        "outputTableName": "measured",
                        "measurements": [
                          {
                            "kind": "AREA",
                            "geometryColumnName": "shape",
                            "mode": "PLANAR",
                            "outputColumnName": "area"
                          },
                          {
                            "kind": "DISTANCE",
                            "leftGeometryColumnName": "shape",
                            "rightGeometryColumnName": "shape",
                            "mode": "SPHEROID",
                            "outputColumnName": "distance"
                          }
                        ]
                      }
                    },
                    {
                      "id": "44444444-4444-4444-8444-444444444444",
                      "type": "GEOMETRY_SERIALIZE",
                      "name": "序列化",
                      "layout": {"x": 900, "y": 0, "width": 250, "height": 120},
                      "configuration": {
                        "sourceTableName": "measured",
                        "outputTableName": "serialized",
                        "geometryColumnName": "shape",
                        "outputColumnName": "geojson",
                        "format": "GEOJSON"
                      }
                    }
                  ],
                  "edges": []
                }
                """;

        CanvasDefinition definition = objectMapper.readValue(json, CanvasDefinition.class);

        GeometryConstructNodeDefinition construct = assertInstanceOf(
                GeometryConstructNodeDefinition.class,
                definition.nodes().get(0)
        );
        assertInstanceOf(GeometryConstructSource.Wkt.class, construct.configuration().source());
        assertInstanceOf(GeometryValidateNodeDefinition.class, definition.nodes().get(1));
        SpatialMeasureNodeDefinition measure = assertInstanceOf(
                SpatialMeasureNodeDefinition.class,
                definition.nodes().get(2)
        );
        assertInstanceOf(SpatialMeasurement.Area.class, measure.configuration().measurements().get(0));
        assertInstanceOf(SpatialMeasurement.Distance.class, measure.configuration().measurements().get(1));
        assertInstanceOf(GeometrySerializeNodeDefinition.class, definition.nodes().get(3));
        String roundTrip = objectMapper.writeValueAsString(definition);
        assertTrue(roundTrip.contains("\"kind\":\"WKT\""));
        assertTrue(roundTrip.contains("\"kind\":\"DISTANCE\""));
        assertTrue(roundTrip.contains("\"type\":\"GEOMETRY_SERIALIZE\""));
    }

    @Test
    void readsAndWritesSpatialEnrichmentNodesAsTheOneDotTwentyTwoContract() throws Exception {
        String json = """
                {
                  "schemaVersion": 1,
                  "schemaMinorVersion": 22,
                  "nodes": [
                    {
                      "id": "11111111-1111-4111-8111-111111111111",
                      "type": "GEOMETRY_REPAIR",
                      "name": "修复",
                      "layout": {"x": 0, "y": 0, "width": 250, "height": 120},
                      "configuration": {
                        "sourceTableName": "geometry_table",
                        "outputTableName": "repaired",
                        "geometryColumnName": "shape",
                        "outputColumnName": "repaired_shape"
                      }
                    },
                    {
                      "id": "22222222-2222-4222-8222-222222222222",
                      "type": "GEOMETRY_BUFFER",
                      "name": "Buffer",
                      "layout": {"x": 300, "y": 0, "width": 250, "height": 120},
                      "configuration": {
                        "sourceTableName": "repaired",
                        "outputTableName": "buffered",
                        "geometryColumnName": "repaired_shape",
                        "outputColumnName": "buffer_shape",
                        "distance": 1000.0,
                        "mode": "SPHEROID"
                      }
                    },
                    {
                      "id": "33333333-3333-4333-8333-333333333333",
                      "type": "GEOMETRY_EXPLODE",
                      "name": "拆分",
                      "layout": {"x": 600, "y": 0, "width": 250, "height": 120},
                      "configuration": {
                        "sourceTableName": "buffered",
                        "outputTableName": "parts",
                        "geometryColumnName": "buffer_shape",
                        "outputColumnName": "part",
                        "partIndexColumnName": "part_index"
                      }
                    }
                  ],
                  "edges": []
                }
                """;

        CanvasDefinition definition = objectMapper.readValue(json, CanvasDefinition.class);

        assertEquals(22, definition.schemaMinorVersion());
        assertInstanceOf(GeometryRepairNodeDefinition.class, definition.nodes().get(0));
        assertInstanceOf(GeometryBufferNodeDefinition.class, definition.nodes().get(1));
        assertInstanceOf(GeometryExplodeNodeDefinition.class, definition.nodes().get(2));
        String roundTrip = objectMapper.writeValueAsString(definition);
        assertTrue(roundTrip.contains("\"type\":\"GEOMETRY_REPAIR\""));
        assertTrue(roundTrip.contains("\"mode\":\"SPHEROID\""));
        assertTrue(roundTrip.contains("\"partIndexColumnName\":\"part_index\""));
    }

    @Test
    void readsAndWritesSpatialAnalysisNodesAsTheOneDotTwentyThreeContract() throws Exception {
        String json = """
                {
                  "schemaVersion": 1,
                  "schemaMinorVersion": 23,
                  "nodes": [
                    {
                      "id": "11111111-1111-4111-8111-111111111111",
                      "type": "SPATIAL_CLIP",
                      "name": "空间裁剪",
                      "layout": {"x": 0, "y": 0, "width": 260, "height": 120},
                      "configuration": {
                        "sourceTableName": "roads",
                        "maskTableName": "districts",
                        "outputTableName": "district_roads",
                        "sourceGeometryColumnName": "centerline",
                        "maskGeometryColumnName": "boundary",
                        "outputColumnName": "clipped_centerline"
                      }
                    },
                    {
                      "id": "22222222-2222-4222-8222-222222222222",
                      "type": "SPATIAL_AGGREGATE",
                      "name": "空间聚合",
                      "layout": {"x": 320, "y": 0, "width": 250, "height": 120},
                      "configuration": {
                        "sourceTableName": "parcels",
                        "outputTableName": "district_geometry",
                        "groupByColumns": ["district_code"],
                        "aggregations": [
                          {
                            "kind": "UNION",
                            "geometryColumnName": "boundary",
                            "outputColumnName": "district_boundary"
                          },
                          {
                            "kind": "ENVELOPE",
                            "geometryColumnName": "boundary",
                            "outputColumnName": "district_envelope"
                          }
                        ]
                      }
                    }
                  ],
                  "edges": []
                }
                """;

        CanvasDefinition definition = objectMapper.readValue(json, CanvasDefinition.class);

        assertEquals(23, definition.schemaMinorVersion());
        assertInstanceOf(SpatialClipNodeDefinition.class, definition.nodes().get(0));
        SpatialAggregateNodeDefinition aggregate = assertInstanceOf(
                SpatialAggregateNodeDefinition.class,
                definition.nodes().get(1)
        );
        assertEquals(
                List.of(SpatialAggregationKind.UNION, SpatialAggregationKind.ENVELOPE),
                aggregate.configuration().aggregations().stream()
                        .map(item -> item.kind())
                        .toList()
        );
        String roundTrip = objectMapper.writeValueAsString(definition);
        assertTrue(roundTrip.contains("\"type\":\"SPATIAL_CLIP\""));
        assertTrue(roundTrip.contains("\"type\":\"SPATIAL_AGGREGATE\""));
        assertTrue(roundTrip.contains("\"kind\":\"ENVELOPE\""));
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
    void readsAndWritesKafkaInlineValueSchemasAsTheOneDotTwentyEightContract() throws Exception {
        String json = """
                {
                  "schemaVersion": 1,
                  "schemaMinorVersion": 28,
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
                        "columnMappings": [{
                          "sourceColumnName": "event_id",
                          "targetColumnName": "event_id"
                        }]
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

        assertEquals(28, definition.schemaMinorVersion());
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
    void readsAndWritesShapefileOutputAsTheOneDotTwentyFourContract() throws Exception {
        String json = """
                {
                  "schemaVersion": 1,
                  "schemaMinorVersion": 24,
                  "nodes": [{
                    "id": "1c436443-4cc0-4fe8-b748-4c02143eb602",
                    "type": "FILE_OUTPUT",
                    "name": "行政区 Shapefile 输出",
                    "layout": {"x": 10, "y": 20, "width": 240, "height": 120},
                    "configuration": {
                      "sourceTableName": "districts",
                      "dataSourceId": "45a1f1bd-c381-45eb-a39b-924fc65122ac",
                      "targetPath": "exports/districts",
                      "conflictPolicy": "FAIL_IF_EXISTS",
                      "formatOptions": {
                        "type": "SHAPEFILE",
                        "baseName": "districts",
                        "packageMode": "ZIP",
                        "geometryColumnName": "geom",
                        "targetShapeType": "POLYGON",
                        "attributeMappings": [{
                          "sourceColumnName": "district_name",
                          "targetFieldName": "DIST_NAME",
                          "targetStringByteLength": 160
                        }]
                      }
                    }
                  }],
                  "edges": []
                }
                """;

        CanvasDefinition definition = objectMapper.readValue(json, CanvasDefinition.class);
        FileOutputNodeDefinition output = assertInstanceOf(
                FileOutputNodeDefinition.class, definition.nodes().getFirst());
        FileOutputFormatOptions.Shapefile shapefile = assertInstanceOf(
                FileOutputFormatOptions.Shapefile.class,
                output.configuration().formatOptions());

        assertEquals("districts", shapefile.baseName());
        assertEquals("ZIP", shapefile.packageMode().name());
        assertEquals("POLYGON", shapefile.targetShapeType().name());
        assertEquals("DIST_NAME", shapefile.attributeMappings().getFirst().targetFieldName());
        assertEquals(160, shapefile.attributeMappings().getFirst().targetStringByteLength());

        String roundTrip = objectMapper.writeValueAsString(definition);
        assertTrue(roundTrip.contains("\"type\":\"SHAPEFILE\""));
        assertTrue(roundTrip.contains("\"targetShapeType\":\"POLYGON\""));
        assertTrue(roundTrip.contains("\"targetStringByteLength\":160"));
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

    @Test
    void readsAndWritesJdbcQueryInputAndJdbcOutputUpsertAsCanvasOneDotTwentyEight() throws Exception {
        String json = """
                {
                  "schemaVersion": 1,
                  "schemaMinorVersion": 28,
                  "nodes": [
                    {
                      "id": "11111111-1111-4111-8111-111111111111",
                      "type": "JDBC_QUERY_INPUT",
                      "name": "订单查询输入",
                      "layout": {"x": 0, "y": 0, "width": 240, "height": 120},
                      "configuration": {
                        "dataSourceId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                        "sql": "SELECT order_id FROM orders",
                        "outputTableName": "query_orders",
                        "analyzedSqlSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "outputColumns": [{
                          "name": "order_id", "fieldType": "LONG", "length": null,
                          "precision": null, "scale": null, "nullable": false,
                          "defaultValue": null, "autoIncrement": false, "generated": false,
                          "comment": "订单 ID", "geometry": null
                        }]
                      }
                    },
                    {
                      "id": "22222222-2222-4222-8222-222222222222",
                      "type": "JDBC_OUTPUT",
                      "name": "订单 UPSERT",
                      "layout": {"x": 320, "y": 0, "width": 240, "height": 120},
                      "configuration": {
                        "sourceTableName": "query_orders",
                        "dataSourceId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                        "targetTableName": "orders",
                        "writeMode": "UPSERT",
                        "columnMappings": [{
                          "sourceColumnName": "order_id",
                          "targetColumnName": "order_id"
                        }],
                        "upsertKeyColumns": ["order_id"]
                      }
                    }
                  ],
                  "edges": []
                }
                """;

        CanvasDefinition definition = objectMapper.readValue(json, CanvasDefinition.class);

        JdbcQueryInputNodeDefinition query = assertInstanceOf(
                JdbcQueryInputNodeDefinition.class, definition.nodes().getFirst());
        JdbcOutputNodeDefinition output = assertInstanceOf(
                JdbcOutputNodeDefinition.class, definition.nodes().getLast());
        assertEquals("query_orders", query.configuration().outputTableName());
        assertEquals(1, query.configuration().outputColumns().size());
        assertEquals(JdbcWriteMode.UPSERT, output.configuration().writeMode());
        assertEquals(List.of("order_id"), output.configuration().upsertKeyColumns());

        CanvasDefinition roundTrip = objectMapper.readValue(
                objectMapper.writeValueAsString(definition), CanvasDefinition.class);
        assertEquals(definition, roundTrip);
    }

    private static String example() throws Exception {
        return Files.readString(Path.of("examples/valid-canvas-compilation.json"));
    }

}

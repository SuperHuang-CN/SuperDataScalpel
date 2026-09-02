package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanvasDefinitionValidatorTest {
    private final CanvasDefinitionUpgrader upgrader = new CanvasDefinitionUpgrader();
    private final CanvasDefinitionValidator validator = new CanvasDefinitionValidator(upgrader);

    @Test
    void acceptsCompleteAndIncompleteModelIdentifiersButRejectsMalformedIdentifiers() {
        String inputId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        CanvasDefinition valid = definition(inputId, outputId, UUID.randomUUID().toString(), "");
        CanvasDefinition incomplete = definition(inputId, outputId, "", "");
        CanvasDefinition malformedInput = definition(inputId, outputId, "not-a-uuid", "");
        CanvasDefinition malformedOutput = definition(inputId, outputId, "", "not-a-uuid");

        assertDoesNotThrow(() -> validator.validate(valid));
        assertDoesNotThrow(() -> validator.validate(incomplete));
        assertThrows(ResponseStatusException.class, () -> validator.validate(malformedInput));
        assertThrows(ResponseStatusException.class, () -> validator.validate(malformedOutput));
    }

    @Test
    void acceptsCurrentMajorWithMissingMinorAndNormalizesItToTheCurrentWriterVersion() {
        CanvasDefinition source = new ObjectMapper().readValue(
                "{\"schemaVersion\":" + CanvasDefinition.CURRENT_SCHEMA_VERSION
                        + ",\"nodes\":[],\"edges\":[]}",
                CanvasDefinition.class
        );

        assertDoesNotThrow(() -> validator.validate(source));
        CanvasDefinition upgraded = upgrader.upgradeToCurrent(source);

        assertEquals(0, source.effectiveSchemaMinorVersion());
        assertEquals(CanvasDefinition.CURRENT_SCHEMA_VERSION, upgraded.schemaVersion());
        assertEquals(CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION, upgraded.schemaMinorVersion());
    }

    @Test
    void acceptsAnIncompleteSqlTransformDraftOnlyFromCanvasFourDotOne() {
        SqlTransformNodeDefinition sqlTransform = new SqlTransformNodeDefinition(
                UUID.randomUUID().toString(),
                "SQL 处理",
                layout(),
                new SqlTransformConfiguration(null, null)
        );
        CanvasDefinition current = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(sqlTransform),
                List.of()
        );
        CanvasDefinition legacy = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.LEGACY_SCHEMA_MINOR_VERSION,
                List.of(sqlTransform),
                List.of()
        );

        assertEquals("", sqlTransform.configuration().outputTableName());
        assertEquals("", sqlTransform.configuration().sql());
        assertDoesNotThrow(() -> validator.validate(current));
        assertThrows(ResponseStatusException.class, () -> validator.validate(legacy));
        assertThrows(ResponseStatusException.class, () -> upgrader.upgradeToCurrent(legacy));
    }

    @Test
    void acceptsCurrentCanvasVersionAndRejectsOtherMajorsAndFutureMinors() {
        CanvasDefinition current = definition(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", "");
        CanvasDefinition previousMajor = new CanvasDefinition(1, 28, current.nodes(), current.edges());
        CanvasDefinition futureMinor = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION + 1,
                List.of(),
                List.of());
        CanvasDefinition futureMajor = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION + 1, 0, List.of(), List.of());

        assertDoesNotThrow(() -> validator.validate(current));
        assertThrows(ResponseStatusException.class, () -> validator.validate(previousMajor));
        assertThrows(ResponseStatusException.class, () -> validator.validate(futureMinor));
        assertThrows(ResponseStatusException.class, () -> validator.validate(futureMajor));
    }

    @Test
    void acceptsEpochTimestampUnitsOnlyFromCanvasFourDotTwoAndOnlyForTimestampTargets() {
        TypeCastNodeDefinition validNode = epochTimestampTypeCast(PlatformDataType.TIMESTAMP);
        CanvasDefinition current = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(validNode),
                List.of()
        );
        CanvasDefinition fourDotOne = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                1,
                List.of(validNode),
                List.of()
        );
        TypeCastNodeDefinition invalidTarget = epochTimestampTypeCast(PlatformDataType.STRING);

        assertDoesNotThrow(() -> validator.validate(current));
        assertThrows(ResponseStatusException.class, () -> validator.validate(fourDotOne));
        assertThrows(ResponseStatusException.class, () -> upgrader.upgradeToCurrent(fourDotOne));
        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(invalidTarget),
                List.of()
        )));
    }

    @Test
    void acceptsStringTemporalParsingOnlyFromCanvasFourDotThree() {
        TypeCastNodeDefinition validNode = new TypeCastNodeDefinition(
                UUID.randomUUID().toString(),
                "字符串时间转换",
                layout(),
                new TypeCastConfiguration(List.of(new TypeCastOperation(
                        UUID.randomUUID().toString(),
                        "events",
                        new ProcessorOutput.CreateNewTable("typed_events"),
                        List.of(new ColumnTypeCast(
                                "created_at_text",
                                new PlatformTypeDefinition(PlatformDataType.TIMESTAMP, null, null, null, null),
                                CastFailureStrategy.SET_NULL,
                                null,
                                new StringTemporalParseOptions(
                                        "yyyy-MM-dd HH:mm:ss",
                                        StringTimestampZoneMode.SOURCE_TIME_ZONE,
                                        "Asia/Shanghai"
                                )
                        ))
                )))
        );
        CanvasDefinition current = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(validNode),
                List.of()
        );
        CanvasDefinition fourDotTwo = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                2,
                List.of(validNode),
                List.of()
        );

        assertDoesNotThrow(() -> validator.validate(current));
        assertThrows(ResponseStatusException.class, () -> validator.validate(fourDotTwo));
        assertThrows(ResponseStatusException.class, () -> upgrader.upgradeToCurrent(fourDotTwo));
    }

    @Test
    void rejectsNonzeroMinorAndAcceptsModelOutputUpsertInThreeDotZero() {
        ModelOutputNodeDefinition output = new ModelOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "模型 UPSERT",
                layout(),
                new ModelOutputConfiguration(
                        "orders",
                        UUID.randomUUID().toString(),
                        JdbcWriteMode.UPSERT,
                        List.of(new JdbcColumnMapping("id", "id"))
                )
        );
        CanvasDefinition previous = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION, 2, List.of(output), List.of());
        CanvasDefinition current = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(output),
                List.of());

        assertThrows(ResponseStatusException.class, () -> upgrader.upgradeToCurrent(previous));
        assertDoesNotThrow(() -> validator.validate(current));
    }

    @Test
    void acceptsRenameInTwoDotZeroAndRejectsThePreviousMajor() {
        CanvasDefinition current = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(new RenameNodeDefinition(
                        UUID.randomUUID().toString(),
                        "重命名",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new RenameConfiguration(
                                "orders",
                                "source_orders",
                                List.of(new RenameColumnMapping("id", "order_id"))
                        )
                )),
                List.of()
        );
        CanvasDefinition previous = new CanvasDefinition(1, 28, current.nodes(), current.edges());

        assertDoesNotThrow(() -> validator.validate(current));
        assertThrows(ResponseStatusException.class, () -> validator.validate(previous));
    }

    @Test
    void acceptsKafkaInlineSchemaInTwoDotZeroWithoutPersistingAModelReference() {
        CanvasDefinition current = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(new KafkaInputNodeDefinition(
                        UUID.randomUUID().toString(),
                        "事件输入",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new KafkaInputConfiguration(
                                UUID.randomUUID().toString(),
                                "order-events",
                                new KafkaValueSchema(List.of(
                                        new KafkaValueColumn(
                                                "event_id",
                                                PlatformDataType.LONG,
                                                null,
                                                null,
                                                null,
                                                false,
                                                "事件 ID"
                                        )
                                )),
                                "order_events",
                                KafkaStartingOffsets.LATEST
                        )
                )),
                List.of()
        );

        assertDoesNotThrow(() -> validator.validate(current));
        assertThrows(
                ResponseStatusException.class,
                () -> validator.validate(new CanvasDefinition(1, 28, current.nodes(), current.edges()))
        );
        String serialized = new ObjectMapper().writeValueAsString(current);
        assertFalse(serialized.contains("valueModelId"));
        assertFalse(serialized.contains("modelId"));
    }

    @Test
    void acceptsKafkaInputFormatsOnlyFromFourDotFourAndNormalizesLegacyDefaults() {
        KafkaInputNodeDefinition textInput = new KafkaInputNodeDefinition(
                UUID.randomUUID().toString(),
                "文本事件输入",
                layout(),
                new KafkaInputConfiguration(
                        UUID.randomUUID().toString(),
                        "text-events",
                        new KafkaValueSchema(List.of()),
                        "text_events",
                        KafkaStartingOffsets.LATEST,
                        10,
                        KafkaInputValueFormat.TEXT,
                        List.of(KafkaInputMetadataField.KEY, KafkaInputMetadataField.OFFSET)
                )
        );
        CanvasDefinition current = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(textInput),
                List.of()
        );
        CanvasDefinition previous = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                3,
                List.of(textInput),
                List.of()
        );

        assertDoesNotThrow(() -> validator.validate(current));
        assertThrows(ResponseStatusException.class, () -> validator.validate(previous));

        KafkaInputNodeDefinition legacyInput = new KafkaInputNodeDefinition(
                textInput.id(),
                "JSON 事件输入",
                layout(),
                new KafkaInputConfiguration(
                        UUID.randomUUID().toString(),
                        "json-events",
                        new KafkaValueSchema(List.of(new KafkaValueColumn(
                                "event_id", PlatformDataType.LONG,
                                null, null, null, false, null
                        ))),
                        "json_events",
                        KafkaStartingOffsets.LATEST,
                        10
                )
        );
        CanvasDefinition upgraded = upgrader.upgradeToCurrent(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                3,
                List.of(legacyInput),
                List.of()
        ));
        KafkaInputConfiguration normalized = ((KafkaInputNodeDefinition) upgraded.nodes().getFirst())
                .configuration();
        assertEquals(KafkaInputValueFormat.JSON, normalized.valueFormat());
        assertEquals(List.of(), normalized.metadataFields());
    }

    @Test
    void rejectsGeometryInKafkaInlineSchema() {
        CanvasDefinition definition = new CanvasDefinition(
                1,
                5,
                List.of(new KafkaInputNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间事件输入",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new KafkaInputConfiguration(
                                UUID.randomUUID().toString(),
                                "spatial-events",
                                new KafkaValueSchema(List.of(
                                        new KafkaValueColumn(
                                                "shape",
                                                PlatformDataType.GEOMETRY,
                                                null,
                                                null,
                                                null,
                                                true,
                                                null
                                        )
                                )),
                                "spatial_events",
                                KafkaStartingOffsets.LATEST
                        )
                )),
                List.of()
        );

        assertThrows(ResponseStatusException.class, () -> validator.validate(definition));
    }

    @Test
    void enforcesIntroducedMinorVersionForAdvancedProcessors() {
        assertIntroducedAt(
                new NullHandlingNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空值处理",
                        layout(),
                        new NullHandlingConfiguration(
                                "orders",
                                "orders_cleaned",
                                List.of(new DropNullRowsRule(
                                        List.of("order_id"),
                                        NullMatchMode.ANY_NULL
                                ))
                        )
                ),
                14
        );
        assertIntroducedAt(
                new ValueMappingNodeDefinition(
                        UUID.randomUUID().toString(),
                        "值映射",
                        layout(),
                        new ValueMappingConfiguration(
                                "orders",
                                "orders_standardized",
                                List.of(new ValueMappingRule(
                                        "status",
                                        List.of(new ValueMappingEntry(
                                                new CanvasLiteral(PlatformDataType.STRING, "P"),
                                                new CanvasLiteral(PlatformDataType.STRING, "PAID")
                                        )),
                                        ValueMappingUnmatchedStrategy.KEEP,
                                        null
                                ))
                        )
                ),
                15
        );
        assertIntroducedAt(
                new WindowNodeDefinition(
                        UUID.randomUUID().toString(),
                        "窗口计算",
                        layout(),
                        new WindowConfiguration(
                                "orders",
                                "orders_windowed",
                                List.of(),
                                List.of(new SortField(
                                        "created_at",
                                        SortDirection.ASC,
                                        NullOrdering.LAST
                                )),
                                List.of(new WindowFunctionItem.RowNumber("row_number"))
                        )
                ),
                16
        );
        assertIntroducedAt(
                new TopNNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Top N",
                        layout(),
                        new TopNConfiguration(
                                "orders",
                                "top_orders",
                                List.of(),
                                List.of(new SortField(
                                        "amount",
                                        SortDirection.DESC,
                                        NullOrdering.LAST
                                )),
                                10,
                                TopNTieStrategy.EXACT
                        )
                ),
                17
        );
        assertIntroducedAt(
                new JsonExtractNodeDefinition(
                        UUID.randomUUID().toString(),
                        "JSON 提取",
                        layout(),
                        new JsonExtractConfiguration(
                                "orders",
                                "orders_enriched",
                                "payload",
                                List.of(new JsonExtraction(
                                        "$.customer.id",
                                        "customer_id",
                                        PlatformTypeDefinition.of(PlatformDataType.STRING)
                                )),
                                JsonExtractFailureStrategy.ERROR
                        )
                ),
                19
        );
        assertIntroducedAt(
                new SpatialTransformNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间转换",
                        layout(),
                        new SpatialTransformConfiguration(
                                "orders",
                                "orders_3857",
                                "location",
                                new CrsReference("EPSG", 3857)
                        )
                ),
                20
        );
        assertIntroducedAt(
                new SpatialJoinNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间连接",
                        layout(),
                        new SpatialJoinConfiguration(
                                "orders",
                                "regions",
                                "orders_with_region",
                                JoinType.INNER,
                                List.of(new SpatialJoinCondition(
                                        "location",
                                        SpatialPredicate.WITHIN,
                                        "boundary"
                                ))
                        )
                ),
                20
        );
        GeometryTypeDefinition point4326 = new GeometryTypeDefinition(
                GeometryKind.POINT,
                new CrsReference("EPSG", 4326),
                CoordinateDimension.XY
        );
        assertIntroducedAt(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        layout(),
                        new GeometryConstructConfiguration(
                                "raw",
                                "geometry_table",
                                "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                point4326
                        )
                ),
                21
        );
        assertIntroducedAt(
                new GeometryValidateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 校验",
                        layout(),
                        new GeometryValidateConfiguration(
                                "geometry_table",
                                "validated",
                                "shape",
                                "is_valid",
                                null
                        )
                ),
                21
        );
        assertIntroducedAt(
                new SpatialMeasureNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间测量",
                        layout(),
                        new SpatialMeasureConfiguration(
                                "geometry_table",
                                "measured",
                                List.of(new SpatialMeasurement.X("shape", "x"))
                        )
                ),
                21
        );
        assertIntroducedAt(
                new GeometrySerializeNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 序列化",
                        layout(),
                        new GeometrySerializeConfiguration(
                                "geometry_table",
                                "serialized",
                                "shape",
                                "wkt",
                                GeometrySerializationFormat.WKT
                        )
                ),
                21
        );
        assertIntroducedAt(
                new GeometryRepairNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 修复",
                        layout(),
                        new GeometryRepairConfiguration(
                                "geometry_table", "repaired", "shape", "repaired_shape")
                ),
                22
        );
        assertIntroducedAt(
                new GeometryBufferNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry Buffer",
                        layout(),
                        new GeometryBufferConfiguration(
                                "geometry_table", "buffered", "shape", "buffer_shape",
                                100d, SpatialMeasureMode.PLANAR)
                ),
                22
        );
        assertIntroducedAt(
                new GeometryExplodeNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 拆分",
                        layout(),
                        new GeometryExplodeConfiguration(
                                "geometry_table", "parts", "shape", "part", "part_index")
                ),
                22
        );
        assertIntroducedAt(
                new SpatialClipNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间裁剪",
                        layout(),
                        new SpatialClipConfiguration(
                                "roads", "districts", "district_roads",
                                "centerline", "boundary", "clipped_centerline")
                ),
                23
        );
        assertIntroducedAt(
                new SpatialAggregateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间聚合",
                        layout(),
                        new SpatialAggregateConfiguration(
                                "parcels",
                                "district_geometry",
                                List.of("district_code"),
                                List.of(new SpatialAggregation(
                                        SpatialAggregationKind.UNION,
                                        "boundary",
                                        "district_boundary"
                                ))
                        )
                ),
                23
        );
    }

    @Test
    void acceptsShapefileOutputInTheCurrentCanvasVersionAndRejectsThePreviousMajor() {
        FileOutputNodeDefinition shapefileOutput = new FileOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "行政区 Shapefile 输出",
                layout(),
                new FileOutputConfiguration(
                        "districts",
                        UUID.randomUUID().toString(),
                        "exports/districts",
                        FileOutputConflictPolicy.FAIL_IF_EXISTS,
                        new FileOutputFormatOptions.Shapefile(
                                "districts",
                                ShapefilePackageMode.ZIP,
                                "geom",
                                ShapefileShapeType.POLYGON,
                                List.of(new ShapefileAttributeMapping(
                                        "district_code", "DIST_CODE", 64
                                ))
                        )
                )
        );

        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(shapefileOutput), List.of())));
        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                1, 24, List.of(shapefileOutput), List.of())));
    }

    @Test
    void acceptsGeoParquetAndGeoJsonOutputInTheCurrentCanvasVersion() {
        FileOutputNodeDefinition geoParquet = fileOutput(new FileOutputFormatOptions.GeoParquet(
                "geom", GeoParquetCompressionCodec.SNAPPY, GeoParquetCoveringMode.ROW_BBOX));
        FileOutputNodeDefinition geoJson = fileOutput(new FileOutputFormatOptions.GeoJson(
                "districts", "geom", "district_id", false));

        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(geoParquet, geoJson), List.of())));
        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                1, 25, List.of(geoParquet), List.of())));
        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                1, 25, List.of(geoJson), List.of())));
    }

    @Test
    void requiresTheCurrentCanvasMajorForSnapshotSyncOutputMappingProtocol() {
        SnapshotDeletePolicy keep = new SnapshotDeletePolicy(
                SnapshotTargetOnlyAction.KEEP, null, null);
        JdbcSnapshotSyncOutputNodeDefinition jdbc = new JdbcSnapshotSyncOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "JDBC 快照同步",
                layout(),
                new JdbcSnapshotSyncOutputConfiguration(
                        "source", "", "target", List.of("id"),
                        List.of(new JdbcColumnMapping("id", "id")), keep)
        );
        ModelSnapshotSyncOutputNodeDefinition model = new ModelSnapshotSyncOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "模型快照同步",
                layout(),
                new ModelSnapshotSyncOutputConfiguration(
                        "source", "", List.of("id"),
                        List.of(new JdbcColumnMapping("id", "id")), keep)
        );

        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                1, 27, List.of(jdbc), List.of())));
        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                1, 27, List.of(model), List.of())));
        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(jdbc, model), List.of())));
    }

    @Test
    void acceptsJdbcQueryInputAndExplicitOutputMappingInCanvasTwo() {
        CanvasColumnSchema orderId = new CanvasColumnSchema(
                "order_id", PlatformDataType.LONG, null, null, null,
                false, null, false, false, "订单 ID"
        );
        JdbcQueryInputNodeDefinition queryInput = new JdbcQueryInputNodeDefinition(
                UUID.randomUUID().toString(),
                "订单查询输入",
                layout(),
                new JdbcQueryInputConfiguration(
                        UUID.randomUUID().toString(),
                        "SELECT order_id FROM orders",
                        "query_orders",
                        "a".repeat(64),
                        List.of(orderId)
                )
        );
        JdbcOutputNodeDefinition upsertOutput = new JdbcOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "订单 UPSERT",
                layout(),
                new JdbcOutputConfiguration(
                        "query_orders",
                        UUID.randomUUID().toString(),
                        "orders",
                        JdbcWriteMode.UPSERT,
                        List.of(new JdbcColumnMapping("order_id", "order_id")),
                        List.of("order_id")
                )
        );

        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(queryInput), List.of())));
        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(queryInput, upsertOutput), List.of())));
        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                1, 23, List.of(queryInput), List.of())));
        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                1, 27, List.of(upsertOutput), List.of())));
    }

    @Test
    void rejectsPreOneDotTwentyEightJdbcOutputDefinitions() {
        String json = """
                {
                  "schemaVersion": 1,
                  "schemaMinorVersion": 27,
                  "nodes": [{
                    "id": "11111111-1111-4111-8111-111111111111",
                    "type": "JDBC_OUTPUT",
                    "name": "旧 JDBC 输出",
                    "layout": {"x": 0, "y": 0, "width": 240, "height": 120},
                    "configuration": {
                      "sourceTableName": "orders",
                      "dataSourceId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                      "targetTableName": "orders",
                      "writeMode": "APPEND",
                      "columnMappings": [{
                        "sourceColumnName": "order_id",
                        "targetColumnName": "order_id"
                      }]
                    }
                  }],
                  "edges": []
                }
                """;

        CanvasDefinition parsed = new ObjectMapper().readValue(json, CanvasDefinition.class);
        JdbcOutputNodeDefinition output = (JdbcOutputNodeDefinition) parsed.nodes().getFirst();

        assertEquals(List.of(), output.configuration().upsertKeyColumns());
        assertThrows(ResponseStatusException.class, () -> validator.validate(parsed));
    }

    @Test
    void validatesRuntimeValuesInGlobalAndPerTableDerivations() {
        DeriveColumnsNodeDefinition valid = new DeriveColumnsNodeDefinition(
                UUID.randomUUID().toString(), "派生技术字段", layout(),
                new DeriveColumnsConfiguration(
                        List.of(new ColumnDerivation(
                                "etl_batch_id",
                                new RuntimeValueExpression(CanvasRuntimeValue.EXECUTION_ID))),
                        List.of(new DeriveColumnsOperation(
                                UUID.randomUUID().toString(), "orders",
                                new ProcessorOutput.ReplaceSource("orders"),
                                List.of(new ColumnDerivation(
                                        "etl_loaded_at",
                                        new RuntimeValueExpression(CanvasRuntimeValue.EXECUTION_STARTED_AT)))))));
        DeriveColumnsNodeDefinition invalid = new DeriveColumnsNodeDefinition(
                UUID.randomUUID().toString(), "缺少运行时变量", layout(),
                new DeriveColumnsConfiguration(List.of(), List.of(new DeriveColumnsOperation(
                        UUID.randomUUID().toString(), "orders",
                        new ProcessorOutput.ReplaceSource("orders"),
                        List.of(new ColumnDerivation(
                                "etl_batch_id", new RuntimeValueExpression(null)))))));

        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(valid), List.of())));
        assertThrows(ResponseStatusException.class, () -> validator.validate(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(invalid), List.of())));
    }

    private static FileOutputNodeDefinition fileOutput(FileOutputFormatOptions formatOptions) {
        return new FileOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "空间文件输出",
                layout(),
                new FileOutputConfiguration(
                        "districts",
                        UUID.randomUUID().toString(),
                        "exports/districts",
                        FileOutputConflictPolicy.FAIL_IF_EXISTS,
                        formatOptions
                )
        );
    }

    private void assertIntroducedAt(CanvasNodeDefinition node, int schemaMinorVersion) {
        CanvasDefinition introduced = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(node),
                List.of()
        );
        CanvasDefinition previous = new CanvasDefinition(
                1,
                schemaMinorVersion,
                List.of(node),
                List.of()
        );

        assertDoesNotThrow(() -> validator.validate(introduced));
        assertThrows(ResponseStatusException.class, () -> validator.validate(previous));
    }

    private static CanvasNodeLayout layout() {
        return new CanvasNodeLayout(0d, 0d, 240d, 120d);
    }

    private static TypeCastNodeDefinition epochTimestampTypeCast(PlatformDataType targetType) {
        return new TypeCastNodeDefinition(
                UUID.randomUUID().toString(),
                "Epoch 时间戳转换",
                layout(),
                new TypeCastConfiguration(List.of(new TypeCastOperation(
                        UUID.randomUUID().toString(),
                        "events",
                        new ProcessorOutput.CreateNewTable("typed_events"),
                        List.of(new ColumnTypeCast(
                                "event_time",
                                new PlatformTypeDefinition(targetType, null, null, null, null),
                                CastFailureStrategy.FAIL,
                                EpochTimestampUnit.MILLISECONDS
                        ))
                )))
        );
    }

    private static CanvasDefinition definition(
            String inputId,
            String outputId,
            String inputModelId,
            String outputModelId
    ) {
        return new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                        new ModelInputNodeDefinition(
                                inputId,
                                "模型输入",
                                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                                new ModelInputConfiguration(inputModelId)
                        ),
                        new ModelOutputNodeDefinition(
                                outputId,
                                "模型输出",
                                new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                new ModelOutputConfiguration(
                                        "orders",
                                        outputModelId,
                                        null,
                                        List.of()
                                )
                        )
                ),
                List.of(new CanvasEdgeDefinition(
                        UUID.randomUUID().toString(),
                        inputId,
                        outputId
                ))
        );
    }
}

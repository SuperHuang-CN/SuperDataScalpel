package cn.superhuang.datascalpel.taskengine.contract;

import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeoParquetCompressionCodec;
import cn.superhuang.data.scalpel.contract.task.GeoParquetCoveringMode;
import cn.superhuang.datascalpel.taskengine.http.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpatialFileOutputContractTest {
    private final ObjectMapper objectMapper = JsonSupport.strictObjectMapper();

    @Test
    void roundTripsGeoParquetAndGeoJsonDiscriminatedConfigurations() throws Exception {
        CanvasDefinition definition = objectMapper.readValue("""
                {
                  "schemaVersion": 1,
                  "schemaMinorVersion": 25,
                  "nodes": [{
                    "id": "11111111-1111-4111-8111-111111111111",
                    "type": "FILE_OUTPUT",
                    "name": "GeoParquet 输出",
                    "layout": {"x": 0, "y": 0, "width": 240, "height": 120},
                    "configuration": {
                      "sourceTableName": "districts",
                      "dataSourceId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                      "targetPath": "exports/geoparquet",
                      "conflictPolicy": "FAIL_IF_EXISTS",
                      "formatOptions": {
                        "type": "GEOPARQUET",
                        "geometryColumnName": "geom",
                        "compression": "ZSTD",
                        "coveringMode": "ROW_BBOX"
                      }
                    }
                  }, {
                    "id": "22222222-2222-4222-8222-222222222222",
                    "type": "FILE_OUTPUT",
                    "name": "GeoJSON 输出",
                    "layout": {"x": 320, "y": 0, "width": 240, "height": 120},
                    "configuration": {
                      "sourceTableName": "districts",
                      "dataSourceId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                      "targetPath": "exports/geojson",
                      "conflictPolicy": "OVERWRITE",
                      "formatOptions": {
                        "type": "GEOJSON",
                        "baseName": "districts",
                        "geometryColumnName": "geom",
                        "idColumnName": "district_id",
                        "ignoreNullProperties": true
                      }
                    }
                  }],
                  "edges": []
                }
                """, CanvasDefinition.class);

        FileOutputFormatOptions.GeoParquet geoParquet = assertInstanceOf(
                FileOutputFormatOptions.GeoParquet.class,
                ((FileOutputNodeDefinition) definition.nodes().getFirst())
                        .configuration().formatOptions());
        assertEquals(GeoParquetCompressionCodec.ZSTD, geoParquet.compression());
        assertEquals(GeoParquetCoveringMode.ROW_BBOX, geoParquet.coveringMode());
        FileOutputFormatOptions.GeoJson geoJson = assertInstanceOf(
                FileOutputFormatOptions.GeoJson.class,
                ((FileOutputNodeDefinition) definition.nodes().getLast())
                        .configuration().formatOptions());
        assertEquals("district_id", geoJson.idColumnName());
        assertEquals(definition, objectMapper.readValue(
                objectMapper.writeValueAsBytes(definition), CanvasDefinition.class));
    }

    @Test
    void rejectsUnknownGeoParquetEnums() {
        assertThrows(Exception.class, () -> objectMapper.readValue("""
                {
                  "type": "GEOPARQUET",
                  "geometryColumnName": "geom",
                  "compression": "GZIP",
                  "coveringMode": "ROW_BBOX"
                }
                """, FileOutputFormatOptions.class));
    }

    @Test
    void rejectsUnknownFormatDiscriminatorsAndUnknownGeoJsonFields() {
        assertThrows(Exception.class, () -> objectMapper.readValue("""
                {"type":"GEO_PACKAGE"}
                """, FileOutputFormatOptions.class));
        assertThrows(Exception.class, () -> objectMapper.readValue("""
                {
                  "type": "GEOJSON",
                  "baseName": "districts",
                  "geometryColumnName": "geom",
                  "idColumnName": null,
                  "ignoreNullProperties": false,
                  "prettyPrint": true
                }
                """, FileOutputFormatOptions.class));
    }

    @Test
    void preservesMissingGeoJsonValuesForCompilerValidation() throws Exception {
        FileOutputFormatOptions.GeoJson options = assertInstanceOf(
                FileOutputFormatOptions.GeoJson.class,
                objectMapper.readValue("""
                        {
                          "type": "GEOJSON",
                          "ignoreNullProperties": false
                        }
                        """, FileOutputFormatOptions.class));

        assertNull(options.baseName());
        assertNull(options.geometryColumnName());
        assertNull(options.idColumnName());
    }
}

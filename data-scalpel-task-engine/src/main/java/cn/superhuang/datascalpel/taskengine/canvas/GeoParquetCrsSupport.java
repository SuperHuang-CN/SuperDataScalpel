package cn.superhuang.datascalpel.taskengine.canvas;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.datasyslab.proj4sedona.core.Proj;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.referencing.CRS;

/** Converts the authoritative Canvas EPSG code to the PROJJSON expected by Sedona GeoParquet. */
public final class GeoParquetCrsSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private GeoParquetCrsSupport() {
    }

    public static String projJson(int epsgCode) {
        try {
            // Resolve from the bundled GeoTools EPSG database first. Calling
            // Proj4Sedona with an arbitrary EPSG code can consult its URL provider,
            // which would violate the Compiler's schema-only/no-external-I/O boundary.
            CoordinateReferenceSystem crs = CRS.decode("EPSG:" + epsgCode, true);
            String serialized = new Proj(crs.toWKT()).toProjJson();
            if (serialized == null || serialized.isBlank()) {
                throw new IllegalArgumentException("无法生成 EPSG:" + epsgCode + " 的 PROJJSON");
            }
            ObjectNode projJson = (ObjectNode) OBJECT_MAPPER.readTree(serialized);
            ObjectNode identifier = OBJECT_MAPPER.createObjectNode();
            identifier.put("authority", "EPSG");
            identifier.put("code", epsgCode);
            projJson.set("id", identifier);
            return OBJECT_MAPPER.writeValueAsString(projJson);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (FactoryException | JsonProcessingException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "无法生成 EPSG:" + epsgCode + " 的 PROJJSON", exception);
        }
    }
}

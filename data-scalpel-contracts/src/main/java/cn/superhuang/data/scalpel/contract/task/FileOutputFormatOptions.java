package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FileOutputFormatOptions.Csv.class, name = "CSV"),
        @JsonSubTypes.Type(value = FileOutputFormatOptions.JsonLines.class, name = "JSON_LINES"),
        @JsonSubTypes.Type(value = FileOutputFormatOptions.Parquet.class, name = "PARQUET"),
        @JsonSubTypes.Type(value = FileOutputFormatOptions.Shapefile.class, name = "SHAPEFILE"),
        @JsonSubTypes.Type(value = FileOutputFormatOptions.GeoParquet.class, name = "GEOPARQUET"),
        @JsonSubTypes.Type(value = FileOutputFormatOptions.GeoJson.class, name = "GEOJSON")
})
public sealed interface FileOutputFormatOptions permits
        FileOutputFormatOptions.Csv,
        FileOutputFormatOptions.JsonLines,
        FileOutputFormatOptions.Parquet,
        FileOutputFormatOptions.Shapefile,
        FileOutputFormatOptions.GeoParquet,
        FileOutputFormatOptions.GeoJson {

    record Csv(
            boolean header,
            String delimiter,
            String quote,
            String escape,
            String nullValue
    ) implements FileOutputFormatOptions {
    }

    record JsonLines(boolean ignoreNullFields) implements FileOutputFormatOptions {
    }

    record Parquet() implements FileOutputFormatOptions {
    }

    record Shapefile(
            String baseName,
            ShapefilePackageMode packageMode,
            String geometryColumnName,
            ShapefileShapeType targetShapeType,
            java.util.List<ShapefileAttributeMapping> attributeMappings
    ) implements FileOutputFormatOptions {
        public Shapefile {
            attributeMappings = attributeMappings == null
                    ? null : java.util.List.copyOf(attributeMappings);
        }
    }

    record GeoParquet(
            String geometryColumnName,
            GeoParquetCompressionCodec compression,
            GeoParquetCoveringMode coveringMode
    ) implements FileOutputFormatOptions {
    }

    record GeoJson(
            String baseName,
            String geometryColumnName,
            String idColumnName,
            boolean ignoreNullProperties
    ) implements FileOutputFormatOptions {
    }
}

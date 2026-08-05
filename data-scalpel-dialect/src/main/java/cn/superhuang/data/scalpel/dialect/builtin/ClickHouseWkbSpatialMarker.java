package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Canonical ClickHouse column-comment declaration for raw two-dimensional WKB. */
final class ClickHouseWkbSpatialMarker {

    static final String PREFIX = "@datascalpel-spatial-v1";

    private static final Pattern MARKER = Pattern.compile(
            "^@datascalpel-spatial-v1 encoding=WKB kind=([A-Z]+) crs=EPSG:([1-9][0-9]*) dimension=XY$"
    );

    private ClickHouseWkbSpatialMarker() {
    }

    static String render(GeometryTypeDefinition geometry) {
        String issue = SpatialTypeSupport.validateV1Geometry(geometry);
        if (issue != null) {
            throw new IllegalArgumentException(issue);
        }
        return PREFIX
                + " encoding=WKB kind=" + geometry.kind().name()
                + " crs=EPSG:" + geometry.crs().code()
                + " dimension=XY";
    }

    static ParseResult parse(String comment) {
        if (comment == null || comment.isBlank()) {
            return ParseResult.absent(comment);
        }
        String normalized = comment.replace("\r\n", "\n").replace('\r', '\n');
        List<String> humanLines = new ArrayList<>();
        List<String> markerLines = new ArrayList<>();
        for (String line : normalized.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.contains(PREFIX)) {
                markerLines.add(trimmed);
            } else {
                humanLines.add(line);
            }
        }
        String humanComment = normalizeHumanComment(humanLines);
        if (markerLines.isEmpty()) {
            return ParseResult.absent(comment);
        }
        if (markerLines.size() != 1) {
            return ParseResult.invalid(humanComment, "ClickHouse WKB 空间声明必须且只能出现一次");
        }

        Matcher matcher = MARKER.matcher(markerLines.getFirst());
        if (!matcher.matches()) {
            return ParseResult.invalid(humanComment, "ClickHouse WKB 空间声明格式不合法");
        }
        GeometryKind kind;
        int crsCode;
        try {
            kind = GeometryKind.valueOf(matcher.group(1));
            crsCode = Integer.parseInt(matcher.group(2));
        } catch (IllegalArgumentException exception) {
            return ParseResult.invalid(humanComment, "ClickHouse WKB 空间声明包含不支持的类型或 EPSG code");
        }
        GeometryTypeDefinition geometry = new GeometryTypeDefinition(
                kind,
                CrsReference.epsg(crsCode),
                CoordinateDimension.XY
        );
        return ParseResult.valid(humanComment, geometry);
    }

    private static String normalizeHumanComment(List<String> lines) {
        String value = String.join("\n", lines).trim();
        return value.isEmpty() ? null : value;
    }

    record ParseResult(
            boolean present,
            String humanComment,
            GeometryTypeDefinition geometry,
            String issue
    ) {

        static ParseResult absent(String comment) {
            String value = comment == null || comment.isBlank() ? null : comment.trim();
            return new ParseResult(false, value, null, null);
        }

        static ParseResult valid(String humanComment, GeometryTypeDefinition geometry) {
            return new ParseResult(true, humanComment, geometry, null);
        }

        static ParseResult invalid(String humanComment, String issue) {
            return new ParseResult(true, humanComment, null, issue);
        }
    }
}

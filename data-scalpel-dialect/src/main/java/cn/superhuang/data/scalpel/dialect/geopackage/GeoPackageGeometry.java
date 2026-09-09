package cn.superhuang.data.scalpel.dialect.geopackage;

import cn.superhuang.data.scalpel.contract.type.GeometryKind;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Validates a GeoPackage Binary geometry and exposes its unwrapped XY WKB. */
public record GeoPackageGeometry(byte[] wkb, GeometryKind kind, boolean empty) {

    public static GeoPackageGeometry decode(
            byte[] input,
            int expectedSrsId,
            GeometryKind expectedKind,
            String tableName,
            String fieldName,
            long rowNumber
    ) {
        try {
            if (input.length < 8 || input[0] != 'G' || input[1] != 'P' || input[2] != 0) {
                throw failure(tableName, fieldName, rowNumber, "GeoPackage Geometry Header 无效");
            }
            int flags = Byte.toUnsignedInt(input[3]);
            if ((flags & 0xE0) != 0) throw failure(tableName, fieldName, rowNumber, "Geometry Header 保留位无效");
            ByteOrder order = (flags & 1) == 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
            int envelope = (flags >>> 1) & 7;
            int envelopeBytes = switch (envelope) {
                case 0 -> 0;
                case 1 -> 32;
                case 2, 3, 4 -> throw failure(
                        tableName, fieldName, rowNumber, "Geometry Envelope 声明 Z 或 M，当前只支持二维 XY"
                );
                default -> throw failure(tableName, fieldName, rowNumber, "Geometry Envelope 标识无效");
            };
            if (input.length < 8 + envelopeBytes) throw failure(tableName, fieldName, rowNumber, "Geometry Header 长度不足");
            ByteBuffer header = ByteBuffer.wrap(input).order(order);
            header.position(4);
            if (header.getInt() != expectedSrsId) throw failure(tableName, fieldName, rowNumber, "Geometry SRS 与图层元数据不一致");
            for (int index = 0; index < envelopeBytes / 8; index++) {
                if (!Double.isFinite(header.getDouble())) throw failure(tableName, fieldName, rowNumber, "Geometry Envelope 包含非有限坐标");
            }
            boolean empty = (flags & 0x10) != 0;
            if (empty && envelope != 0) {
                throw failure(tableName, fieldName, rowNumber, "Empty Geometry 不能包含 Envelope");
            }
            int wkbOffset = 8 + envelopeBytes;
            if (empty && wkbOffset == input.length) return new GeoPackageGeometry(new byte[0], expectedKind, true);
            if (wkbOffset >= input.length) throw failure(tableName, fieldName, rowNumber, "Geometry 缺少 WKB 内容");
            byte[] wkb = Arrays.copyOfRange(input, wkbOffset, input.length);
            WkbCursor cursor = new WkbCursor(wkb, tableName, fieldName, rowNumber);
            WkbCursor.GeometryResult result = cursor.geometry(null, 0);
            if (cursor.position != wkb.length) throw failure(tableName, fieldName, rowNumber, "Geometry WKB 包含尾随内容");
            if (expectedKind != GeometryKind.GEOMETRY && result.kind() != expectedKind) {
                throw failure(tableName, fieldName, rowNumber, "Geometry 类型与图层元数据不一致");
            }
            if (empty && !result.empty()) {
                throw failure(tableName, fieldName, rowNumber, "Geometry Empty 标记与 WKB 内容不一致");
            }
            return new GeoPackageGeometry(wkb, result.kind(), empty);
        } catch (GeoPackageException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(tableName, fieldName, rowNumber, "Geometry WKB 结构无效");
        }
    }

    private static GeoPackageException failure(String table, String field, long row, String reason) {
        return new GeoPackageException("GeoPackage 表 " + table + " 第 " + row + " 条记录 Geometry 字段 " + field + " " + reason);
    }

    private static final class WkbCursor {
        private static final int MAX_DEPTH = 128;
        private final byte[] values;
        private final String table;
        private final String field;
        private final long row;
        private int position;

        private WkbCursor(byte[] values, String table, String field, long row) {
            this.values = values;
            this.table = table;
            this.field = field;
            this.row = row;
        }

        private GeometryResult geometry(GeometryKind required, int depth) {
            if (depth > MAX_DEPTH) throw failure(table, field, row, "Geometry 嵌套层级超过限制");
            int marker = unsignedByte();
            ByteOrder order;
            if (marker == 0) {
                order = ByteOrder.BIG_ENDIAN;
            } else if (marker == 1) {
                order = ByteOrder.LITTLE_ENDIAN;
            } else {
                throw throwFailure("WKB 字节序无效");
            }
            int type = integer(order);
            if ((type & 0xE0000000) != 0 || type < 1 || type > 7) throw failure(table, field, row, "只支持二维基础 WKB Geometry");
            GeometryKind kind = switch (type) {
                case 1 -> GeometryKind.POINT;
                case 2 -> GeometryKind.LINESTRING;
                case 3 -> GeometryKind.POLYGON;
                case 4 -> GeometryKind.MULTIPOINT;
                case 5 -> GeometryKind.MULTILINESTRING;
                case 6 -> GeometryKind.MULTIPOLYGON;
                case 7 -> GeometryKind.GEOMETRYCOLLECTION;
                default -> throw throwFailure("WKB Geometry 类型无效");
            };
            if (required != null && kind != required) throw failure(table, field, row, "WKB Multi Geometry 成员类型不一致");
            boolean empty = switch (kind) {
                case POINT -> point(order);
                case LINESTRING -> points(order) == 0;
                case POLYGON -> polygon(order);
                case MULTIPOINT -> collection(order, GeometryKind.POINT, depth);
                case MULTILINESTRING -> collection(order, GeometryKind.LINESTRING, depth);
                case MULTIPOLYGON -> collection(order, GeometryKind.POLYGON, depth);
                case GEOMETRYCOLLECTION -> collection(order, null, depth);
                default -> throw throwFailure("WKB Geometry 类型无效");
            };
            return new GeometryResult(kind, empty);
        }

        private boolean collection(ByteOrder order, GeometryKind required, int depth) {
            int count = count(order);
            boolean allEmpty = count > 0;
            for (int index = 0; index < count; index++) {
                allEmpty &= geometry(required, depth + 1).empty();
            }
            return count == 0 || allEmpty;
        }

        private boolean polygon(ByteOrder order) {
            int rings = count(order);
            for (int index = 0; index < rings; index++) {
                double[][] ring = ringPoints(order);
                if (ring.length > 0 && (ring.length < 4 || ring[0][0] != ring[ring.length - 1][0] || ring[0][1] != ring[ring.length - 1][1])) {
                    throw failure(table, field, row, "Polygon 环未闭合");
                }
            }
            return rings == 0;
        }

        private boolean point(ByteOrder order) {
            double[] coordinate = coordinate(order, true);
            return Double.isNaN(coordinate[0]) && Double.isNaN(coordinate[1]);
        }

        private int points(ByteOrder order) {
            int count = count(order);
            for (int index = 0; index < count; index++) {
                coordinate(order, false);
            }
            return count;
        }

        private double[][] ringPoints(ByteOrder order) {
            int count = count(order);
            double[][] result = new double[count][2];
            for (int index = 0; index < count; index++) {
                result[index] = coordinate(order, false);
            }
            return result;
        }

        private double[] coordinate(ByteOrder order, boolean point) {
            double x = decimal(order);
            double y = decimal(order);
            if (Double.isNaN(x) && Double.isNaN(y) && point) return new double[]{x, y};
            if (!Double.isFinite(x) || !Double.isFinite(y)) throw failure(table, field, row, "WKB 包含非有限 XY 坐标");
            return new double[]{x, y};
        }

        private int count(ByteOrder order) {
            int value = integer(order);
            if (value < 0 || value > 10_000_000) throw failure(table, field, row, "WKB 坐标/成员数量无效");
            return value;
        }

        private int unsignedByte() {
            require(1);
            return Byte.toUnsignedInt(values[position++]);
        }

        private int integer(ByteOrder order) {
            require(4);
            int value = ByteBuffer.wrap(values, position, 4).order(order).getInt();
            position += 4;
            return value;
        }

        private double decimal(ByteOrder order) {
            require(8);
            double value = ByteBuffer.wrap(values, position, 8).order(order).getDouble();
            position += 8;
            return value;
        }

        private void require(int count) {
            if (count > values.length - position) throw failure(table, field, row, "WKB 内容不完整");
        }

        private GeoPackageException throwFailure(String message) {
            return failure(table, field, row, message);
        }

        private record GeometryResult(GeometryKind kind, boolean empty) {
        }
    }
}

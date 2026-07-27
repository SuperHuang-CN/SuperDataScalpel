package cn.superhuang.data.scalpel.shapefile.internal;

import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileSpatialReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class PrjReader {
    private PrjReader() {
    }

    public static ShapefileSpatialReference read(RandomAccessObjectReader reader, int maximumBytes) {
        if (reader == null) {
            return null;
        }
        if (reader.size() > maximumBytes) {
            throw new ShapefileException(
                    ShapefileErrorCode.LIMIT_EXCEEDED, "PRJ component exceeds the configured metadata limit");
        }
        byte[] bytes = reader.read(0, Math.toIntExact(reader.size()), ByteOrder.BIG_ENDIAN).array();
        String wkt;
        try {
            wkt = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.INVALID_ENCODING, "PRJ component is not valid UTF-8 text", exception);
        }
        if (!wkt.isEmpty() && wkt.charAt(0) == '\uFEFF') {
            wkt = wkt.substring(1);
        }
        wkt = wkt.trim();
        if (wkt.isEmpty()) {
            throw new ShapefileException(ShapefileErrorCode.MALFORMED_HEADER, "PRJ component is empty");
        }
        return new ShapefileSpatialReference(wkt);
    }
}

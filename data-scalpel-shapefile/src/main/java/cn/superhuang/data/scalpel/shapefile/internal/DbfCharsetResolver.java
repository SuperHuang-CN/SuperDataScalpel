package cn.superhuang.data.scalpel.shapefile.internal;

import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import cn.superhuang.data.scalpel.shapefile.ShapefileOpenOptions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Resolves DBF text encoding with explicit, auditable precedence. */
final class DbfCharsetResolver {
    private static final Map<Integer, String> LANGUAGE_DRIVER_CHARSETS = Map.ofEntries(
            Map.entry(0x01, "IBM437"),
            Map.entry(0x02, "IBM850"),
            Map.entry(0x03, "windows-1252"),
            Map.entry(0x4D, "GBK"),
            Map.entry(0x57, "windows-1252"),
            Map.entry(0x64, "IBM852"),
            Map.entry(0x65, "IBM866"),
            Map.entry(0x66, "IBM865"),
            Map.entry(0x67, "IBM861"),
            Map.entry(0x6A, "IBM737"),
            Map.entry(0x6B, "IBM857"),
            Map.entry(0x7A, "GBK"),
            Map.entry(0x7B, "windows-932"),
            Map.entry(0x7C, "windows-874"),
            Map.entry(0x7D, "windows-1255"),
            Map.entry(0x7E, "windows-1256"),
            Map.entry(0xC8, "windows-1250"),
            Map.entry(0xC9, "windows-1251"),
            Map.entry(0xCA, "windows-1254"),
            Map.entry(0xCB, "windows-1253"));

    private static final Map<String, String> CPG_ALIASES = Map.ofEntries(
            Map.entry("65001", "UTF-8"),
            Map.entry("CP65001", "UTF-8"),
            Map.entry("ANSI 65001", "UTF-8"),
            Map.entry("437", "IBM437"),
            Map.entry("CP437", "IBM437"),
            Map.entry("850", "IBM850"),
            Map.entry("CP850", "IBM850"),
            Map.entry("852", "IBM852"),
            Map.entry("CP852", "IBM852"),
            Map.entry("857", "IBM857"),
            Map.entry("CP857", "IBM857"),
            Map.entry("866", "IBM866"),
            Map.entry("CP866", "IBM866"),
            Map.entry("936", "GBK"),
            Map.entry("CP936", "GBK"),
            Map.entry("ANSI 936", "GBK"),
            Map.entry("932", "windows-932"),
            Map.entry("CP932", "windows-932"),
            Map.entry("874", "windows-874"),
            Map.entry("CP874", "windows-874"),
            Map.entry("1250", "windows-1250"),
            Map.entry("CP1250", "windows-1250"),
            Map.entry("1251", "windows-1251"),
            Map.entry("CP1251", "windows-1251"),
            Map.entry("1252", "windows-1252"),
            Map.entry("CP1252", "windows-1252"),
            Map.entry("1253", "windows-1253"),
            Map.entry("CP1253", "windows-1253"),
            Map.entry("1254", "windows-1254"),
            Map.entry("CP1254", "windows-1254"),
            Map.entry("1255", "windows-1255"),
            Map.entry("CP1255", "windows-1255"),
            Map.entry("1256", "windows-1256"),
            Map.entry("CP1256", "windows-1256"));

    private DbfCharsetResolver() {
    }

    static Charset resolve(RandomAccessObjectReader cpg, int languageDriverId, ShapefileOpenOptions options) {
        if (options.dbfCharsetOverride() != null) {
            return options.dbfCharsetOverride();
        }
        if (cpg != null) {
            return readCpg(cpg, options.readLimits().maxMetadataBytes());
        }
        String mapped = LANGUAGE_DRIVER_CHARSETS.get(languageDriverId);
        if (mapped != null) {
            return charset(mapped, "DBF language driver");
        }
        return options.dbfFallbackCharset();
    }

    private static Charset readCpg(RandomAccessObjectReader reader, int maximumBytes) {
        if (reader.size() > maximumBytes) {
            throw new ShapefileException(
                    ShapefileErrorCode.LIMIT_EXCEEDED, "CPG component exceeds the configured metadata limit");
        }
        byte[] bytes = reader.read(0, Math.toIntExact(reader.size()), ByteOrder.BIG_ENDIAN).array();
        String value;
        try {
            value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.INVALID_ENCODING, "CPG component is not valid UTF-8 text", exception);
        }
        if (!value.isEmpty() && value.charAt(0) == '\uFEFF') {
            value = value.substring(1);
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new ShapefileException(ShapefileErrorCode.INVALID_ENCODING, "CPG component is empty");
        }
        return charset(CPG_ALIASES.getOrDefault(value.toUpperCase(java.util.Locale.ROOT), value), "CPG");
    }

    private static Charset charset(String name, String source) {
        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.INVALID_ENCODING, source + " declares an unsupported charset", exception);
        }
    }
}

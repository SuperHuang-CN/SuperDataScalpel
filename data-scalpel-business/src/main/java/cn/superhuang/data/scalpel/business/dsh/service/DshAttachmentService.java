package cn.superhuang.data.scalpel.business.dsh.service;

import cn.superhuang.data.scalpel.business.dsh.web.request.UploadDshAttachmentRequest;
import cn.superhuang.data.scalpel.business.dsh.web.response.DshAttachmentResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.ZipInputStream;

@Service
public class DshAttachmentService {
    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_UPLOAD_REQUEST_BYTES = 8 * 1024 * 1024;
    public static final int MAX_BRIDGE_REQUEST_BYTES = 9 * 1024 * 1024;
    private static final int MAX_EXTRACTED_BYTES = 1024 * 1024;
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("xls", "application/vnd.ms-excel"), Map.entry("csv", "text/csv"),
            Map.entry("tsv", "text/tab-separated-values"), Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"), Map.entry("json", "application/json"),
            Map.entry("yaml", "application/yaml"), Map.entry("yml", "application/yaml"),
            Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("webp", "image/webp"), Map.entry("gif", "image/gif"));
    private final DshService service;
    private final ObjectMapper mapper;
    public DshAttachmentService(DshService service, ObjectMapper mapper) { this.service = service; this.mapper = mapper; }

    public DshAttachmentResponse upload(UUID owner, UUID sessionId, UploadDshAttachmentRequest input) {
        // Check session ownership before parsing a potentially costly workbook. No model or tool is started.
        var session = service.get(owner, "/sessions/" + sessionId);
        if (session.path("archived").asBoolean()) throw DshProblems.error(409, "BRIDGE_SESSION_ARCHIVED", "请先恢复已归档的会话。");
        String ext = input.name().substring(input.name().lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String mediaType = TYPES.get(ext);
        if (mediaType == null) throw DshProblems.error(415, "DSH_ATTACHMENT_TYPE_UNSUPPORTED", "暂不支持此附件类型，请上传 Excel、文本或 PNG/JPEG/WebP/GIF 图片。");
        byte[] data = decode(input.data());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientAttachmentId", input.clientAttachmentId()); body.put("name", input.name());
        body.put("data", input.data()); body.put("mediaType", mediaType);
        if (!mediaType.startsWith("image/")) body.put("extractedText", extract(ext, data));
        return mapper.treeToValue(service.post(owner, "/sessions/" + sessionId + "/attachments", body), DshAttachmentResponse.class);
    }
    static byte[] decode(String encoded) {
        try {
            byte[] data = Base64.getDecoder().decode(encoded);
            if (data.length > MAX_FILE_BYTES) throw DshProblems.error(413, "DSH_ATTACHMENT_TOO_LARGE", "单个附件不能超过 5 MiB。");
            if (!Base64.getEncoder().encodeToString(data).equals(encoded)) throw new IllegalArgumentException();
            return data;
        } catch (IllegalArgumentException e) { throw DshProblems.error(400, "DSH_ATTACHMENT_INVALID", "附件 Base64 编码无效。"); }
    }
    String extract(String ext, byte[] data) {
        if (ext.equals("xlsx") || ext.equals("xls")) return excel(data, ext.equals("xlsx"));
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
            if (text.chars().anyMatch(c -> c < 32 && c != '\n' && c != '\r' && c != '\t')) throw new CharacterCodingException();
            requireBudget(data.length);
            return text;
        } catch (CharacterCodingException e) { throw DshProblems.error(422, "DSH_ATTACHMENT_UNREADABLE", "文本附件需要使用 UTF-8 编码，不能包含二进制内容。"); }
    }
    private String excel(byte[] data, boolean xlsx) {
        try {
            if (FileMagic.valueOf(data) != (xlsx ? FileMagic.OOXML : FileMagic.OLE2))
                throw new IllegalArgumentException("Excel format does not match its extension");
            if (xlsx) checkExpandedSize(data);
            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(data))) {
                var output = new ByteArrayOutputStream();
                DataFormatter formatter = new DataFormatter(Locale.CHINA);
                formatter.setUseCachedValuesForFormulaCells(true);
                append(output, Map.of("format", "excel", "sheets", workbook.getNumberOfSheets(), "formulaValues", "cached; formulas and macros are not executed"));
                if (workbook.getNumberOfSheets() > 100) throw DshProblems.error(413, "DSH_ATTACHMENT_CONTENT_TOO_LARGE", "Excel 最多支持 100 个工作表，请拆分后上传。");
                int cells = 0, rows = 0;
                for (Sheet sheet : workbook) {
                    append(output, Map.of("sheet", sheet.getSheetName(), "lastRow", sheet.getPhysicalNumberOfRows() == 0 ? 0 : sheet.getLastRowNum() + 1));
                    for (Row row : sheet) {
                        if (++rows > 20000) throw DshProblems.error(413, "DSH_ATTACHMENT_CONTENT_TOO_LARGE", "Excel 最多支持 20000 个有效行，请拆分后上传。");
                        Map<String, String> values = new LinkedHashMap<>();
                        for (Cell cell : row) {
                            if (++cells > 100000) throw DshProblems.error(413, "DSH_ATTACHMENT_CONTENT_TOO_LARGE", "Excel 最多支持 100000 个单元格，请拆分后上传。");
                            values.put(cell.getAddress().formatAsString(), formatter.formatCellValue(cell));
                        }
                        append(output, Map.of("row", row.getRowNum() + 1, "cells", values));
                    }
                }
                return output.toString(StandardCharsets.UTF_8);
            }
        } catch (IOException | org.apache.poi.EncryptedDocumentException | IllegalArgumentException e) {
            throw DshProblems.error(422, "DSH_ATTACHMENT_UNREADABLE", "Excel 无法解析，请检查文件是否损坏、加密或格式不正确。");
        }
    }
    private void append(ByteArrayOutputStream output, Object value) {
        byte[] line = mapper.writeValueAsBytes(value);
        requireBudget(output.size() + line.length + 1);
        output.writeBytes(line); output.write('\n');
    }
    private static void requireBudget(int bytes) {
        if (bytes > MAX_EXTRACTED_BYTES) throw DshProblems.error(413, "DSH_ATTACHMENT_CONTENT_TOO_LARGE", "附件解析内容超过 1 MiB，请拆分后上传；未截断或发送不完整内容。");
    }
    private void checkExpandedSize(byte[] data) throws IOException {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            long bytes = 0; int count = 0; byte[] buffer = new byte[8192];
            while (zip.getNextEntry() != null) {
                if (++count > 10000) throw DshProblems.error(413, "DSH_ATTACHMENT_CONTENT_TOO_LARGE", "Excel 压缩内容过多，请拆分后上传。");
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    bytes += read;
                    if (bytes > 32L * 1024 * 1024) throw DshProblems.error(413, "DSH_ATTACHMENT_CONTENT_TOO_LARGE", "Excel 解压后超过 32 MiB，请拆分后上传。");
                }
            }
        }
    }
    public ResponseEntity<byte[]> download(UUID owner, UUID sessionId, UUID id) {
        var result = service.get(owner, "/sessions/" + sessionId + "/attachments/" + id);
        byte[] bytes = decode(result.path("data").asText());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(result.path("name").asText(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(result.path("mediaType").asText()))
                .contentLength(bytes.length).body(bytes);
    }
}

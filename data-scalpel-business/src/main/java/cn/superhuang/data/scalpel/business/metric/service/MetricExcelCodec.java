package cn.superhuang.data.scalpel.business.metric.service;

import cn.superhuang.data.scalpel.business.metric.web.response.MetricImportIssueResponse;
import cn.superhuang.data.scalpel.business.metric.web.response.MetricImportPreviewResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** The V1 workbook carries metadata only. Formula cells are never evaluated. */
@Component
public class MetricExcelCodec {
    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final int MAX_ROWS = 1000;
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final String MARKER = "DATASCALPEL_METRIC_V1";

    public record Column(String key, String title, String description, String example, int width) {}
    public static final List<Column> COLUMNS = List.of(
            new Column("code", "指标编码*", "创建后固定，小写字母开头，仅含小写字母、数字和下划线，最长64位。", "daily_rainfall", 26),
            new Column("name", "指标名称*", "最长100字。", "日降雨量", 24),
            new Column("kind", "指标类型*", "原子指标/派生指标/复合指标；首次发布后固定。", "派生指标", 16),
            new Column("directoryPath", "目录路径", "使用已存在的完整目录路径，以 / 分隔；留空为未分类。", "水利/降雨", 28),
            new Column("ownerName", "业务负责人", "发布前填写，最长100字。", "水文监测科", 20),
            new Column("summary", "简介", "选填，最长1000字。", "单站日累计降雨量", 32),
            new Column("businessMeaning", "业务含义", "发布前填写，最长10000字。", "反映一个测站在一个统计日内的累计降雨量。", 44),
            new Column("calculation", "计算口径", "发布前填写，普通文本，最长20000字；不执行公式。", "对有效小时降雨量去重后按测站、统计日求和。", 54),
            new Column("statisticalScope", "统计范围", "发布前填写，最长10000字。", "纳入正式测站，排除测试记录及异常观测。", 44),
            new Column("sourceGrain", "来源粒度", "选填，最长2000字。", "每行一个测站、一个小时。", 36),
            new Column("statisticalPeriod", "统计周期", "无周期/日度/周度/月度/季度/年度，留空默认无周期。", "日度", 16),
            new Column("timeDescription", "时间口径", "时间型指标发布前填写，最长10000字。", "示例：北京时间当日0时至次日0时，归属当日。", 44),
            new Column("grainDescription", "结果粒度", "发布前填写，最长2000字。", "每行一个统计日、一个测站。", 36),
            new Column("periodFormat", "字符串时间格式", "绑定字符串时间字段时发布前填写，最长100字。", "yyyy-MM-dd", 24),
            new Column("unit", "单位", "发布前填写，最长32字。", "mm", 14),
            new Column("nullHandling", "空值与零值", "发布前填写，最长10000字。", "观测完整且无降雨为0；缺测为空。", 44),
            new Column("aggregationDescription", "汇总说明", "发布前填写，最长10000字。", "同站完整日值可累计；跨站不直接相加。", 44),
            new Column("updateDescription", "更新说明", "选填，最长2000字。", "次日更新，补录后重新计算对应日期。", 36),
            new Column("decimalPlaces", "显示小数位", "0至10的整数，留空默认2。", "1", 16),
            new Column("valueFormat", "数值显示", "普通数值/比值百分比/百分数值，留空默认普通数值。", "普通数值", 20),
            new Column("metricId", "指标ID（勿改）", "系统生成。更新已有指标必须保留，新增指标留空。", "", 38),
            new Column("draftFingerprint", "草稿摘要（勿改）", "系统生成，用于检查离线编辑期间的草稿冲突。", "", 68),
            new Column("basicsFingerprint", "资料摘要（勿改）", "系统生成，用于检查基础资料及状态变化。", "", 68),
            new Column("status", "指标状态（只读）", "仅供查阅，导入不会修改状态。", "草稿", 16),
            new Column("releaseVersion", "发布版本（只读）", "仅供查阅，导入不会发布或修改历史版本。", "", 16)
    );

    record SheetRow(int rowNumber, Map<String, String> cells, List<MetricImportIssueResponse> issues) {
        String get(String key) { return cells.getOrDefault(key, ""); }
    }
    record ParsedWorkbook(String fileDigest, List<SheetRow> rows) {}

    public MetricExcelFile template() {
        return new MetricExcelFile("DataScalpel-指标导入模板.xlsx", write("DRAFT", List.of()));
    }

    public byte[] write(String mode, List<Map<String, String>> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(workbook);
            CellStyle text = workbook.createCellStyle();
            text.setDataFormat(workbook.createDataFormat().getFormat("@"));
            text.setVerticalAlignment(VerticalAlignment.TOP);
            text.setWrapText(true);
            Sheet sheet = workbook.createSheet("指标");
            sheet.setDisplayGridlines(false);
            Row headings = sheet.createRow(0);
            headings.setHeightInPoints(28);
            for (int i = 0; i < COLUMNS.size(); i++) {
                Column column = COLUMNS.get(i);
                put(headings, i, column.title(), header);
                sheet.setColumnWidth(i, column.width() * 256);
                sheet.setDefaultColumnStyle(i, text);
                if (i >= 20 && i <= 22) sheet.setColumnHidden(i, true);
            }
            CellStyle integer = workbook.createCellStyle();
            integer.cloneStyleFrom(text);
            integer.setDataFormat(workbook.createDataFormat().getFormat("0"));
            for (int i = 0; i < rows.size(); i++) {
                Row row = sheet.createRow(i + 1);
                row.setHeightInPoints(60);
                for (int c = 0; c < COLUMNS.size(); c++) {
                    String value = rows.get(i).getOrDefault(COLUMNS.get(c).key(), "");
                    if (c == 18 && value != null && !value.isBlank()) {
                        Cell cell = row.createCell(c);
                        cell.setCellValue(Integer.parseInt(value));
                        cell.setCellStyle(integer);
                    } else put(row, c, value, text);
                }
            }
            sheet.createFreezePane(2, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(1, rows.size()), 0, COLUMNS.size() - 1));
            dropdown(sheet, 2, "原子指标", "派生指标", "复合指标");
            dropdown(sheet, 10, "无周期", "日度", "周度", "月度", "季度", "年度");
            dropdown(sheet, 19, "普通数值", "比值百分比", "百分数值");
            instructions(workbook, mode, header, text);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成指标 Excel 失败", e);
        }
    }

    private static void instructions(XSSFWorkbook workbook, String mode, CellStyle header, CellStyle text) {
        Sheet sheet = workbook.createSheet("填写说明");
        sheet.setDisplayGridlines(false);
        String[][] notes = {
                {"文件标识", MARKER}, {"导出内容", mode},
                {"用途", mode.equals("DRAFT") ? "线下整理草稿并回导。示例仅在本页，不参与导入。" : "当前发布口径查阅版，不可回导；线下编辑请重新导出草稿。"},
                {"导入规则", "编码识别指标；新增时编码、名称、类型必填。更新须保留导出的三列隐藏标识，禁止修改编码；本期不导入结果绑定或参考资源。"},
                {"空值规则", "空白单元格清空对应可选内容；统计周期、小数位、显示方式为空时采用默认值。不要删除表头。"},
                {"发布规则", "口径可不完整；仅保存草稿，基础资料直接更新，不自动发布、启用或运行任务。既有绑定和参考资料保留。"},
                {"冲突处理", "导出后系统资料或草稿发生变化时，重新导出并整理；预览后文件或系统发生变化时，重新预览。"},
                {"批量限制", "只支持 .xlsx，最大10MB、1000行；同一文件编码不能重复；有错误时整批不导入。"},
                {"目录", "目录必须预先存在，按完整路径匹配；留空会归入未分类，不会自动创建目录。"},
                {"显示说明", "比值百分比：0.85解释为85%；百分数值：85解释为85%。当前只登记显示规则。"},
                {"填写说明", "下面按字段提供说明与假设示例，请采用实际业务认可的口径。"}
        };
        for (int i = 0; i < notes.length; i++) {
            Row row = sheet.createRow(i);
            row.setHeightInPoints(i < 2 ? 24 : 48);
            put(row, 0, notes[i][0], header);
            put(row, 1, notes[i][1], text);
        }
        int start = notes.length + 1;
        Row headings = sheet.createRow(start);
        put(headings, 0, "字段", header); put(headings, 1, "填写要求", header); put(headings, 2, "示例", header);
        for (int i = 0; i < COLUMNS.size(); i++) {
            Row row = sheet.createRow(start + 1 + i);
            row.setHeightInPoints(48);
            Column c = COLUMNS.get(i);
            put(row, 0, c.title(), text); put(row, 1, c.description(), text); put(row, 2, c.example(), text);
        }
        sheet.setColumnWidth(0, 25 * 256); sheet.setColumnWidth(1, 85 * 256); sheet.setColumnWidth(2, 60 * 256);
    }

    public ParsedWorkbook read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw bad("请选择指标 Excel 文件");
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "指标 Excel 不能超过10MB");
        if (!Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw bad("只支持 .xlsx 文件");
        try {
            byte[] bytes = file.getBytes();
            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                DataFormatter formatter = new DataFormatter(Locale.ROOT);
                Sheet info = workbook.getSheet("填写说明");
                if (info == null || !MARKER.equals(cellText(info.getRow(0), 1, formatter))) throw bad("请使用系统指标模板或草稿导出文件");
                if (!"DRAFT".equals(cellText(info.getRow(1), 1, formatter))) throw bad("发布口径查阅版不可回导，请导出草稿后编辑");
                Sheet sheet = workbook.getSheet("指标");
                if (sheet == null) throw bad("缺少“指标”工作表");
                Map<String, Integer> positions = new HashMap<>();
                Row header = sheet.getRow(0);
                if (header == null || header.getLastCellNum() > 100) throw bad("指标表头无效");
                for (int i = 0; i < header.getLastCellNum(); i++) {
                    String title = cellText(header, i, formatter);
                    if (!title.isEmpty() && positions.putIfAbsent(title, i) != null) throw bad("重复表头：" + title);
                }
                for (Column c : COLUMNS) if (!positions.containsKey(c.title())) throw bad("缺少列：" + c.title());
                List<SheetRow> rows = new ArrayList<>();
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue;
                    Map<String, String> values = new LinkedHashMap<>();
                    List<MetricImportIssueResponse> issues = new ArrayList<>();
                    for (Column c : COLUMNS) {
                        Cell cell = row.getCell(positions.get(c.title()));
                        boolean invalid = cell != null && (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR);
                        if (invalid) issues.add(new MetricImportIssueResponse(row.getRowNum() + 1, c.title(), "不支持公式或错误单元格，请粘贴为值", true));
                        // Validate the actual integer, even if Excel's display format rounds a decimal.
                        String value = !invalid && c.key().equals("decimalPlaces") && cell != null && cell.getCellType() == CellType.NUMERIC
                                ? NumberToTextConverter.toText(cell.getNumericCellValue())
                                : invalid ? "" : cellText(row, positions.get(c.title()), formatter);
                        values.put(c.key(), value);
                    }
                    if (values.values().stream().allMatch(String::isEmpty) && issues.isEmpty()) continue;
                    if (rows.size() >= MAX_ROWS) throw bad("单次最多导入1000行指标");
                    rows.add(new SheetRow(row.getRowNum() + 1, values, issues));
                }
                if (rows.isEmpty()) throw bad("指标工作表没有可导入的数据");
                return new ParsedWorkbook(digest(bytes), List.copyOf(rows));
            }
        } catch (ResponseStatusException e) { throw e; }
        catch (IOException | RuntimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取 Excel，请使用未损坏的系统模板", e); }
    }

    public MetricExcelFile errors(MetricImportPreviewResponse preview) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("错误清单");
            CellStyle header = headerStyle(workbook);
            CellStyle text = workbook.createCellStyle(); text.setWrapText(true); text.setVerticalAlignment(VerticalAlignment.TOP);
            Row first = sheet.createRow(0);
            String[] headings = {"Excel行号", "指标编码", "列名", "级别", "问题说明"};
            for (int i = 0; i < headings.length; i++) put(first, i, headings[i], header);
            int next = 1;
            for (var row : preview.rows()) for (var issue : row.issues()) {
                Row target = sheet.createRow(next++); target.setHeightInPoints(44);
                target.createCell(0).setCellValue(issue.rowNumber());
                put(target, 1, row.code(), text); put(target, 2, issue.column(), text);
                put(target, 3, issue.blocking() ? "错误" : "发布前提示", text); put(target, 4, issue.message(), text);
            }
            int[] widths = {14, 28, 26, 18, 90};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
            sheet.createFreezePane(0, 1); workbook.write(output);
            return new MetricExcelFile("DataScalpel-指标导入错误清单.xlsx", output.toByteArray());
        } catch (IOException e) { throw new IllegalStateException("生成错误清单失败", e); }
    }

    static String label(String key) { return COLUMNS.stream().filter(c -> c.key().equals(key)).map(Column::title).findFirst().orElse(key); }
    static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String cellText(Row row, int index, DataFormatter formatter) {
        if (row == null || row.getCell(index) == null) return "";
        Cell cell = row.getCell(index);
        if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR) throw bad("表头及格式标识不支持公式或错误单元格");
        return formatter.formatCellValue(cell).trim();
    }
    private static void put(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index, CellType.STRING);
        cell.setCellValue(Objects.toString(value, "")); cell.setCellStyle(style);
    }
    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.INDIGO.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex()); style.setFont(font);
        style.setWrapText(true); style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    private static void dropdown(Sheet sheet, int column, String... values) {
        var helper = sheet.getDataValidationHelper();
        var validation = helper.createValidation(helper.createExplicitListConstraint(values), new CellRangeAddressList(1, MAX_ROWS, column, column));
        validation.setShowErrorBox(true); validation.createErrorBox("选项无效", "请使用下拉列表中的选项"); sheet.addValidationData(validation);
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}

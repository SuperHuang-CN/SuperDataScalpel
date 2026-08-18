package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayer;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.repository.ModelWarehouseLayerRepository;
import cn.superhuang.data.scalpel.business.model.web.request.ExportModelMetadataRequest;
import cn.superhuang.data.scalpel.business.model.web.response.ModelMetadataImportFieldResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ModelMetadataImportModelResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ModelMetadataImportPreviewResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ModelWarehouseLayerSummaryResponse;
import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionary;
import cn.superhuang.data.scalpel.business.standard.repository.StandardDictionaryRepository;
import cn.superhuang.data.scalpel.business.standard.service.StandardDictionaryValueSupport;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionarySummaryResponse;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.model.PhysicalTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingResult;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ModelMetadataExcelService {

    private static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String FILE_MARKER = "DATASCALPEL_MODEL_METADATA";
    private static final int FORMAT_VERSION = 5;
    private static final int V4_FORMAT_VERSION = 4;
    private static final int V3_FORMAT_VERSION = 3;
    private static final int V2_FORMAT_VERSION = 2;
    private static final int LEGACY_FORMAT_VERSION = 1;
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_MODELS = 200;
    private static final int MAX_FIELDS_PER_MODEL = 500;
    private static final int MAX_TOTAL_FIELDS = 20_000;
    private static final Pattern MODEL_CODE = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Pattern WAREHOUSE_LAYER_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");
    private static final Pattern TABLE_NAME = Pattern.compile("[a-z][a-z0-9_]{0,127}");
    private static final Pattern FIELD_CODE = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DataFormatter DATA_FORMATTER = new DataFormatter(Locale.ROOT);

    private static final List<String> LEGACY_MODEL_HEADERS = List.of(
            "模型编码*", "模型名称*", "目标物理表名*", "模型说明", "ClickHouse排序键"
    );
    private static final List<String> V3_V4_MODEL_HEADERS = List.of(
            "模型编码*", "模型名称*", "数仓分层编码", "数仓分层名称",
            "目标物理表名*", "模型说明", "ClickHouse排序键"
    );
    private static final List<String> MODEL_HEADERS = List.of(
            "模型编码*", "模型名称*", "数仓分层编码", "数仓分层名称", "模型目录路径",
            "目标物理表名*", "模型说明", "ClickHouse排序键"
    );
    private static final List<String> V1_FIELD_HEADERS = List.of(
            "模型编码*", "字段编码*", "字段名称*", "平台字段类型*", "长度", "精度", "小数位",
            "是否可空*", "是否主键*", "排序值*", "字段说明"
    );
    private static final List<String> V2_V3_FIELD_HEADERS = List.of(
            "模型编码*", "字段编码*", "字段名称*", "平台字段类型*", "长度", "精度", "小数位",
            "几何类型", "CRS Authority", "CRS Code", "坐标维度",
            "是否可空*", "是否主键*", "排序值*", "字段说明"
    );
    private static final List<String> FIELD_HEADERS = List.of(
            "模型编码*", "字段编码*", "字段名称*", "平台字段类型*", "长度", "精度", "小数位",
            "几何类型", "CRS Authority", "CRS Code", "坐标维度",
            "是否可空*", "是否主键*", "排序值*", "字段说明", "码表编码"
    );

    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DirectoryService directoryService;
    private final ModelWarehouseLayerRepository warehouseLayerRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DialectRegistry dialectRegistry;
    private final ModelPhysicalTablePort physicalTablePort;
    private final StandardDictionaryRepository standardDictionaryRepository;
    private final StandardDictionaryValueSupport standardDictionaryValueSupport;

    public ModelMetadataExcelService(
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            DirectoryService directoryService,
            ModelWarehouseLayerRepository warehouseLayerRepository,
            DataSourceRepository dataSourceRepository,
            DialectRegistry dialectRegistry,
            ModelPhysicalTablePort physicalTablePort,
            StandardDictionaryRepository standardDictionaryRepository,
            StandardDictionaryValueSupport standardDictionaryValueSupport
    ) {
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.directoryService = directoryService;
        this.warehouseLayerRepository = warehouseLayerRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.dialectRegistry = dialectRegistry;
        this.physicalTablePort = physicalTablePort;
        this.standardDictionaryRepository = standardDictionaryRepository;
        this.standardDictionaryValueSupport = standardDictionaryValueSupport;
    }

    public String contentType() {
        return CONTENT_TYPE;
    }

    public ModelMetadataExcelFile template() {
        return new ModelMetadataExcelFile("DataScalpel-模型元数据导入模板.xlsx", writeWorkbook(List.of()));
    }

    @Transactional(readOnly = true)
    public ModelMetadataExcelFile export(ExportModelMetadataRequest request) {
        LinkedHashSet<UUID> requestedIds = new LinkedHashSet<>(request.modelIds());
        if (requestedIds.size() != request.modelIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "导出模型不能重复选择");
        }
        Map<UUID, DataModel> modelsById = modelRepository.findAllById(requestedIds).stream()
                .collect(Collectors.toMap(DataModel::getId, Function.identity()));
        List<UUID> missing = requestedIds.stream().filter(id -> !modelsById.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "部分导出模型不存在：" + missing);
        }
        List<DataModel> models = requestedIds.stream().map(modelsById::get).toList();
        List<String> externalCodes = models.stream()
                .filter(model -> model.getPhysicalTableMode() == PhysicalTableMode.EXTERNAL)
                .map(DataModel::getCode)
                .toList();
        if (!externalCodes.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "EXTERNAL 模型暂不支持导出：" + String.join("、", externalCodes)
            );
        }
        Map<UUID, List<DataModelField>> fieldsByModel = fieldRepository
                .findAllByModelIdInOrderByModelAndSort(requestedIds).stream()
                .collect(Collectors.groupingBy(
                        DataModelField::getModelId, LinkedHashMap::new, Collectors.toList()
                ));
        Map<UUID, ModelWarehouseLayer> layersById = warehouseLayerRepository.findAllById(
                models.stream().map(DataModel::getWarehouseLayerId).filter(Objects::nonNull).distinct().toList()
        ).stream().collect(Collectors.toMap(ModelWarehouseLayer::getId, Function.identity()));
        Map<UUID, StandardDictionary> dictionariesById = standardDictionaryRepository.findAllById(
                fieldsByModel.values().stream()
                        .flatMap(Collection::stream)
                        .map(DataModelField::getStandardDictionaryId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()
        ).stream().collect(Collectors.toMap(StandardDictionary::getId, Function.identity()));
        DirectoryService.DirectoryPathIndex directoryPaths = directoryService.pathIndex(DirectoryScope.MODEL);
        Map<UUID, String> directoryPathsByModelId = new HashMap<>();
        for (DataModel model : models) {
            DirectoryService.DirectoryPathResolution resolution = directoryPaths.resolve(model.getDirectoryId());
            if (!resolution.resolved()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "模型“" + model.getCode() + "”的目录路径无法导出：" + resolution.issue()
                );
            }
            directoryPathsByModelId.put(model.getId(), resolution.path());
        }
        List<ExportModel> exports = models.stream()
                .map(model -> new ExportModel(
                        model,
                        layersById.get(model.getWarehouseLayerId()),
                        directoryPathsByModelId.get(model.getId()),
                        fieldsByModel.getOrDefault(model.getId(), List.of()),
                        dictionariesById
                ))
                .toList();
        String fileName = "DataScalpel-模型元数据-" + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".xlsx";
        return new ModelMetadataExcelFile(fileName, writeWorkbook(exports));
    }

    public ModelMetadataImportPreviewResponse preview(UUID targetStorageDataSourceId, MultipartFile file) {
        DataSource target = requireTargetStorage(targetStorageDataSourceId);
        ParsedWorkbook parsed = readWorkbook(file);
        validateParsedWorkbook(parsed, target);
        List<ModelMetadataImportModelResponse> models = parsed.models.stream()
                .map(MutableModel::toResponse)
                .toList();
        boolean importable = parsed.issues.isEmpty() && !models.isEmpty()
                && models.stream().allMatch(ModelMetadataImportModelResponse::importable);
        return new ModelMetadataImportPreviewResponse(
                originalFileName(file), parsed.formatVersion, importable, parsed.issues, models
        );
    }

    private byte[] writeWorkbook(List<ExportModel> models) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            WorkbookStyles styles = styles(workbook);
            createInstructionsSheet(workbook, styles);
            Sheet modelSheet = createDataSheet(
                    workbook,
                    "模型",
                    MODEL_HEADERS,
                    styles,
                    new int[]{20, 24, 18, 24, 34, 24, 42, 28}
            );
            Sheet fieldSheet = createDataSheet(
                    workbook, "字段", FIELD_HEADERS, styles,
                    new int[]{20, 20, 24, 18, 12, 12, 12, 22, 18, 14, 14, 14, 14, 12, 42, 22}
            );
            addListValidation(fieldSheet, 3, enumNames(PlatformDataType.values()));
            addListValidation(fieldSheet, 7, enumNames(GeometryKind.values()));
            addListValidation(fieldSheet, 8, new String[]{"EPSG"});
            addListValidation(fieldSheet, 10, new String[]{"XY"});
            addListValidation(fieldSheet, 11, new String[]{"是", "否"});
            addListValidation(fieldSheet, 12, new String[]{"是", "否"});

            int modelRowIndex = 1;
            int fieldRowIndex = 1;
            for (ExportModel export : models) {
                DataModel model = export.model();
                ModelWarehouseLayer layer = export.warehouseLayer();
                Row row = modelSheet.createRow(modelRowIndex++);
                writeText(row, 0, model.getCode(), styles.text());
                writeText(row, 1, model.getName(), styles.text());
                writeText(row, 2, layer == null ? null : layer.getCode(), styles.text());
                writeText(row, 3, layer == null ? null : layer.getName(), styles.text());
                writeText(row, 4, export.directoryPath(), styles.text());
                writeText(row, 5, model.getPhysicalTableName(), styles.text());
                writeText(row, 6, model.getDescription(), styles.text());
                writeText(row, 7, String.join(",", model.getClickHouseOrderByColumns()), styles.text());
                for (DataModelField field : export.fields()) {
                    Row fieldRow = fieldSheet.createRow(fieldRowIndex++);
                    writeText(fieldRow, 0, model.getCode(), styles.text());
                    writeText(fieldRow, 1, field.getCode(), styles.text());
                    writeText(fieldRow, 2, field.getName(), styles.text());
                    writeText(fieldRow, 3, field.getFieldType().name(), styles.text());
                    writeInteger(fieldRow, 4, field.getLength(), styles.integer());
                    writeInteger(fieldRow, 5, field.getPrecision(), styles.integer());
                    writeInteger(fieldRow, 6, field.getScale(), styles.integer());
                    GeometryTypeDefinition geometry = field.getGeometry();
                    writeText(fieldRow, 7, geometry == null ? null : geometry.kind().name(), styles.text());
                    writeText(fieldRow, 8, geometry == null ? null : geometry.crs().authority(), styles.text());
                    writeInteger(fieldRow, 9, geometry == null ? null : geometry.crs().code(), styles.integer());
                    writeText(fieldRow, 10, geometry == null ? null : geometry.dimension().name(), styles.text());
                    writeText(fieldRow, 11, field.isNullable() ? "是" : "否", styles.text());
                    writeText(fieldRow, 12, field.isPrimaryKey() ? "是" : "否", styles.text());
                    writeInteger(fieldRow, 13, field.getSortOrder(), styles.integer());
                    writeText(fieldRow, 14, field.getDescription(), styles.text());
                    StandardDictionary dictionary = export.dictionariesById()
                            .get(field.getStandardDictionaryId());
                    writeText(fieldRow, 15, dictionary == null ? null : dictionary.getCode(), styles.text());
                }
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成模型元数据 Excel 失败", exception);
        }
    }

    private static void createInstructionsSheet(XSSFWorkbook workbook, WorkbookStyles styles) {
        Sheet sheet = workbook.createSheet("说明");
        String[][] rows = {
                {"文件标识", FILE_MARKER},
                {"格式版本", String.valueOf(FORMAT_VERSION)},
                {"用途", "仅导入导出模型、模型目录路径、数仓分层编码和字段元数据，不包含数据源连接、状态、UUID 或物理表数据。"},
                {"导入结果", "整份文件原子创建为 MANAGED + DRAFT；任一模型失败时全部回滚；不会自动创建物理表。"},
                {"填写规则", "带 * 的列必填；编码只允许小写字母、数字和下划线，且必须以小写字母开头。"},
                {"模型目录路径", "使用 / 分隔已有 MODEL 目录，例如 DW/水库工程；逐级忽略大小写匹配；空值表示未分类；导入不会自动创建目录。"},
                {"数仓分层", "按数仓分层编码匹配当前系统中的启用分层；名称仅用于导出展示，导入时不参与匹配。"},
                {"码表", "字段可按码表编码绑定当前系统中的启用码表；码表必须与字段类型兼容，旧版文件缺少该列时按未绑定处理。"},
                {"字段类型", String.join("、", enumNames(PlatformDataType.values()))},
                {"STRING", "长度可选；填写时必须为正整数。"},
                {"DECIMAL", "精度必填且为 1～38，小数位必填且为 0～精度。"},
                {"GEOMETRY", "几何类型、CRS Authority、CRS Code、坐标维度必填；第一版仅支持 EPSG 正整数编码和 XY。"},
                {"布尔值", "是否可空、是否主键请填写“是”或“否”，导入也兼容 TRUE/FALSE、1/0。"},
                {"ClickHouse排序键", "多个字段编码使用英文逗号分隔；导入到非 ClickHouse 时忽略并提示。"}
        };
        for (int index = 0; index < rows.length; index++) {
            Row row = sheet.createRow(index);
            writeText(row, 0, rows[index][0], styles.instructionLabel());
            writeText(row, 1, rows[index][1], styles.instructionValue());
        }
        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 110 * 256);
    }

    private static Sheet createDataSheet(
            XSSFWorkbook workbook,
            String name,
            List<String> headers,
            WorkbookStyles styles,
            int[] widths
    ) {
        Sheet sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        header.setHeightInPoints(24);
        for (int index = 0; index < headers.size(); index++) {
            writeText(header, index, headers.get(index), headers.get(index).endsWith("*") ? styles.requiredHeader() : styles.header());
            sheet.setColumnWidth(index, widths[index] * 256);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.size() - 1));
        return sheet;
    }

    private static void addListValidation(Sheet sheet, int column, String[] values) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(values);
        DataValidation validation = helper.createValidation(
                constraint, new CellRangeAddressList(1, MAX_TOTAL_FIELDS, column, column)
        );
        validation.setShowErrorBox(true);
        validation.setSuppressDropDownArrow(true);
        sheet.addValidationData(validation);
    }

    private ParsedWorkbook readWorkbook(MultipartFile file) {
        validateFile(file);
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            int formatVersion = validateWorkbookMarker(workbook);
            Sheet modelSheet = requireSheet(workbook, "模型");
            Sheet fieldSheet = requireSheet(workbook, "字段");
            requireHeaders(
                    modelSheet,
                    formatVersion >= FORMAT_VERSION
                            ? MODEL_HEADERS
                            : formatVersion >= V3_FORMAT_VERSION ? V3_V4_MODEL_HEADERS : LEGACY_MODEL_HEADERS
            );
            requireHeaders(
                    fieldSheet,
                    formatVersion >= V4_FORMAT_VERSION
                            ? FIELD_HEADERS
                            : formatVersion == LEGACY_FORMAT_VERSION ? V1_FIELD_HEADERS : V2_V3_FIELD_HEADERS
            );
            ParsedWorkbook parsed = new ParsedWorkbook(formatVersion);
            readModels(modelSheet, parsed, formatVersion);
            readFields(fieldSheet, parsed, formatVersion);
            associateFields(parsed);
            return parsed;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取模型元数据 Excel，请使用系统模板", exception);
        }
    }

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要导入的 Excel 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "模型元数据 Excel 不能超过 10 MB");
        }
        String fileName = originalFileName(file).toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".xlsx")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只支持 .xlsx 模型元数据文件");
        }
    }

    private static int validateWorkbookMarker(Workbook workbook) {
        Sheet instructions = requireSheet(workbook, "说明");
        String marker = formattedCell(instructions.getRow(0), 1);
        String version = formattedCell(instructions.getRow(1), 1);
        if (!FILE_MARKER.equals(marker)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel 文件标识不正确，请使用系统模板");
        }
        int parsedVersion;
        try {
            parsedVersion = Integer.parseInt(version);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂不支持该 Excel 格式版本：" + version);
        }
        if (parsedVersion != LEGACY_FORMAT_VERSION
                && parsedVersion != V2_FORMAT_VERSION
                && parsedVersion != V3_FORMAT_VERSION
                && parsedVersion != V4_FORMAT_VERSION
                && parsedVersion != FORMAT_VERSION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂不支持该 Excel 格式版本：" + version);
        }
        return parsedVersion;
    }

    private static Sheet requireSheet(Workbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel 缺少工作表：“" + name + "”");
        }
        return sheet;
    }

    private static void requireHeaders(Sheet sheet, List<String> expected) {
        Row header = sheet.getRow(0);
        for (int index = 0; index < expected.size(); index++) {
            String actual = formattedCell(header, index);
            if (!expected.get(index).equals(actual)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        sheet.getSheetName() + "工作表第 " + (index + 1) + " 列应为“" + expected.get(index) + "”"
                );
            }
        }
    }

    private static void readModels(Sheet sheet, ParsedWorkbook parsed, int formatVersion) {
        int columnCount = formatVersion >= FORMAT_VERSION
                ? MODEL_HEADERS.size()
                : formatVersion >= V3_FORMAT_VERSION ? V3_V4_MODEL_HEADERS.size() : LEGACY_MODEL_HEADERS.size();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (blankRow(row, columnCount)) {
                continue;
            }
            if (parsed.models.size() >= MAX_MODELS) {
                parsed.issues.add("单个文件最多导入 " + MAX_MODELS + " 个模型");
                break;
            }
            int rowNumber = rowIndex + 1;
            MutableModel model = new MutableModel("model:" + rowNumber, rowNumber);
            model.sourceCode = normalizedText(cellText(row, 0, model.issues, "模型", rowNumber));
            model.code = identifier(model.sourceCode, MODEL_CODE, "模型编码", model.issues);
            model.name = requiredText(cellText(row, 1, model.issues, "模型", rowNumber), "模型名称", 100, model.issues);
            int physicalTableColumn = 2;
            int descriptionColumn = 3;
            int orderByColumn = 4;
            if (formatVersion >= V3_FORMAT_VERSION) {
                model.warehouseLayerCode = normalizedWarehouseLayerCode(
                        cellText(row, 2, model.issues, "模型", rowNumber),
                        model.issues
                );
                cellText(row, 3, model.issues, "模型", rowNumber);
                physicalTableColumn = 4;
                descriptionColumn = 5;
                orderByColumn = 6;
            }
            if (formatVersion >= FORMAT_VERSION) {
                model.directoryPath = optionalText(
                        cellText(row, 4, model.issues, "模型", rowNumber),
                        "模型目录路径",
                        1000,
                        model.issues
                );
                physicalTableColumn = 5;
                descriptionColumn = 6;
                orderByColumn = 7;
            }
            String physicalName = normalizedText(cellText(
                    row, physicalTableColumn, model.issues, "模型", rowNumber
            ));
            model.physicalTableName = identifier(physicalName, TABLE_NAME, "目标物理表名", model.issues);
            model.description = optionalText(
                    cellText(row, descriptionColumn, model.issues, "模型", rowNumber),
                    "模型说明",
                    1000,
                    model.issues
            );
            model.clickHouseOrderByColumns = parseOrderBy(
                    cellText(row, orderByColumn, model.issues, "模型", rowNumber),
                    model.issues
            );
            parsed.models.add(model);
        }
        if (parsed.models.isEmpty()) {
            parsed.issues.add("模型工作表中至少需要填写一个模型");
        }
    }

    private static void readFields(Sheet sheet, ParsedWorkbook parsed, int formatVersion) {
        int columnCount = formatVersion >= V4_FORMAT_VERSION
                ? FIELD_HEADERS.size()
                : formatVersion == LEGACY_FORMAT_VERSION ? V1_FIELD_HEADERS.size() : V2_V3_FIELD_HEADERS.size();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (blankRow(row, columnCount)) {
                continue;
            }
            if (parsed.fields.size() >= MAX_TOTAL_FIELDS) {
                parsed.issues.add("单个文件最多导入 " + MAX_TOTAL_FIELDS + " 个字段");
                break;
            }
            int rowNumber = rowIndex + 1;
            MutableField field = new MutableField("field:" + rowNumber, rowNumber);
            field.modelReference = normalizedText(cellText(row, 0, field.issues, "字段", rowNumber));
            if (field.modelReference.isEmpty()) {
                field.issues.add("模型编码不能为空");
            }
            field.code = identifier(
                    normalizedText(cellText(row, 1, field.issues, "字段", rowNumber)),
                    FIELD_CODE, "字段编码", field.issues
            );
            field.name = requiredText(cellText(row, 2, field.issues, "字段", rowNumber), "字段名称", 100, field.issues);
            field.fieldType = platformType(cellText(row, 3, field.issues, "字段", rowNumber), field.issues);
            field.length = integer(cellText(row, 4, field.issues, "字段", rowNumber), "长度", false, field.issues);
            field.precision = integer(cellText(row, 5, field.issues, "字段", rowNumber), "精度", false, field.issues);
            field.scale = integer(cellText(row, 6, field.issues, "字段", rowNumber), "小数位", false, field.issues);
            if (formatVersion >= V2_FORMAT_VERSION) {
                String kind = cellText(row, 7, field.issues, "字段", rowNumber);
                String authority = cellText(row, 8, field.issues, "字段", rowNumber);
                String code = cellText(row, 9, field.issues, "字段", rowNumber);
                String dimension = cellText(row, 10, field.issues, "字段", rowNumber);
                field.geometryParametersPresent = !kind.isBlank() || !authority.isBlank()
                        || !code.isBlank() || !dimension.isBlank();
                field.geometry = geometryDefinition(kind, authority, code, dimension, field.issues);
                field.nullable = booleanValue(cellText(row, 11, field.issues, "字段", rowNumber), "是否可空", field.issues);
                field.primaryKey = booleanValue(cellText(row, 12, field.issues, "字段", rowNumber), "是否主键", field.issues);
                field.sortOrder = integer(cellText(row, 13, field.issues, "字段", rowNumber), "排序值", true, field.issues);
                field.description = optionalText(cellText(row, 14, field.issues, "字段", rowNumber), "字段说明", 500, field.issues);
                if (formatVersion >= V4_FORMAT_VERSION) {
                    field.standardDictionaryCode = normalizedDictionaryCode(
                            cellText(row, 15, field.issues, "字段", rowNumber),
                            field.issues
                    );
                }
            } else {
                field.nullable = booleanValue(cellText(row, 7, field.issues, "字段", rowNumber), "是否可空", field.issues);
                field.primaryKey = booleanValue(cellText(row, 8, field.issues, "字段", rowNumber), "是否主键", field.issues);
                field.sortOrder = integer(cellText(row, 9, field.issues, "字段", rowNumber), "排序值", true, field.issues);
                field.description = optionalText(cellText(row, 10, field.issues, "字段", rowNumber), "字段说明", 500, field.issues);
            }
            validateFieldDefinition(field);
            parsed.fields.add(field);
        }
    }

    private static void associateFields(ParsedWorkbook parsed) {
        Map<String, List<MutableModel>> bySourceCode = parsed.models.stream()
                .collect(Collectors.groupingBy(model -> model.sourceCode, LinkedHashMap::new, Collectors.toList()));
        bySourceCode.forEach((sourceCode, models) -> {
            if (!sourceCode.isEmpty() && models.size() > 1) {
                parsed.issues.add("模型编码小写化后重复，无法确定字段归属：" + sourceCode);
                models.forEach(model -> {
                    model.code = "";
                    model.issues.add("模型编码小写化后重复：" + sourceCode);
                });
            }
        });
        for (MutableField field : parsed.fields) {
            List<MutableModel> candidates = bySourceCode.getOrDefault(field.modelReference, List.of());
            if (candidates.size() != 1) {
                parsed.issues.add("字段工作表第 " + field.rowNumber + " 行无法关联唯一模型：" + field.modelReference);
                continue;
            }
            candidates.getFirst().fields.add(field);
        }
    }

    private void validateParsedWorkbook(ParsedWorkbook parsed, DataSource target) {
        DatabaseDialect dialect = dialectRegistry.require(target.getType().name());
        JdbcConnectionConfig connection = target.getConnection().toJdbcConnectionConfig();
        String catalog = normalizeOptional(dialect.resolveCatalog(connection, null));
        String schema = normalizeOptional(dialect.resolveSchema(connection, null));
        DirectoryService.DirectoryPathIndex directoryPaths = directoryService.pathIndex(DirectoryScope.MODEL);
        if (parsed.formatVersion >= FORMAT_VERSION
                && parsed.models.stream().anyMatch(model -> !model.directoryPath.isBlank())) {
            parsed.issues.addAll(directoryPaths.issues());
        }

        Set<String> duplicatePhysicalNames = duplicateValues(
                parsed.models.stream().map(model -> model.physicalTableName).toList()
        );
        Map<String, ModelWarehouseLayer> layersByCode = warehouseLayerRepository.findAll().stream()
                .collect(Collectors.toMap(ModelWarehouseLayer::getCode, Function.identity()));
        Map<String, StandardDictionary> dictionariesByCode = standardDictionaryRepository.findAll().stream()
                .collect(Collectors.toMap(StandardDictionary::getCode, Function.identity()));
        for (MutableModel model : parsed.models) {
            resolveDirectory(model, parsed.formatVersion, directoryPaths);
            resolveWarehouseLayer(model, layersByCode);
            validateModelFields(model, dialect);
            resolveStandardDictionaries(model, dictionariesByCode);
            if (!model.code.isEmpty() && modelRepository.existsByCode(model.code)) {
                model.issues.add("模型编码已存在：" + model.code);
            }
            if (!model.physicalTableName.isEmpty() && duplicatePhysicalNames.contains(model.physicalTableName)) {
                model.issues.add("本次导入中目标物理表名重复：" + model.physicalTableName);
            }
            validateClickHouseOrderBy(model, target);
            if (!model.physicalTableName.isEmpty()) {
                validateTargetLocation(model, target, catalog, schema);
            }
        }
    }

    private static void resolveDirectory(
            MutableModel model,
            int formatVersion,
            DirectoryService.DirectoryPathIndex directoryPaths
    ) {
        if (formatVersion < FORMAT_VERSION) {
            model.warnings.add("旧版文件未定义模型目录，将导入未分类");
            model.directoryPath = "";
            model.directoryId = null;
            return;
        }
        DirectoryService.DirectoryPathResolution resolution = directoryPaths.resolve(model.directoryPath);
        if (!resolution.resolved()) {
            model.issues.add(resolution.issue());
            return;
        }
        model.directoryPath = resolution.path();
        model.directoryId = resolution.directoryId();
    }

    private static void resolveWarehouseLayer(
            MutableModel model,
            Map<String, ModelWarehouseLayer> layersByCode
    ) {
        if (model.warehouseLayerCode.isEmpty()) {
            return;
        }
        if (!WAREHOUSE_LAYER_CODE.matcher(model.warehouseLayerCode).matches()) {
            return;
        }
        ModelWarehouseLayer layer = layersByCode.get(model.warehouseLayerCode);
        if (layer == null) {
            model.issues.add("数仓分层不存在：" + model.warehouseLayerCode);
            return;
        }
        model.warehouseLayer = layer;
        if (!layer.isEnabled()) {
            model.issues.add("数仓分层已停用：" + model.warehouseLayerCode);
        }
    }

    private void resolveStandardDictionaries(
            MutableModel model,
            Map<String, StandardDictionary> dictionariesByCode
    ) {
        for (MutableField field : model.fields) {
            if (field.standardDictionaryCode.isEmpty()) {
                continue;
            }
            StandardDictionary dictionary = dictionariesByCode.get(field.standardDictionaryCode);
            if (dictionary == null) {
                field.issues.add("码表不存在：" + field.standardDictionaryCode);
                continue;
            }
            try {
                standardDictionaryValueSupport.validateAssignment(
                        dictionary.getId(),
                        null,
                        field.fieldType,
                        field.length,
                        field.precision,
                        field.scale
                );
                field.standardDictionary = dictionary;
            } catch (ResponseStatusException exception) {
                field.issues.add(exception.getReason());
            }
        }
    }

    private static void validateModelFields(MutableModel model, DatabaseDialect dialect) {
        if (model.fields.isEmpty()) {
            model.issues.add("模型至少需要一个字段");
            return;
        }
        if (model.fields.size() > MAX_FIELDS_PER_MODEL) {
            model.issues.add("单个模型最多导入 " + MAX_FIELDS_PER_MODEL + " 个字段");
        }
        Set<String> duplicates = duplicateValues(model.fields.stream().map(field -> field.code).toList());
        for (MutableField field : model.fields) {
            if (!field.code.isEmpty() && duplicates.contains(field.code)) {
                field.issues.add("字段编码小写化后重复：" + field.code);
                field.code = "";
            }
            if (field.fieldType != null && validPlatformDefinition(field)) {
                TypeMappingResult<PhysicalTypeDefinition> mapping = dialect.mapToPhysicalType(platformDefinition(field));
                if (!mapping.acceptable()) {
                    field.issues.add("字段类型无法安全映射到目标数据存储：" + mappingMessage(mapping));
                    field.fieldType = null;
                    field.length = null;
                    field.precision = null;
                    field.scale = null;
                    field.geometry = null;
                }
            }
        }
    }

    private void validateTargetLocation(
            MutableModel candidate,
            DataSource target,
            String catalog,
            String schema
    ) {
        if (modelRepository.existsByStorageDataSourceIdAndCatalogNameAndSchemaNameAndPhysicalTableName(
                target.getId(), catalog, schema, candidate.physicalTableName
        )) {
            candidate.issues.add("目标物理位置已被其他模型使用");
            return;
        }
        DataModel previewModel = DataModel.create(
                candidate.code.isEmpty() ? "metadata_import_preview" : candidate.code,
                candidate.name == null || candidate.name.isBlank() ? "元数据导入预览" : candidate.name,
                null,
                target.getId(),
                catalog,
                schema,
                candidate.physicalTableName,
                PhysicalTableMode.MANAGED,
                List.of(),
                null
        );
        try {
            ModelPhysicalTableInspection inspection = physicalTablePort.inspect(target, previewModel, List.of());
            if (inspection.state() != PhysicalTableState.NOT_FOUND) {
                candidate.issues.add("目标物理表必须不存在，当前状态为 " + inspection.state() + "：" + inspection.message());
            } else {
                validateSpatialRuntime(candidate, target, previewModel);
            }
        } catch (RuntimeException exception) {
            candidate.issues.add("无法确认目标物理表是否存在：" + exception.getMessage());
        }
    }

    private void validateSpatialRuntime(
            MutableModel candidate,
            DataSource target,
            DataModel previewModel
    ) {
        if (candidate.fields.stream().noneMatch(field -> field.fieldType == PlatformDataType.GEOMETRY)
                || candidate.fields.stream().anyMatch(field -> !field.issues.isEmpty() || field.fieldType == null)) {
            return;
        }
        UUID previewModelId = UUID.randomUUID();
        List<DataModelField> previewFields = candidate.fields.stream()
                .map(field -> DataModelField.create(
                        previewModelId,
                        field.code,
                        field.name,
                        field.fieldType,
                        field.length,
                        field.precision,
                        field.scale,
                        field.geometry,
                        Boolean.TRUE.equals(field.nullable),
                        field.fieldType == PlatformDataType.GEOMETRY ? false : Boolean.TRUE.equals(field.primaryKey),
                        field.sortOrder == null ? 0 : field.sortOrder,
                        field.description
                ))
                .toList();
        try {
            physicalTablePort.planCreate(target, previewModel, previewFields);
        } catch (RuntimeException exception) {
            candidate.issues.add("目标空间能力校验失败：" + exception.getMessage());
        }
    }

    private static void validateClickHouseOrderBy(MutableModel model, DataSource target) {
        if (model.clickHouseOrderByColumns.isEmpty()) {
            return;
        }
        if (target.getType() != DataSourceType.CLICKHOUSE) {
            model.warnings.add("ClickHouse 排序键已忽略：目标不是 ClickHouse 数据存储");
            model.clickHouseOrderByColumns = List.of();
            return;
        }
        Map<String, PlatformDataType> fieldTypesByCode = model.fields.stream()
                .filter(field -> !field.code.isEmpty() && field.fieldType != null)
                .collect(Collectors.toMap(
                        field -> field.code,
                        field -> field.fieldType,
                        (first, ignored) -> first
                ));
        Set<String> seen = new HashSet<>();
        for (String code : model.clickHouseOrderByColumns) {
            if (!seen.add(code)) {
                model.issues.add("ClickHouse 排序键字段重复：" + code);
            } else if (!fieldTypesByCode.containsKey(code)) {
                model.issues.add("ClickHouse 排序键引用了不存在的字段：" + code);
            } else if (fieldTypesByCode.get(code) == PlatformDataType.GEOMETRY) {
                model.issues.add("ClickHouse Geometry 字段不能作为排序键：" + code);
            }
        }
    }

    private DataSource requireTargetStorage(UUID id) {
        DataSource target = dataSourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标数据存储不存在"));
        if (!target.getType().isJdbc() || !target.getPurposes().contains(DataSourcePurpose.STORAGE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标必须是具有 STORAGE 用途的 JDBC 数据源");
        }
        if (!target.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目标数据存储已停用");
        }
        return target;
    }

    private static void validateFieldDefinition(MutableField field) {
        if (Boolean.TRUE.equals(field.primaryKey) && Boolean.TRUE.equals(field.nullable)) {
            field.issues.add("主键字段不能允许为空");
        }
        if (field.sortOrder != null && field.sortOrder < 0) {
            field.issues.add("排序值不能小于 0");
        }
        if (field.fieldType == null) {
            return;
        }
        if (field.fieldType == PlatformDataType.STRING) {
            rejectGeometryParameters(field);
            if (field.length != null && field.length < 1) {
                field.issues.add("STRING 长度必须为正整数");
            }
            if (field.precision != null || field.scale != null) {
                field.issues.add("STRING 不能填写精度或小数位");
            }
            return;
        }
        if (field.fieldType == PlatformDataType.DECIMAL) {
            rejectGeometryParameters(field);
            if (field.length != null) {
                field.issues.add("DECIMAL 不能填写长度");
            }
            if (field.precision == null || field.precision < 1 || field.precision > 38) {
                field.issues.add("DECIMAL 精度必须为 1～38");
            }
            if (field.scale == null || field.scale < 0
                    || field.precision == null || field.scale > field.precision) {
                field.issues.add("DECIMAL 小数位必须为 0～精度");
            }
            return;
        }
        if (field.fieldType == PlatformDataType.GEOMETRY) {
            if (field.length != null || field.precision != null || field.scale != null) {
                field.issues.add("GEOMETRY 不能填写长度、精度或小数位");
            }
            if (field.geometry == null) {
                field.issues.add("GEOMETRY 必须完整填写几何类型、CRS Authority、CRS Code 和坐标维度");
            }
            if (Boolean.TRUE.equals(field.primaryKey)) {
                field.issues.add("GEOMETRY 不能作为主键");
            }
            return;
        }
        rejectGeometryParameters(field);
        if (field.length != null || field.precision != null || field.scale != null) {
            field.issues.add(field.fieldType + " 不接受长度、精度或小数位参数");
        }
    }

    private static boolean validPlatformDefinition(MutableField field) {
        if (field.fieldType == PlatformDataType.STRING) {
            return field.length == null || field.length > 0;
        }
        if (field.fieldType == PlatformDataType.DECIMAL) {
            return field.precision != null && field.precision >= 1 && field.precision <= 38
                    && field.scale != null && field.scale >= 0 && field.scale <= field.precision;
        }
        if (field.fieldType == PlatformDataType.GEOMETRY) {
            return field.length == null && field.precision == null && field.scale == null
                    && field.geometry != null;
        }
        return field.length == null && field.precision == null && field.scale == null
                && !field.geometryParametersPresent;
    }

    private static PlatformTypeDefinition platformDefinition(MutableField field) {
        return switch (field.fieldType) {
            case STRING -> PlatformTypeDefinition.string(field.length);
            case DECIMAL -> PlatformTypeDefinition.decimal(field.precision, field.scale);
            case GEOMETRY -> PlatformTypeDefinition.geometry(field.geometry);
            default -> PlatformTypeDefinition.of(field.fieldType);
        };
    }

    private static void rejectGeometryParameters(MutableField field) {
        if (field.geometryParametersPresent) {
            field.issues.add("只有 GEOMETRY 字段可以填写空间参数");
        }
    }

    private static GeometryTypeDefinition geometryDefinition(
            String kindValue,
            String authorityValue,
            String codeValue,
            String dimensionValue,
            List<String> issues
    ) {
        if ((kindValue == null || kindValue.isBlank())
                && (authorityValue == null || authorityValue.isBlank())
                && (codeValue == null || codeValue.isBlank())
                && (dimensionValue == null || dimensionValue.isBlank())) {
            return null;
        }
        GeometryKind kind = enumValue(kindValue, GeometryKind.class, "几何类型", issues);
        CoordinateDimension dimension = enumValue(
                dimensionValue, CoordinateDimension.class, "坐标维度", issues
        );
        String authority = authorityValue == null ? "" : authorityValue.trim().toUpperCase(Locale.ROOT);
        if (!"EPSG".equals(authority)) {
            issues.add("CRS Authority 第一版只支持 EPSG");
        }
        Integer code = integer(codeValue, "CRS Code", true, issues);
        if (code != null && code < 1) {
            issues.add("CRS Code 必须为正整数");
        }
        if (dimension != null && dimension != CoordinateDimension.XY) {
            issues.add("坐标维度第一版只支持 XY");
        }
        if (kind == null || !"EPSG".equals(authority) || code == null || code < 1
                || dimension != CoordinateDimension.XY) {
            return null;
        }
        return new GeometryTypeDefinition(kind, CrsReference.epsg(code), CoordinateDimension.XY);
    }

    private static <E extends Enum<E>> E enumValue(
            String value,
            Class<E> enumType,
            String label,
            List<String> issues
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            issues.add("不支持的" + label + "：" + value.trim());
            return null;
        }
    }

    private static String mappingMessage(TypeMappingResult<?> mapping) {
        return mapping.message() == null ? mapping.quality().name() : mapping.message();
    }

    private static Set<String> duplicateValues(Collection<String> values) {
        Map<String, Integer> counts = new HashMap<>();
        values.stream().filter(value -> value != null && !value.isEmpty())
                .forEach(value -> counts.merge(value, 1, Integer::sum));
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static String cellText(Row row, int index, List<String> issues, String sheet, int rowNumber) {
        Cell cell = row == null ? null : row.getCell(index);
        if (cell != null && cell.getCellType() == CellType.FORMULA) {
            issues.add(sheet + "工作表第 " + rowNumber + " 行第 " + (index + 1) + " 列不允许使用公式");
            return "";
        }
        return formattedCell(row, index);
    }

    private static String formattedCell(Row row, int index) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(index);
        return cell == null ? "" : DATA_FORMATTER.formatCellValue(cell).trim();
    }

    private static boolean blankRow(Row row, int columns) {
        for (int index = 0; index < columns; index++) {
            if (!formattedCell(row, index).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String identifier(String value, Pattern pattern, String label, List<String> issues) {
        if (value.isEmpty()) {
            issues.add(label + "不能为空");
            return "";
        }
        if (!pattern.matcher(value).matches()) {
            issues.add(label + "小写化后仍不合法：" + value);
            return "";
        }
        return value;
    }

    private static String requiredText(String value, String label, int maximum, List<String> issues) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            issues.add(label + "不能为空");
            return "";
        }
        if (normalized.length() > maximum) {
            issues.add(label + "不能超过 " + maximum + " 个字符");
        }
        return normalized;
    }

    private static String optionalText(String value, String label, int maximum, List<String> issues) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maximum) {
            issues.add(label + "不能超过 " + maximum + " 个字符");
        }
        return normalized;
    }

    private static String normalizedText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static List<String> parseOrderBy(String value, List<String> issues) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : value.split(",", -1)) {
            String code = normalizedText(part);
            if (code.isEmpty() || !FIELD_CODE.matcher(code).matches()) {
                issues.add("ClickHouse 排序键字段编码不合法：" + part.trim());
            } else {
                result.add(code);
            }
        }
        return List.copyOf(result);
    }

    private static PlatformDataType platformType(String value, List<String> issues) {
        if (value == null || value.isBlank()) {
            issues.add("平台字段类型不能为空");
            return null;
        }
        try {
            return PlatformDataType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            issues.add("不支持的平台字段类型：" + value.trim());
            return null;
        }
    }

    private static Integer integer(String value, String label, boolean required, List<String> issues) {
        if (value == null || value.isBlank()) {
            if (required) {
                issues.add(label + "不能为空");
            }
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "")).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            issues.add(label + "必须是整数：" + value);
            return null;
        }
    }

    private static Boolean booleanValue(String value, String label, List<String> issues) {
        if (value == null || value.isBlank()) {
            issues.add(label + "不能为空");
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "是", "true", "1", "yes", "y" -> true;
            case "否", "false", "0", "no", "n" -> false;
            default -> {
                issues.add(label + "只能填写是或否：" + value);
                yield null;
            }
        };
    }

    private static void writeText(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index, CellType.STRING);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void writeInteger(Row row, int index, Integer value, CellStyle style) {
        Cell cell = row.createCell(index);
        if (value != null) {
            cell.setCellValue(value);
        } else {
            cell.setBlank();
        }
        cell.setCellStyle(style);
    }

    private static String[] enumNames(Enum<?>[] values) {
        String[] names = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            names[index] = values[index].name();
        }
        return names;
    }

    private static String originalFileName(MultipartFile file) {
        String value = file.getOriginalFilename();
        return value == null || value.isBlank() ? "模型元数据.xlsx" : value.trim();
    }

    private static String normalizedWarehouseLayerCode(String value, List<String> issues) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.isEmpty() && !WAREHOUSE_LAYER_CODE.matcher(normalized).matches()) {
            issues.add("数仓分层编码只能包含字母、数字和下划线，且必须以字母开头，最多 32 个字符");
        }
        return normalized;
    }

    private static String normalizedDictionaryCode(String value, List<String> issues) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.isEmpty() && !Pattern.compile("[A-Z][A-Z0-9_]{0,63}").matcher(normalized).matches()) {
            issues.add("码表编码只能包含字母、数字和下划线，且必须以字母开头，最多 64 个字符");
        }
        return normalized;
    }

    private static WorkbookStyles styles(XSSFWorkbook workbook) {
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());

        CellStyle header = workbook.createCellStyle();
        header.setFont(headerFont);
        header.setFillForegroundColor(IndexedColors.BLUE_GREY.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        borders(header);

        CellStyle requiredHeader = workbook.createCellStyle();
        requiredHeader.cloneStyleFrom(header);
        requiredHeader.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());

        CellStyle text = workbook.createCellStyle();
        text.setVerticalAlignment(VerticalAlignment.TOP);
        text.setWrapText(true);
        borders(text);

        CellStyle integer = workbook.createCellStyle();
        integer.cloneStyleFrom(text);
        integer.setDataFormat(workbook.createDataFormat().getFormat("0"));

        CellStyle instructionLabel = workbook.createCellStyle();
        instructionLabel.cloneStyleFrom(header);
        instructionLabel.setAlignment(HorizontalAlignment.LEFT);

        CellStyle instructionValue = workbook.createCellStyle();
        instructionValue.cloneStyleFrom(text);

        return new WorkbookStyles(header, requiredHeader, text, integer, instructionLabel, instructionValue);
    }

    private static void borders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
    }

    private record WorkbookStyles(
            CellStyle header,
            CellStyle requiredHeader,
            CellStyle text,
            CellStyle integer,
            CellStyle instructionLabel,
            CellStyle instructionValue
    ) {
    }

    private record ExportModel(
            DataModel model,
            ModelWarehouseLayer warehouseLayer,
            String directoryPath,
            List<DataModelField> fields,
            Map<UUID, StandardDictionary> dictionariesById
    ) {
    }

    private static final class ParsedWorkbook {
        private final int formatVersion;
        private final List<MutableModel> models = new ArrayList<>();
        private final List<MutableField> fields = new ArrayList<>();
        private final List<String> issues = new ArrayList<>();

        private ParsedWorkbook(int formatVersion) {
            this.formatVersion = formatVersion;
        }
    }

    private static final class MutableModel {
        private final String key;
        private final int rowNumber;
        private String sourceCode = "";
        private String code = "";
        private String name = "";
        private String directoryPath = "";
        private UUID directoryId;
        private String warehouseLayerCode = "";
        private ModelWarehouseLayer warehouseLayer;
        private String physicalTableName = "";
        private List<String> clickHouseOrderByColumns = List.of();
        private String description = "";
        private final List<String> issues = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<MutableField> fields = new ArrayList<>();

        private MutableModel(String key, int rowNumber) {
            this.key = key;
            this.rowNumber = rowNumber;
        }

        private ModelMetadataImportModelResponse toResponse() {
            List<ModelMetadataImportFieldResponse> fieldResponses = fields.stream()
                    .map(MutableField::toResponse)
                    .toList();
            boolean importable = issues.isEmpty() && !fieldResponses.isEmpty()
                    && fieldResponses.stream().allMatch(ModelMetadataImportFieldResponse::importable);
            return new ModelMetadataImportModelResponse(
                    key, rowNumber, code, name, directoryPath, warehouseLayerCode,
                    ModelWarehouseLayerSummaryResponse.from(warehouseLayer),
                    physicalTableName, clickHouseOrderByColumns, description,
                    importable, issues, warnings, fieldResponses
            );
        }
    }

    private static final class MutableField {
        private final String key;
        private final int rowNumber;
        private String modelReference = "";
        private String code = "";
        private String name = "";
        private PlatformDataType fieldType;
        private Integer length;
        private Integer precision;
        private Integer scale;
        private GeometryTypeDefinition geometry;
        private boolean geometryParametersPresent;
        private Boolean nullable;
        private Boolean primaryKey;
        private Integer sortOrder;
        private String description = "";
        private String standardDictionaryCode = "";
        private StandardDictionary standardDictionary;
        private final List<String> issues = new ArrayList<>();

        private MutableField(String key, int rowNumber) {
            this.key = key;
            this.rowNumber = rowNumber;
        }

        private ModelMetadataImportFieldResponse toResponse() {
            return new ModelMetadataImportFieldResponse(
                    key, rowNumber, code, name, fieldType, length, precision, scale, geometry, nullable, primaryKey,
                    sortOrder, description, standardDictionaryCode,
                    StandardDictionarySummaryResponse.from(standardDictionary),
                    issues.isEmpty(), issues
            );
        }
    }
}

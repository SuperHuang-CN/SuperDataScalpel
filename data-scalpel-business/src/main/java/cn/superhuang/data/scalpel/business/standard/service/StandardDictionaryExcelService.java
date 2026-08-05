package cn.superhuang.data.scalpel.business.standard.service;

import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.ModelFieldTemplateItemRepository;
import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionary;
import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionaryItem;
import cn.superhuang.data.scalpel.business.standard.repository.StandardDictionaryItemRepository;
import cn.superhuang.data.scalpel.business.standard.repository.StandardDictionaryRepository;
import cn.superhuang.data.scalpel.business.standard.web.request.ExportStandardDictionaryMetadataRequest;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryImportDictionaryPreviewResponse;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryImportItemPreviewResponse;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryImportPreviewResponse;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryImportResultResponse;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
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
public class StandardDictionaryExcelService {

    private static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String FILE_MARKER = "DATASCALPEL_STANDARD_DICTIONARY";
    private static final int FORMAT_VERSION = 1;
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_DICTIONARIES = 200;
    private static final int MAX_ITEMS = 20_000;
    private static final Pattern DICTIONARY_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.ROOT);
    private static final List<PlatformDataType> VALUE_TYPES = List.of(
            PlatformDataType.STRING,
            PlatformDataType.INTEGER,
            PlatformDataType.LONG,
            PlatformDataType.DECIMAL,
            PlatformDataType.BOOLEAN
    );
    private static final List<String> DICTIONARY_HEADERS = List.of(
            "码表编码*", "码表名称*", "取值类型*", "启用状态*", "说明"
    );
    private static final List<String> ITEM_HEADERS = List.of(
            "码表编码*", "节点编码*", "节点名称*", "父节点编码", "同级顺序*", "启用状态*", "说明"
    );

    private final StandardDictionaryRepository dictionaryRepository;
    private final StandardDictionaryItemRepository itemRepository;
    private final DataModelFieldRepository fieldRepository;
    private final ModelFieldTemplateItemRepository templateItemRepository;
    private final StandardDictionaryValueSupport valueSupport;

    public StandardDictionaryExcelService(
            StandardDictionaryRepository dictionaryRepository,
            StandardDictionaryItemRepository itemRepository,
            DataModelFieldRepository fieldRepository,
            ModelFieldTemplateItemRepository templateItemRepository,
            StandardDictionaryValueSupport valueSupport
    ) {
        this.dictionaryRepository = dictionaryRepository;
        this.itemRepository = itemRepository;
        this.fieldRepository = fieldRepository;
        this.templateItemRepository = templateItemRepository;
        this.valueSupport = valueSupport;
    }

    public String contentType() {
        return CONTENT_TYPE;
    }

    public StandardDictionaryExcelFile template() {
        return new StandardDictionaryExcelFile(
                "DataScalpel-码表导入模板.xlsx",
                writeWorkbook(List.of())
        );
    }

    @Transactional(readOnly = true)
    public StandardDictionaryExcelFile export(ExportStandardDictionaryMetadataRequest request) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>(request.dictionaryIds());
        if (ids.size() != request.dictionaryIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "导出码表不能重复选择");
        }
        Map<UUID, StandardDictionary> found = dictionaryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(StandardDictionary::getId, Function.identity()));
        List<UUID> missing = ids.stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "部分导出码表不存在：" + missing);
        }
        List<DictionaryExport> exports = ids.stream()
                .map(found::get)
                .map(dictionary -> new DictionaryExport(
                        dictionary,
                        preorder(itemRepository.findAllByDictionaryIdOrderBySortOrderAscNameAscCodeAsc(
                                dictionary.getId()
                        ))
                ))
                .toList();
        return new StandardDictionaryExcelFile(
                "DataScalpel-码表元数据-" + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".xlsx",
                writeWorkbook(exports)
        );
    }

    @Transactional(readOnly = true)
    public StandardDictionaryImportPreviewResponse preview(MultipartFile file) {
        Analysis analysis = analyze(file, false);
        return analysis.response(originalFileName(file));
    }

    @Transactional
    public StandardDictionaryImportResultResponse importMetadata(MultipartFile file, String previewDigest) {
        Analysis analysis = analyze(file, true);
        if (!analysis.importable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "码表 Excel 仍包含未解决问题，不能导入");
        }
        if (previewDigest == null || !MessageDigest.isEqual(
                analysis.digest().getBytes(StandardCharsets.UTF_8),
                previewDigest.trim().getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "码表或导入文件已发生变化，请重新预览");
        }
        int createdDictionaries = 0;
        int updatedDictionaries = 0;
        int createdItems = 0;
        int updatedItems = 0;
        List<UUID> dictionaryIds = new ArrayList<>();

        for (DictionaryDraft draft : analysis.dictionaries) {
            StandardDictionary dictionary = draft.existing;
            boolean created = dictionary == null;
            boolean changed = false;
            if (created) {
                dictionary = StandardDictionary.create(
                        draft.code, draft.name, draft.valueType, draft.enabled, draft.description
                );
                dictionaryRepository.saveAndFlush(dictionary);
                createdDictionaries++;
            } else {
                changed |= dictionary.update(draft.code, draft.name, draft.valueType, draft.description);
                changed |= draft.enabled ? dictionary.enable() : dictionary.disable();
            }

            List<StandardDictionaryItem> allItems = new ArrayList<>(
                    itemRepository.findAllByDictionaryIdOrderBySortOrderAscNameAscCodeAsc(dictionary.getId())
            );
            Map<String, StandardDictionaryItem> itemsByCode = allItems.stream()
                    .collect(Collectors.toMap(StandardDictionaryItem::getCode, Function.identity()));
            List<StandardDictionaryItem> newItems = new ArrayList<>();
            for (ItemDraft itemDraft : draft.items) {
                if (!itemsByCode.containsKey(itemDraft.code)) {
                    StandardDictionaryItem item = StandardDictionaryItem.create(
                            dictionary.getId(),
                            null,
                            itemDraft.code,
                            itemDraft.name,
                            itemDraft.sortOrder,
                            itemDraft.enabled,
                            itemDraft.description
                    );
                    newItems.add(item);
                    allItems.add(item);
                    itemsByCode.put(itemDraft.code, item);
                    createdItems++;
                    changed = true;
                }
            }
            if (!newItems.isEmpty()) {
                itemRepository.saveAllAndFlush(newItems);
            }

            for (ItemDraft itemDraft : draft.items) {
                StandardDictionaryItem item = itemsByCode.get(itemDraft.code);
                StandardDictionaryItem parent = itemDraft.parentCode == null
                        ? null
                        : itemsByCode.get(itemDraft.parentCode);
                changed |= item.update(itemDraft.code, itemDraft.name, itemDraft.description);
                changed |= itemDraft.enabled ? item.enable() : item.disable();
                changed |= item.move(parent == null ? null : parent.getId(), itemDraft.sortOrder);
                if ("UPDATE".equals(itemDraft.action)) {
                    updatedItems++;
                }
            }
            changed |= reindex(allItems);
            itemRepository.saveAllAndFlush(allItems);

            if (!created && changed) {
                dictionary.advanceVersion();
                dictionaryRepository.saveAndFlush(dictionary);
                updatedDictionaries++;
            }
            dictionaryIds.add(dictionary.getId());
        }
        return new StandardDictionaryImportResultResponse(
                createdDictionaries,
                updatedDictionaries,
                createdItems,
                updatedItems,
                dictionaryIds
        );
    }

    private Analysis analyze(MultipartFile file, boolean lockExisting) {
        ParsedWorkbook parsed = readWorkbook(file);
        Set<String> requestedCodes = parsed.dictionaries.stream()
                .map(row -> row.code)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
        List<StandardDictionary> existingDictionaries = lockExisting && !requestedCodes.isEmpty()
                ? dictionaryRepository.findAllByCodeInForUpdate(requestedCodes)
                : dictionaryRepository.findAll();
        Map<String, StandardDictionary> existingByCode = existingDictionaries.stream()
                .collect(Collectors.toMap(StandardDictionary::getCode, Function.identity()));
        Set<String> duplicateDictionaries = duplicates(parsed.dictionaries.stream().map(row -> row.code).toList());
        List<DictionaryDraft> drafts = new ArrayList<>();
        for (DictionaryRow row : parsed.dictionaries) {
            List<String> issues = new ArrayList<>(row.issues);
            if (duplicateDictionaries.contains(row.code)) {
                issues.add("码表编码在文件中重复：" + row.code);
            }
            StandardDictionary existing = existingByCode.get(row.code);
            if (existing != null && existing.getValueType() != row.valueType
                    && (itemRepository.countByDictionaryId(existing.getId()) > 0
                    || fieldRepository.existsByStandardDictionaryId(existing.getId())
                    || templateItemRepository.existsByStandardDictionaryId(existing.getId()))) {
                issues.add("已有节点、模型字段或常用字段模板引用的码表不能修改取值类型");
            }
            DictionaryDraft draft = new DictionaryDraft(
                    row.rowNumber,
                    existing,
                    row.code,
                    row.name,
                    row.valueType,
                    row.enabled,
                    row.description,
                    issues
            );
            drafts.add(draft);
        }
        Map<String, List<ItemRow>> itemsByDictionary = parsed.items.stream()
                .collect(Collectors.groupingBy(row -> row.dictionaryCode, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<ItemRow>> entry : itemsByDictionary.entrySet()) {
            DictionaryDraft dictionary = drafts.stream()
                    .filter(candidate -> candidate.code.equals(entry.getKey()))
                    .findFirst()
                    .orElse(null);
            if (dictionary == null) {
                parsed.issues.add("码表项引用了未在“码表”工作表声明的码表：" + entry.getKey());
                continue;
            }
            analyzeItems(dictionary, entry.getValue());
        }
        for (DictionaryDraft dictionary : drafts) {
            dictionary.finishAction();
        }
        return new Analysis(parsed.issues, drafts);
    }

    private void analyzeItems(DictionaryDraft dictionary, List<ItemRow> rows) {
        List<StandardDictionaryItem> existingItems = dictionary.existing == null
                ? List.of()
                : itemRepository.findAllByDictionaryIdOrderBySortOrderAscNameAscCodeAsc(dictionary.existing.getId());
        Map<String, StandardDictionaryItem> existingByCode = existingItems.stream()
                .collect(Collectors.toMap(StandardDictionaryItem::getCode, Function.identity()));
        Map<UUID, String> existingCodeById = existingItems.stream()
                .collect(Collectors.toMap(StandardDictionaryItem::getId, StandardDictionaryItem::getCode));

        List<ItemDraft> items = new ArrayList<>();
        for (ItemRow row : rows) {
            List<String> issues = new ArrayList<>(row.issues);
            String code = normalizeValue(dictionary.valueType, row.code, "节点编码", issues);
            String parentCode = row.parentCode == null || row.parentCode.isBlank()
                    ? null
                    : normalizeValue(dictionary.valueType, row.parentCode, "父节点编码", issues);
            items.add(new ItemDraft(
                    row.rowNumber,
                    existingByCode.get(code),
                    code,
                    row.name,
                    parentCode,
                    row.sortOrder,
                    row.enabled,
                    row.description,
                    issues
            ));
        }
        Set<String> duplicates = duplicates(items.stream().map(item -> item.code).toList());
        Map<String, ItemDraft> importedByCode = new LinkedHashMap<>();
        for (ItemDraft item : items) {
            if (!item.code.isBlank() && duplicates.contains(item.code)) {
                item.issues.add("节点编码规范化后在当前码表中重复：" + item.code);
            } else if (!item.code.isBlank()) {
                importedByCode.put(item.code, item);
            }
            if (!item.code.isBlank() && item.existing == null && dictionary.existing != null) {
                try {
                    valueSupport.validateValueAgainstReferences(dictionary.existing, item.code);
                } catch (ResponseStatusException exception) {
                    item.issues.add(exception.getReason());
                }
            }
        }

        Set<String> availableCodes = new HashSet<>(existingByCode.keySet());
        availableCodes.addAll(importedByCode.keySet());
        Map<String, String> parentByCode = new HashMap<>();
        for (StandardDictionaryItem item : existingItems) {
            parentByCode.put(item.getCode(), existingCodeById.get(item.getParentId()));
        }
        for (ItemDraft item : items) {
            if (item.parentCode != null && !availableCodes.contains(item.parentCode)) {
                item.issues.add("父节点不存在：" + item.parentCode);
            }
            parentByCode.put(item.code, item.parentCode);
        }
        validateCycles(items, parentByCode);
        for (ItemDraft item : items) {
            item.finishAction(existingCodeById);
        }
        dictionary.items.addAll(items);
    }

    private static void validateCycles(List<ItemDraft> items, Map<String, String> parentByCode) {
        for (ItemDraft item : items) {
            Set<String> visited = new HashSet<>();
            String current = item.code;
            while (current != null) {
                if (!visited.add(current)) {
                    item.issues.add("节点层级形成循环：" + String.join(" → ", visited));
                    break;
                }
                current = parentByCode.get(current);
            }
        }
    }

    private String normalizeValue(
            PlatformDataType valueType,
            String value,
            String label,
            List<String> issues
    ) {
        try {
            return valueSupport.normalizeItemValue(valueType, value);
        } catch (ResponseStatusException exception) {
            issues.add(label + "不合法：" + exception.getReason());
            return "";
        }
    }

    private ParsedWorkbook readWorkbook(MultipartFile file) {
        validateFile(file);
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            validateMarker(workbook);
            Sheet dictionaries = requireSheet(workbook, "码表");
            Sheet items = requireSheet(workbook, "码表项");
            requireHeaders(dictionaries, DICTIONARY_HEADERS);
            requireHeaders(items, ITEM_HEADERS);
            ParsedWorkbook parsed = new ParsedWorkbook();
            readDictionaries(dictionaries, parsed);
            readItems(items, parsed);
            return parsed;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取码表 Excel，请使用系统模板", exception);
        }
    }

    private static void readDictionaries(Sheet sheet, ParsedWorkbook parsed) {
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (blankRow(row, DICTIONARY_HEADERS.size())) {
                continue;
            }
            if (parsed.dictionaries.size() >= MAX_DICTIONARIES) {
                parsed.issues.add("单个文件最多导入 " + MAX_DICTIONARIES + " 张码表");
                break;
            }
            int rowNumber = rowIndex + 1;
            List<String> issues = new ArrayList<>();
            String code = cell(row, 0, issues, "码表", rowNumber).toUpperCase(Locale.ROOT);
            if (!DICTIONARY_CODE.matcher(code).matches()) {
                issues.add("码表编码只能包含字母、数字和下划线，且必须以字母开头");
            }
            String name = required(cell(row, 1, issues, "码表", rowNumber), "码表名称", 100, issues);
            PlatformDataType valueType = valueType(cell(row, 2, issues, "码表", rowNumber), issues);
            boolean enabled = booleanValue(cell(row, 3, issues, "码表", rowNumber), "启用状态", issues);
            String description = optional(cell(row, 4, issues, "码表", rowNumber), "说明", 500, issues);
            parsed.dictionaries.add(new DictionaryRow(
                    rowNumber, code, name, valueType, enabled, description, issues
            ));
        }
        if (parsed.dictionaries.isEmpty()) {
            parsed.issues.add("码表工作表中至少需要填写一张码表");
        }
    }

    private static void readItems(Sheet sheet, ParsedWorkbook parsed) {
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (blankRow(row, ITEM_HEADERS.size())) {
                continue;
            }
            if (parsed.items.size() >= MAX_ITEMS) {
                parsed.issues.add("单个文件最多导入 " + MAX_ITEMS + " 个码表项");
                break;
            }
            int rowNumber = rowIndex + 1;
            List<String> issues = new ArrayList<>();
            String dictionaryCode = cell(row, 0, issues, "码表项", rowNumber).toUpperCase(Locale.ROOT);
            if (!DICTIONARY_CODE.matcher(dictionaryCode).matches()) {
                issues.add("码表编码不合法");
            }
            String code = required(cell(row, 1, issues, "码表项", rowNumber), "节点编码", 256, issues);
            String name = required(cell(row, 2, issues, "码表项", rowNumber), "节点名称", 100, issues);
            String parentCode = optional(cell(row, 3, issues, "码表项", rowNumber), "父节点编码", 256, issues);
            int sortOrder = integer(cell(row, 4, issues, "码表项", rowNumber), "同级顺序", issues);
            boolean enabled = booleanValue(cell(row, 5, issues, "码表项", rowNumber), "启用状态", issues);
            String description = optional(cell(row, 6, issues, "码表项", rowNumber), "说明", 500, issues);
            parsed.items.add(new ItemRow(
                    rowNumber,
                    dictionaryCode,
                    code,
                    name,
                    parentCode,
                    sortOrder,
                    enabled,
                    description,
                    issues
            ));
        }
    }

    private byte[] writeWorkbook(List<DictionaryExport> exports) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = styles(workbook);
            Sheet instructions = workbook.createSheet("说明");
            String[][] instructionsRows = {
                    {"文件标识", FILE_MARKER},
                    {"格式版本", Integer.toString(FORMAT_VERSION)},
                    {"用途", "仅导入导出树形码表元数据，不包含物理业务数据。"},
                    {"树结构", "父节点通过同一码表内的父节点编码定位；父节点行可以出现在子节点行之后。"},
                    {"更新规则", "缺少的已有码表或节点保持不变，不根据 Excel 缺失执行删除。"},
                    {"取值类型", "STRING、INTEGER、LONG、DECIMAL、BOOLEAN。"},
                    {"状态", "启用状态填写“启用”或“停用”。"}
            };
            for (int index = 0; index < instructionsRows.length; index++) {
                Row row = instructions.createRow(index);
                writeText(row, 0, instructionsRows[index][0], styles.header);
                writeText(row, 1, instructionsRows[index][1], styles.text);
            }
            instructions.setColumnWidth(0, 20 * 256);
            instructions.setColumnWidth(1, 100 * 256);

            Sheet dictionarySheet = dataSheet(
                    workbook, "码表", DICTIONARY_HEADERS, new int[]{22, 26, 18, 16, 50}, styles
            );
            Sheet itemSheet = dataSheet(
                    workbook, "码表项", ITEM_HEADERS, new int[]{22, 24, 26, 24, 14, 16, 50}, styles
            );
            addValidation(dictionarySheet, 2, VALUE_TYPES.stream().map(Enum::name).toArray(String[]::new), MAX_DICTIONARIES);
            addValidation(dictionarySheet, 3, new String[]{"启用", "停用"}, MAX_DICTIONARIES);
            addValidation(itemSheet, 5, new String[]{"启用", "停用"}, MAX_ITEMS);

            int dictionaryRowIndex = 1;
            int itemRowIndex = 1;
            for (DictionaryExport export : exports) {
                StandardDictionary dictionary = export.dictionary;
                Row row = dictionarySheet.createRow(dictionaryRowIndex++);
                writeText(row, 0, dictionary.getCode(), styles.text);
                writeText(row, 1, dictionary.getName(), styles.text);
                writeText(row, 2, dictionary.getValueType().name(), styles.text);
                writeText(row, 3, dictionary.isEnabled() ? "启用" : "停用", styles.text);
                writeText(row, 4, dictionary.getDescription(), styles.text);
                Map<UUID, String> codeById = export.items.stream()
                        .collect(Collectors.toMap(StandardDictionaryItem::getId, StandardDictionaryItem::getCode));
                for (StandardDictionaryItem item : export.items) {
                    Row itemRow = itemSheet.createRow(itemRowIndex++);
                    writeText(itemRow, 0, dictionary.getCode(), styles.text);
                    writeText(itemRow, 1, item.getCode(), styles.text);
                    writeText(itemRow, 2, item.getName(), styles.text);
                    writeText(itemRow, 3, codeById.get(item.getParentId()), styles.text);
                    writeInteger(itemRow, 4, item.getSortOrder(), styles.integer);
                    writeText(itemRow, 5, item.isEnabled() ? "启用" : "停用", styles.text);
                    writeText(itemRow, 6, item.getDescription(), styles.text);
                }
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成码表 Excel 失败", exception);
        }
    }

    private static List<StandardDictionaryItem> preorder(List<StandardDictionaryItem> items) {
        Comparator<StandardDictionaryItem> order = Comparator
                .comparingInt(StandardDictionaryItem::getSortOrder)
                .thenComparing(StandardDictionaryItem::getName)
                .thenComparing(StandardDictionaryItem::getCode);
        Map<UUID, List<StandardDictionaryItem>> children = new HashMap<>();
        items.forEach(item -> children.computeIfAbsent(item.getParentId(), ignored -> new ArrayList<>()).add(item));
        children.values().forEach(list -> list.sort(order));
        List<StandardDictionaryItem> result = new ArrayList<>();
        appendPreorder(null, children, result, new HashSet<>());
        return result;
    }

    private static void appendPreorder(
            UUID parentId,
            Map<UUID, List<StandardDictionaryItem>> children,
            List<StandardDictionaryItem> result,
            Set<UUID> visited
    ) {
        for (StandardDictionaryItem item : children.getOrDefault(parentId, List.of())) {
            if (!visited.add(item.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "码表树存在循环引用");
            }
            result.add(item);
            appendPreorder(item.getId(), children, result, visited);
        }
    }

    private static boolean reindex(List<StandardDictionaryItem> items) {
        Map<UUID, List<StandardDictionaryItem>> byParent = new HashMap<>();
        for (StandardDictionaryItem item : items) {
            byParent.computeIfAbsent(item.getParentId(), ignored -> new ArrayList<>()).add(item);
        }
        Comparator<StandardDictionaryItem> order = Comparator
                .comparingInt(StandardDictionaryItem::getSortOrder)
                .thenComparing(StandardDictionaryItem::getName)
                .thenComparing(StandardDictionaryItem::getCode);
        boolean changed = false;
        for (List<StandardDictionaryItem> siblings : byParent.values()) {
            siblings.sort(order);
            for (int index = 0; index < siblings.size(); index++) {
                StandardDictionaryItem item = siblings.get(index);
                changed |= item.move(item.getParentId(), index);
            }
        }
        return changed;
    }

    private static Set<String> duplicates(List<String> values) {
        Map<String, Integer> counts = new HashMap<>();
        values.stream().filter(value -> value != null && !value.isBlank())
                .forEach(value -> counts.merge(value, 1, Integer::sum));
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要导入的码表 Excel");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "码表 Excel 不能超过 10 MB");
        }
        if (!originalFileName(file).toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只支持 .xlsx 码表文件");
        }
    }

    private static void validateMarker(Workbook workbook) {
        Sheet instructions = requireSheet(workbook, "说明");
        if (!FILE_MARKER.equals(formatted(instructions.getRow(0), 1))
                || !Integer.toString(FORMAT_VERSION).equals(formatted(instructions.getRow(1), 1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel 文件标识或格式版本不正确，请使用系统模板");
        }
    }

    private static Sheet requireSheet(Workbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel 缺少工作表：“" + name + "”");
        }
        return sheet;
    }

    private static void requireHeaders(Sheet sheet, List<String> headers) {
        for (int index = 0; index < headers.size(); index++) {
            if (!headers.get(index).equals(formatted(sheet.getRow(0), index))) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        sheet.getSheetName() + "工作表第 " + (index + 1) + " 列应为“" + headers.get(index) + "”"
                );
            }
        }
    }

    private static String cell(Row row, int index, List<String> issues, String sheet, int rowNumber) {
        Cell cell = row == null ? null : row.getCell(index);
        if (cell != null && cell.getCellType() == CellType.FORMULA) {
            issues.add(sheet + "工作表第 " + rowNumber + " 行第 " + (index + 1) + " 列不允许使用公式");
            return "";
        }
        return formatted(row, index);
    }

    private static String formatted(Row row, int index) {
        if (row == null || row.getCell(index) == null) {
            return "";
        }
        return FORMATTER.formatCellValue(row.getCell(index)).trim();
    }

    private static boolean blankRow(Row row, int columns) {
        for (int index = 0; index < columns; index++) {
            if (!formatted(row, index).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String required(String value, String label, int maximum, List<String> issues) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            issues.add(label + "不能为空");
        } else if (normalized.length() > maximum) {
            issues.add(label + "不能超过 " + maximum + " 个字符");
        }
        return normalized;
    }

    private static String optional(String value, String label, int maximum, List<String> issues) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maximum) {
            issues.add(label + "不能超过 " + maximum + " 个字符");
        }
        return normalized.isBlank() ? null : normalized;
    }

    private static PlatformDataType valueType(String value, List<String> issues) {
        try {
            PlatformDataType parsed = PlatformDataType.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (!VALUE_TYPES.contains(parsed)) {
                throw new IllegalArgumentException();
            }
            return parsed;
        } catch (RuntimeException exception) {
            issues.add("取值类型只能填写 STRING、INTEGER、LONG、DECIMAL 或 BOOLEAN");
            return PlatformDataType.STRING;
        }
    }

    private static boolean booleanValue(String value, String label, List<String> issues) {
        return switch (value == null ? "" : value.trim().toLowerCase(Locale.ROOT)) {
            case "启用", "是", "true", "1" -> true;
            case "停用", "否", "false", "0" -> false;
            default -> {
                issues.add(label + "只能填写启用或停用");
                yield false;
            }
        };
    }

    private static int integer(String value, String label, List<String> issues) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException exception) {
            issues.add(label + "必须是大于等于 0 的整数");
            return 0;
        }
    }

    private static String originalFileName(MultipartFile file) {
        return file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "码表元数据.xlsx"
                : file.getOriginalFilename().trim();
    }

    private static Sheet dataSheet(
            XSSFWorkbook workbook,
            String name,
            List<String> headers,
            int[] widths,
            Styles styles
    ) {
        Sheet sheet = workbook.createSheet(name);
        Row row = sheet.createRow(0);
        for (int index = 0; index < headers.size(); index++) {
            writeText(row, index, headers.get(index), styles.header);
            sheet.setColumnWidth(index, widths[index] * 256);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.size() - 1));
        return sheet;
    }

    private static void addValidation(Sheet sheet, int column, String[] values, int maximumRows) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(values);
        DataValidation validation = helper.createValidation(
                constraint,
                new CellRangeAddressList(1, maximumRows, column, column)
        );
        validation.setShowErrorBox(true);
        validation.setSuppressDropDownArrow(true);
        sheet.addValidationData(validation);
    }

    private static Styles styles(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle header = workbook.createCellStyle();
        header.setFont(font);
        header.setFillForegroundColor(IndexedColors.BLUE_GREY.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        borders(header);
        CellStyle text = workbook.createCellStyle();
        text.setWrapText(true);
        text.setVerticalAlignment(VerticalAlignment.TOP);
        borders(text);
        CellStyle integer = workbook.createCellStyle();
        integer.cloneStyleFrom(text);
        integer.setDataFormat(workbook.createDataFormat().getFormat("0"));
        return new Styles(header, text, integer);
    }

    private static void borders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
    }

    private static void writeText(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index, CellType.STRING);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void writeInteger(Row row, int index, int value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static String digest(List<String> globalIssues, List<DictionaryDraft> dictionaries) {
        StringBuilder source = new StringBuilder();
        globalIssues.forEach(issue -> source.append("G|").append(issue).append('\n'));
        for (DictionaryDraft dictionary : dictionaries) {
            source.append("D|")
                    .append(dictionary.existing == null ? "" : dictionary.existing.getId())
                    .append('|')
                    .append(dictionary.existing == null ? "" : dictionary.existing.getVersion())
                    .append('|').append(dictionary.code)
                    .append('|').append(dictionary.name)
                    .append('|').append(dictionary.valueType)
                    .append('|').append(dictionary.enabled)
                    .append('|').append(dictionary.description)
                    .append('\n');
            for (ItemDraft item : dictionary.items) {
                source.append("I|").append(item.code)
                        .append('|').append(item.name)
                        .append('|').append(item.parentCode)
                        .append('|').append(item.sortOrder)
                        .append('|').append(item.enabled)
                        .append('|').append(item.description)
                        .append('\n');
            }
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(source.toString().getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private record Styles(CellStyle header, CellStyle text, CellStyle integer) {
    }

    private record DictionaryExport(StandardDictionary dictionary, List<StandardDictionaryItem> items) {
    }

    private static final class ParsedWorkbook {
        private final List<String> issues = new ArrayList<>();
        private final List<DictionaryRow> dictionaries = new ArrayList<>();
        private final List<ItemRow> items = new ArrayList<>();
    }

    private record DictionaryRow(
            int rowNumber,
            String code,
            String name,
            PlatformDataType valueType,
            boolean enabled,
            String description,
            List<String> issues
    ) {
    }

    private record ItemRow(
            int rowNumber,
            String dictionaryCode,
            String code,
            String name,
            String parentCode,
            int sortOrder,
            boolean enabled,
            String description,
            List<String> issues
    ) {
    }

    private static final class DictionaryDraft {
        private final int rowNumber;
        private final StandardDictionary existing;
        private final String code;
        private final String name;
        private final PlatformDataType valueType;
        private final boolean enabled;
        private final String description;
        private final List<String> issues;
        private final List<ItemDraft> items = new ArrayList<>();
        private String action;

        private DictionaryDraft(
                int rowNumber,
                StandardDictionary existing,
                String code,
                String name,
                PlatformDataType valueType,
                boolean enabled,
                String description,
                List<String> issues
        ) {
            this.rowNumber = rowNumber;
            this.existing = existing;
            this.code = code;
            this.name = name;
            this.valueType = valueType;
            this.enabled = enabled;
            this.description = description;
            this.issues = issues;
        }

        private void finishAction() {
            if (!issues.isEmpty() || items.stream().anyMatch(item -> !item.issues.isEmpty())) {
                action = "ERROR";
            } else if (existing == null) {
                action = "CREATE";
            } else if (!existing.getName().equals(name)
                    || existing.getValueType() != valueType
                    || existing.isEnabled() != enabled
                    || !Objects.equals(existing.getDescription(), description)
                    || items.stream().anyMatch(item -> !"UNCHANGED".equals(item.action))) {
                action = "UPDATE";
            } else {
                action = "UNCHANGED";
            }
        }

        private StandardDictionaryImportDictionaryPreviewResponse response() {
            return new StandardDictionaryImportDictionaryPreviewResponse(
                    rowNumber,
                    existing == null ? null : existing.getId(),
                    existing == null ? null : existing.getVersion(),
                    code,
                    name,
                    valueType,
                    enabled,
                    description,
                    action,
                    issues,
                    items.stream().map(ItemDraft::response).toList()
            );
        }
    }

    private static final class ItemDraft {
        private final int rowNumber;
        private final StandardDictionaryItem existing;
        private final String code;
        private final String name;
        private final String parentCode;
        private final int sortOrder;
        private final boolean enabled;
        private final String description;
        private final List<String> issues;
        private String action;

        private ItemDraft(
                int rowNumber,
                StandardDictionaryItem existing,
                String code,
                String name,
                String parentCode,
                int sortOrder,
                boolean enabled,
                String description,
                List<String> issues
        ) {
            this.rowNumber = rowNumber;
            this.existing = existing;
            this.code = code;
            this.name = name;
            this.parentCode = parentCode;
            this.sortOrder = sortOrder;
            this.enabled = enabled;
            this.description = description;
            this.issues = issues;
        }

        private void finishAction(Map<UUID, String> existingCodeById) {
            if (!issues.isEmpty()) {
                action = "ERROR";
            } else if (existing == null) {
                action = "CREATE";
            } else if (!existing.getName().equals(name)
                    || existing.getSortOrder() != sortOrder
                    || existing.isEnabled() != enabled
                    || !Objects.equals(existing.getDescription(), description)
                    || !Objects.equals(existingCodeById.get(existing.getParentId()), parentCode)) {
                action = "UPDATE";
            } else {
                action = "UNCHANGED";
            }
        }

        private StandardDictionaryImportItemPreviewResponse response() {
            return new StandardDictionaryImportItemPreviewResponse(
                    rowNumber,
                    code,
                    name,
                    parentCode,
                    sortOrder,
                    enabled,
                    description,
                    action,
                    issues
            );
        }
    }

    private static final class Analysis {
        private final List<String> issues;
        private final List<DictionaryDraft> dictionaries;

        private Analysis(List<String> issues, List<DictionaryDraft> dictionaries) {
            this.issues = List.copyOf(issues);
            this.dictionaries = List.copyOf(dictionaries);
        }

        private boolean importable() {
            return issues.isEmpty()
                    && !dictionaries.isEmpty()
                    && dictionaries.stream().allMatch(dictionary -> "CREATE".equals(dictionary.action)
                            || "UPDATE".equals(dictionary.action)
                            || "UNCHANGED".equals(dictionary.action));
        }

        private String digest() {
            return StandardDictionaryExcelService.digest(issues, dictionaries);
        }

        private StandardDictionaryImportPreviewResponse response(String fileName) {
            return new StandardDictionaryImportPreviewResponse(
                    fileName,
                    FORMAT_VERSION,
                    importable(),
                    digest(),
                    issues,
                    dictionaries.stream().map(DictionaryDraft::response).toList()
            );
        }
    }
}

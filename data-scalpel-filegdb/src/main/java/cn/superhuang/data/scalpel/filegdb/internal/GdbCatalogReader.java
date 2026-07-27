package cn.superhuang.data.scalpel.filegdb.internal;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import cn.superhuang.data.scalpel.filegdb.FileGdbOpenOptions;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadLimits;
import cn.superhuang.data.scalpel.filegdb.FileGdbSource;
import cn.superhuang.data.scalpel.filegdb.FileGdbSourceInfo;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbFeature;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayer;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayerType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads a00000001 and resolves logical catalog entries to immutable physical table headers. */
public final class GdbCatalogReader {
    private static final String CATALOG_PHYSICAL_NAME = "a00000001";

    private GdbCatalogReader() {
    }

    public static OpenedCatalog open(FileGdbSource source, FileGdbOpenOptions options) {
        FileGdbSourceInfo sourceInfo = source.info();
        FileGdbReadLimits limits = options.limits();
        requireFile(source, "gdb");
        String catalogTableFile = CATALOG_PHYSICAL_NAME + ".gdbtable";
        String catalogIndexFile = CATALOG_PHYSICAL_NAME + ".gdbtablx";
        requireFile(source, catalogTableFile);
        GdbTableDefinition catalogDefinition = GdbTableHeaderReader.read(
                source,
                catalogTableFile,
                CATALOG_PHYSICAL_NAME,
                limits);
        requireFile(source, catalogIndexFile);
        if (catalogDefinition.layerType() != FileGdbLayerType.TABLE) {
            throw new FileGdbException(FileGdbErrorCode.MALFORMED_HEADER, "FileGDB system catalog is not a table");
        }
        List<CatalogEntry> entries = readEntries(source, catalogDefinition, catalogIndexFile, limits);
        List<GdbLayerHandle> handles = new ArrayList<>();
        Set<Integer> physicalIds = new HashSet<>();
        for (CatalogEntry entry : entries.stream().sorted(Comparator.comparingInt(CatalogEntry::physicalId)).toList()) {
            if (!physicalIds.add(entry.physicalId())) {
                throw new FileGdbException(FileGdbErrorCode.MALFORMED_HEADER, "System catalog contains duplicate physical table IDs");
            }
            boolean system = isSystemTable(entry.name());
            if (system && !options.includeSystemTables()) {
                continue;
            }
            String physicalName = physicalName(entry.physicalId());
            String tableFileName = physicalName + ".gdbtable";
            String indexFileName = physicalName + ".gdbtablx";
            requireFile(source, tableFileName);
            GdbTableDefinition definition = GdbTableHeaderReader.read(
                    source,
                    tableFileName,
                    physicalName,
                    limits);
            requireFile(source, indexFileName);
            FileGdbLayer layer = new FileGdbLayer(
                    physicalName,
                    entry.name(),
                    entry.physicalId(),
                    definition.layerType(),
                    system);
            handles.add(new GdbLayerHandle(layer, source, definition, indexFileName, limits));
        }
        return new OpenedCatalog(sourceInfo, List.copyOf(handles));
    }

    private static List<CatalogEntry> readEntries(
            FileGdbSource source,
            GdbTableDefinition definition,
            String indexFileName,
            FileGdbReadLimits limits) {
        List<CatalogEntry> entries = new ArrayList<>();
        try (GdbTableIndexReader index = GdbTableIndexReader.open(
                        source,
                        indexFileName,
                        CATALOG_PHYSICAL_NAME,
                        limits);
                GdbTableReader table = GdbTableReader.open(source, definition, limits)) {
            if (index.slotCount() > limits.maxIndexSlotsPerCursor()) {
                throw new FileGdbException(
                        FileGdbErrorCode.LIMIT_EXCEEDED,
                        "FileGDB system catalog exceeds the configured index slot limit");
            }
            for (int slot = 0; slot < index.slotCount(); slot++) {
                long offset = index.recordOffset(slot);
                if (offset == 0) {
                    continue;
                }
                int oid = Math.addExact(slot, 1);
                FileGdbFeature feature = GdbRecordReader.read(
                        oid,
                        table.readRecord(offset, oid),
                        definition,
                        limits);
                Object id = feature.attribute("ID");
                Object name = feature.attribute("Name");
                if (!(id instanceof Integer physicalId) || physicalId <= 0 || !(name instanceof String logicalName) || logicalName.isBlank()) {
                    throw new FileGdbException(
                            FileGdbErrorCode.MALFORMED_HEADER,
                            "System catalog contains an invalid ID or Name at OID " + oid);
                }
                entries.add(new CatalogEntry(physicalId, logicalName));
            }
        }
        if (entries.isEmpty()) {
            throw new FileGdbException(FileGdbErrorCode.MALFORMED_HEADER, "FileGDB system catalog is empty");
        }
        return entries;
    }

    private static void requireFile(FileGdbSource source, String fileName) {
        if (!source.exists(fileName)) {
            throw new FileGdbException(FileGdbErrorCode.MISSING_FILE, "Missing FileGDB file " + fileName);
        }
    }

    private static boolean isSystemTable(String name) {
        return name.toUpperCase(Locale.ROOT).startsWith("GDB_");
    }

    private static String physicalName(int id) {
        return "a%08x".formatted(id);
    }

    private record CatalogEntry(int physicalId, String name) {
    }

    public record OpenedCatalog(FileGdbSourceInfo sourceInfo, List<GdbLayerHandle> layers) {
        public OpenedCatalog {
            java.util.Objects.requireNonNull(sourceInfo, "sourceInfo");
            layers = List.copyOf(layers);
        }
    }
}

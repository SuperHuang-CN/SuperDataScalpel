package cn.superhuang.data.scalpel.business.filedataset.service.prepare;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetTemporaryFileManager;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingException;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import cn.superhuang.data.scalpel.dialect.geopackage.GeoPackageException;
import cn.superhuang.data.scalpel.dialect.geopackage.GeoPackageReader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Discovers GeoPackage business tables without changing the uploaded single SQLite object. */
@Component
public class GeoPackagePreparationService implements FileDatasetPreparationService {

    private final ObjectProvider<FileObjectStorage> storageProvider;
    private final FileDatasetTemporaryFileManager temporaryFileManager;

    public GeoPackagePreparationService(
            ObjectProvider<FileObjectStorage> storageProvider,
            FileDatasetTemporaryFileManager temporaryFileManager
    ) {
        this.storageProvider = storageProvider;
        this.temporaryFileManager = temporaryFileManager;
    }

    @Override
    public boolean supports(FileDatasetFormat format) {
        return format == FileDatasetFormat.GPKG;
    }

    @Override
    public FileDatasetPreparationResult prepare(FileDatasetPreparationInput input) throws IOException {
        if (!supports(input.format())) {
            throw new IllegalArgumentException("GPKG 准备器不支持当前文件格式");
        }
        FileObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new FileDatasetParsingException("文件对象存储尚未配置");
        }
        FileObjectStorage.FileObjectContent content = storage.open(input.rawObjectKey());
        Path file = null;
        try (content) {
            file = temporaryFileManager.materialize(
                    content.inputStream(), content.contentLength() >= 0 ? content.contentLength() : input.rawSizeBytes()
            );
            try (GeoPackageReader reader = GeoPackageReader.open(file)) {
                List<DiscoveredTable> tables = reader.discoverTables().stream()
                        .map(table -> new DiscoveredTable(table.tableName(), table.tableName(), 0))
                        .toList();
                if (tables.isEmpty()) {
                    throw new FileDatasetParsingException("GeoPackage 不包含可导入的 features 或 attributes 表");
                }
                return new FileDatasetPreparationResult(null, 0, 0, tables);
            }
        } catch (GeoPackageException exception) {
            throw new FileDatasetParsingException(exception.getMessage(), exception);
        } finally {
            temporaryFileManager.delete(file);
        }
    }

    @Override
    public void discard(String materializedPrefix) {
        // GeoPackage remains its original immutable object; no object prefix is materialized.
    }
}

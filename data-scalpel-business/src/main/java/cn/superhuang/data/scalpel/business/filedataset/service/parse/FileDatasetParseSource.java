package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.filegdb.FileGeodatabase;
import cn.superhuang.data.scalpel.shapefile.ShapefileDataset;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;

/** A parser input supplied by the file-dataset service after storage access is resolved. */
public sealed interface FileDatasetParseSource permits
        FileDatasetParseSource.Stream,
        FileDatasetParseSource.LocalFile,
        FileDatasetParseSource.FileGdb,
        FileDatasetParseSource.Shapefile {

    record Stream(InputStream inputStream) implements FileDatasetParseSource {
        public Stream {
            Objects.requireNonNull(inputStream, "inputStream");
        }
    }

    record LocalFile(Path path) implements FileDatasetParseSource {
        public LocalFile {
            Objects.requireNonNull(path, "path");
        }
    }

    record FileGdb(FileGeodatabase database) implements FileDatasetParseSource {
        public FileGdb {
            Objects.requireNonNull(database, "database");
        }
    }

    record Shapefile(ShapefileDataset dataset) implements FileDatasetParseSource {
        public Shapefile {
            Objects.requireNonNull(dataset, "dataset");
        }
    }

    static InputStream requireStream(FileDatasetParseSource source) {
        if (source instanceof Stream stream) {
            return stream.inputStream();
        }
        throw new FileDatasetParsingException("当前文件解析器只支持流式输入");
    }

    static Path requireLocalFile(FileDatasetParseSource source) {
        if (source instanceof LocalFile localFile) {
            return localFile.path();
        }
        throw new FileDatasetParsingException("当前文件解析器需要本地文件输入");
    }

    static FileGeodatabase requireFileGdb(FileDatasetParseSource source) {
        if (source instanceof FileGdb fileGdb) {
            return fileGdb.database();
        }
        throw new FileDatasetParsingException("当前文件解析器需要已物化的 GDB 目录输入");
    }

    static ShapefileDataset requireShapefile(FileDatasetParseSource source) {
        if (source instanceof Shapefile shapefile) {
            return shapefile.dataset();
        }
        throw new FileDatasetParsingException("当前文件解析器需要已物化的 SHP 组件集输入");
    }
}

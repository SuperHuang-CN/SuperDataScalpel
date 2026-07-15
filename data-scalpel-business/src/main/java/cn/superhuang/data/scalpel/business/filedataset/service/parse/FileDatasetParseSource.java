package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;

/** A parser input supplied by the file-dataset service after storage access is resolved. */
public sealed interface FileDatasetParseSource permits FileDatasetParseSource.Stream, FileDatasetParseSource.LocalFile {

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
}

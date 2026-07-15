package cn.superhuang.data.scalpel.business.filedataset.service;

import java.io.InputStream;

/** Download payload resolved from a private object store. */
public record FileDatasetContent(
        String originalFileName,
        String contentType,
        long sizeBytes,
        InputStream inputStream
) {
}

package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileRecordDelimiter;

/**
 * Parser-internal configuration for formats with an available sample parser.
 *
 * <p>It deliberately contains no HTTP or JSON-serialization annotations. The
 * file-dataset service translates the persisted API configuration at its
 * boundary before invoking a parser.</p>
 */
public sealed interface FileDatasetParsingConfiguration permits
        FileDatasetParsingConfiguration.Csv,
        FileDatasetParsingConfiguration.Text,
        FileDatasetParsingConfiguration.Json,
        FileDatasetParsingConfiguration.JsonLines,
        FileDatasetParsingConfiguration.Spreadsheet,
        FileDatasetParsingConfiguration.Parquet,
        FileDatasetParsingConfiguration.Avro {

    record Csv(
            String charset,
            String fieldDelimiter,
            FileRecordDelimiter recordDelimiter,
            String quoteCharacter,
            String escapeCharacter,
            boolean firstRowHeader
    ) implements FileDatasetParsingConfiguration {
    }

    record Text(String charset, FileRecordDelimiter recordDelimiter) implements FileDatasetParsingConfiguration {
    }

    record Json(String charset, String rootPointer) implements FileDatasetParsingConfiguration {
    }

    record JsonLines(String charset, FileRecordDelimiter recordDelimiter) implements FileDatasetParsingConfiguration {
    }

    record Spreadsheet(String sheetName, int headerRowIndex, int dataStartRowIndex)
            implements FileDatasetParsingConfiguration {
    }

    record Parquet() implements FileDatasetParsingConfiguration {
    }

    record Avro() implements FileDatasetParsingConfiguration {
    }
}

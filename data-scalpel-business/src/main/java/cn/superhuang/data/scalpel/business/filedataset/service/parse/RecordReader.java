package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileRecordDelimiter;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class RecordReader {

    private RecordReader() {
    }

    static List<String> readLines(Reader source, FileRecordDelimiter delimiter, int maxRecords) throws IOException {
        List<String> records = new ArrayList<>();
        forEachLine(source, delimiter, record -> {
            if (records.size() < maxRecords) {
                records.add(record);
            }
        }, () -> records.size() >= maxRecords);
        return records;
    }

    static void forEachLine(Reader source, FileRecordDelimiter delimiter, Consumer<String> consumer)
            throws IOException {
        forEachLine(source, delimiter, consumer, () -> false);
    }

    static void forEachLine(
            Reader source,
            FileRecordDelimiter delimiter,
            Consumer<String> consumer,
            java.util.function.BooleanSupplier stop
    ) throws IOException {
        try (PushbackReader reader = new PushbackReader(source, 2)) {
            StringBuilder current = new StringBuilder();
            int value;
            while (!stop.getAsBoolean() && (value = reader.read()) >= 0) {
                char character = (char) value;
                if (isRecordDelimiter(reader, character, delimiter)) {
                    consumer.accept(current.toString());
                    current.setLength(0);
                } else {
                    current.append(character);
                }
            }
            if (!stop.getAsBoolean() && !current.isEmpty()) {
                consumer.accept(current.toString());
            }
        }
    }

    static boolean isRecordDelimiter(PushbackReader reader, char character, FileRecordDelimiter delimiter) throws IOException {
        return switch (delimiter) {
            case LF -> character == '\n';
            case CR -> character == '\r';
            case CRLF -> isCrLf(reader, character);
            case AUTO -> isAutoLineEnding(reader, character);
        };
    }

    private static boolean isCrLf(PushbackReader reader, char character) throws IOException {
        if (character != '\r') {
            return false;
        }
        int next = reader.read();
        if (next == '\n') {
            return true;
        }
        if (next >= 0) {
            reader.unread(next);
        }
        return false;
    }

    private static boolean isAutoLineEnding(PushbackReader reader, char character) throws IOException {
        if (character == '\n') {
            return true;
        }
        if (character != '\r') {
            return false;
        }
        int next = reader.read();
        if (next >= 0 && next != '\n') {
            reader.unread(next);
        }
        return true;
    }
}

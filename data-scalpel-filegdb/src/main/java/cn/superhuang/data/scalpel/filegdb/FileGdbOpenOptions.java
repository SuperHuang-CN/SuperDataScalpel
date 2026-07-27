package cn.superhuang.data.scalpel.filegdb;

import java.util.Objects;

/** Options controlling local-directory validation, metadata visibility and parser limits. */
public record FileGdbOpenOptions(
        boolean allowSymbolicLinks,
        boolean includeSystemTables,
        FileGdbReadLimits limits) {

    public FileGdbOpenOptions {
        Objects.requireNonNull(limits, "limits");
    }

    public static FileGdbOpenOptions defaults() {
        return new FileGdbOpenOptions(false, false, FileGdbReadLimits.defaults());
    }
}

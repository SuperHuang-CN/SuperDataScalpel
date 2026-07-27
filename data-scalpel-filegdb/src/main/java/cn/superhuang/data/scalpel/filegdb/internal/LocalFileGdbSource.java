package cn.superhuang.data.scalpel.filegdb.internal;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import cn.superhuang.data.scalpel.filegdb.FileGdbOpenOptions;
import cn.superhuang.data.scalpel.filegdb.FileGdbRandomAccessObject;
import cn.superhuang.data.scalpel.filegdb.FileGdbSource;
import cn.superhuang.data.scalpel.filegdb.FileGdbSourceInfo;
import cn.superhuang.data.scalpel.filegdb.FileGdbSourceType;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Local filesystem source with directory-boundary and symbolic-link enforcement. */
public final class LocalFileGdbSource implements FileGdbSource {
    private final Path directory;
    private final Path realDirectory;
    private final boolean allowSymbolicLinks;
    private final FileGdbSourceInfo info;
    private final Set<LocalObject> openObjects = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean closed;

    private LocalFileGdbSource(Path directory, Path realDirectory, boolean allowSymbolicLinks) {
        this.directory = directory;
        this.realDirectory = realDirectory;
        this.allowSymbolicLinks = allowSymbolicLinks;
        this.info = new FileGdbSourceInfo(FileGdbSourceType.LOCAL, directory.toString());
    }

    public static LocalFileGdbSource open(Path input, FileGdbOpenOptions options) {
        if (input == null) {
            throw new FileGdbException(FileGdbErrorCode.INVALID_DIRECTORY, "FileGDB directory must not be null");
        }
        Path normalized = input.toAbsolutePath().normalize();
        Path fileName = normalized.getFileName();
        if (fileName == null || !fileName.toString().toLowerCase(Locale.ROOT).endsWith(".gdb")) {
            throw new FileGdbException(FileGdbErrorCode.INVALID_DIRECTORY, "Input must be an unpacked .gdb directory");
        }
        boolean directory = options.allowSymbolicLinks()
                ? Files.isDirectory(normalized)
                : Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS);
        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS) || !directory) {
            throw new FileGdbException(
                    FileGdbErrorCode.INVALID_DIRECTORY,
                    "FileGDB directory does not exist or is not a directory");
        }
        try {
            Path realDirectory = normalized.toRealPath();
            if (!options.allowSymbolicLinks() && Files.isSymbolicLink(normalized)) {
                throw new FileGdbException(
                        FileGdbErrorCode.INVALID_DIRECTORY,
                        "Symbolic links are disabled for the FileGDB directory");
            }
            return new LocalFileGdbSource(
                    options.allowSymbolicLinks() ? realDirectory : normalized,
                    realDirectory,
                    options.allowSymbolicLinks());
        } catch (FileGdbException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FileGdbException(FileGdbErrorCode.IO_ERROR, "Cannot resolve the FileGDB directory", exception);
        }
    }

    @Override
    public FileGdbSourceInfo info() {
        ensureOpen();
        return info;
    }

    @Override
    public boolean exists(String fileName) {
        ensureOpen();
        return resolve(fileName, false) != null;
    }

    @Override
    public FileGdbRandomAccessObject open(String fileName) {
        ensureOpen();
        Path path = resolve(fileName, true);
        try {
            FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
            LocalObject object = new LocalObject(channel, fileName, channel.size());
            openObjects.add(object);
            return object;
        } catch (IOException exception) {
            throw new FileGdbException(FileGdbErrorCode.IO_ERROR, "Cannot open FileGDB file " + fileName, exception);
        }
    }

    private Path resolve(String fileName, boolean required) {
        validateFileName(fileName);
        Path candidate = directory.resolve(fileName).normalize();
        if (!candidate.startsWith(directory)) {
            throw new FileGdbException(FileGdbErrorCode.INVALID_DIRECTORY, "FileGDB file escapes the selected directory");
        }
        if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            if (required) {
                throw new FileGdbException(FileGdbErrorCode.MISSING_FILE, "Missing FileGDB file " + fileName);
            }
            return null;
        }
        if (!allowSymbolicLinks && Files.isSymbolicLink(candidate)) {
            throw new FileGdbException(FileGdbErrorCode.INVALID_DIRECTORY, "Symbolic FileGDB files are disabled: " + fileName);
        }
        try {
            Path realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(realDirectory)) {
                throw new FileGdbException(FileGdbErrorCode.INVALID_DIRECTORY, "FileGDB file escapes the selected directory");
            }
            if (!Files.isRegularFile(realCandidate)) {
                if (required) {
                    throw new FileGdbException(
                            FileGdbErrorCode.MISSING_FILE,
                            "FileGDB entry is not a regular file: " + fileName);
                }
                return null;
            }
            return allowSymbolicLinks ? realCandidate : candidate;
        } catch (FileGdbException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FileGdbException(FileGdbErrorCode.IO_ERROR, "Cannot resolve FileGDB file " + fileName, exception);
        }
    }

    private static void validateFileName(String fileName) {
        if (fileName == null
                || fileName.isBlank()
                || fileName.equals(".")
                || fileName.equals("..")
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.chars().anyMatch(Character::isISOControl)) {
            throw new FileGdbException(FileGdbErrorCode.INVALID_DIRECTORY, "Invalid FileGDB physical file name");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new FileGdbException(FileGdbErrorCode.CLOSED, "Local FileGDB source is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        for (LocalObject object : List.copyOf(openObjects)) {
            try {
                object.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        openObjects.clear();
        if (failure != null) {
            throw failure;
        }
    }

    private final class LocalObject implements FileGdbRandomAccessObject {
        private final FileChannel channel;
        private final String fileName;
        private final long size;
        private boolean objectClosed;

        private LocalObject(FileChannel channel, String fileName, long size) {
            this.channel = channel;
            this.fileName = fileName;
            this.size = size;
        }

        @Override
        public long size() {
            ensureObjectOpen();
            return size;
        }

        @Override
        public int read(long position, ByteBuffer target) {
            ensureObjectOpen();
            if (position < 0) {
                throw new FileGdbException(FileGdbErrorCode.INVALID_OFFSET, fileName + " has a negative read position");
            }
            try {
                return channel.read(target, position);
            } catch (IOException exception) {
                throw new FileGdbException(FileGdbErrorCode.IO_ERROR, "Cannot read FileGDB file " + fileName, exception);
            }
        }

        private void ensureObjectOpen() {
            if (objectClosed || LocalFileGdbSource.this.closed) {
                throw new FileGdbException(FileGdbErrorCode.CLOSED, fileName + " is closed");
            }
        }

        @Override
        public void close() {
            if (objectClosed) {
                return;
            }
            objectClosed = true;
            openObjects.remove(this);
            try {
                channel.close();
            } catch (IOException exception) {
                throw new FileGdbException(FileGdbErrorCode.IO_ERROR, "Cannot close FileGDB file " + fileName, exception);
            }
        }
    }
}

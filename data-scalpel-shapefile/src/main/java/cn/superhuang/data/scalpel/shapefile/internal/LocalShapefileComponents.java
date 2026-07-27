package cn.superhuang.data.scalpel.shapefile.internal;

import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import cn.superhuang.data.scalpel.shapefile.ShapefileOpenOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** Resolves one local sibling component set without following links by default. */
public final class LocalShapefileComponents {
    private static final List<String> KNOWN_EXTENSIONS = List.of("shp", "shx", "dbf", "cpg", "prj");

    private final Path shp;
    private final Path shx;
    private final Path dbf;
    private final Path cpg;
    private final Path prj;

    private LocalShapefileComponents(Path shp, Path shx, Path dbf, Path cpg, Path prj) {
        this.shp = shp;
        this.shx = shx;
        this.dbf = dbf;
        this.cpg = cpg;
        this.prj = prj;
    }

    public static LocalShapefileComponents resolve(Path source, ShapefileOpenOptions options) {
        Objects.requireNonNull(source, "source");
        Path normalized = source.toAbsolutePath().normalize();
        Path name = normalized.getFileName();
        if (name == null || !extension(name.toString()).equals("shp")) {
            throw new ShapefileException(
                    ShapefileErrorCode.INVALID_SOURCE, "Shapefile source must be a .shp file");
        }
        Path parent = normalized.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new ShapefileException(ShapefileErrorCode.INVALID_SOURCE, "Shapefile parent directory is invalid");
        }
        String stem = stem(name.toString()).toLowerCase(Locale.ROOT);
        Map<String, List<Path>> matches = new HashMap<>();
        try (Stream<Path> paths = Files.list(parent)) {
            paths.forEach(path -> {
                String fileName = path.getFileName().toString();
                String ext = extension(fileName);
                if (KNOWN_EXTENSIONS.contains(ext)
                        && stem(fileName).toLowerCase(Locale.ROOT).equals(stem)) {
                    matches.computeIfAbsent(ext, ignored -> new ArrayList<>()).add(path.toAbsolutePath().normalize());
                }
            });
        } catch (IOException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.IO_ERROR, "Cannot inspect Shapefile component directory", exception);
        }
        for (Map.Entry<String, List<Path>> entry : matches.entrySet()) {
            if (entry.getValue().size() > 1) {
                throw new ShapefileException(
                        ShapefileErrorCode.INVALID_SOURCE,
                        "Shapefile has case-conflicting ." + entry.getKey() + " components");
            }
        }
        Path shp = required(matches, "shp");
        if (!sameFile(shp, normalized)) {
            throw new ShapefileException(
                    ShapefileErrorCode.INVALID_SOURCE, "Requested .shp path conflicts with a sibling component");
        }
        Path shx = required(matches, "shx");
        Path dbf = required(matches, "dbf");
        Path cpg = optional(matches, "cpg");
        Path prj = optional(matches, "prj");
        for (Path component : List.of(shp, shx, dbf)) {
            validateFile(component, options.allowSymbolicLinks());
            enforceSize(component, options.readLimits().maxComponentFileBytes());
        }
        if (cpg != null) {
            validateFile(cpg, options.allowSymbolicLinks());
            enforceSize(cpg, options.readLimits().maxMetadataBytes());
        }
        if (prj != null) {
            validateFile(prj, options.allowSymbolicLinks());
            enforceSize(prj, options.readLimits().maxMetadataBytes());
        }
        return new LocalShapefileComponents(shp, shx, dbf, cpg, prj);
    }

    public Path shp() { return shp; }
    public Path shx() { return shx; }
    public Path dbf() { return dbf; }
    public Path cpg() { return cpg; }
    public Path prj() { return prj; }

    private static Path required(Map<String, List<Path>> matches, String extension) {
        Path value = optional(matches, extension);
        if (value == null) {
            throw new ShapefileException(
                    ShapefileErrorCode.MISSING_COMPONENT, "Missing Shapefile ." + extension + " component");
        }
        return value;
    }

    private static Path optional(Map<String, List<Path>> matches, String extension) {
        List<Path> values = matches.get(extension);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private static void validateFile(Path path, boolean allowSymbolicLinks) {
        if (!allowSymbolicLinks && Files.isSymbolicLink(path)) {
            throw new ShapefileException(
                    ShapefileErrorCode.INVALID_SOURCE, "Shapefile component must not be a symbolic link");
        }
        LinkOption[] linkOptions = allowSymbolicLinks
                ? new LinkOption[0] : new LinkOption[] {LinkOption.NOFOLLOW_LINKS};
        if (!Files.isRegularFile(path, linkOptions) || !Files.isReadable(path)) {
            throw new ShapefileException(
                    ShapefileErrorCode.INVALID_SOURCE,
                    "Shapefile component is not a readable regular file");
        }
    }

    private static void enforceSize(Path path, long maximum) {
        try {
            if (Files.size(path) > maximum) {
                throw new ShapefileException(
                        ShapefileErrorCode.LIMIT_EXCEEDED, "Shapefile component exceeds the configured size limit");
            }
        } catch (IOException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.IO_ERROR, "Cannot inspect Shapefile component size", exception);
        }
    }

    private static boolean sameFile(Path discovered, Path requested) {
        if (discovered.equals(requested)) {
            return true;
        }
        try {
            return Files.isSameFile(discovered, requested);
        } catch (IOException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.INVALID_SOURCE, "Requested .shp path is not a readable component", exception);
        }
    }

    private static String extension(String fileName) {
        int separator = fileName.lastIndexOf('.');
        return separator < 0 ? "" : fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private static String stem(String fileName) {
        int separator = fileName.lastIndexOf('.');
        return separator < 0 ? fileName : fileName.substring(0, separator);
    }
}

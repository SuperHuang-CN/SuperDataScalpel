package cn.superhuang.data.scalpel.shapefile.s3;

import cn.superhuang.data.scalpel.shapefile.ShapefileComponent;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Explicit S3 object keys for one unpacked Shapefile component set. */
public final class S3ShapefileLocation {
    private final String bucket;
    private final Map<ShapefileComponent, String> keys;

    private S3ShapefileLocation(String bucket, Map<ShapefileComponent, String> keys) {
        this.bucket = requireBucket(bucket);
        EnumMap<ShapefileComponent, String> validated = new EnumMap<>(ShapefileComponent.class);
        Set<String> distinct = new HashSet<>();
        for (ShapefileComponent component : ShapefileComponent.values()) {
            String key = keys.get(component);
            if (key == null) {
                if (component.required()) {
                    throw new IllegalArgumentException(
                            "S3 Shapefile " + component + " object key must not be absent");
                }
                continue;
            }
            String normalized = requireKey(key, component);
            if (!distinct.add(normalized)) {
                throw new IllegalArgumentException("S3 Shapefile component object keys must be distinct");
            }
            validated.put(component, normalized);
        }
        this.keys = Map.copyOf(validated);
    }

    /** Derives lower-case companion suffixes from a key whose final path segment ends in {@code .shp}. */
    public static S3ShapefileLocation fromShpKey(String bucket, String shpKey) {
        String normalizedShp = requireKey(shpKey, ShapefileComponent.SHP);
        int suffix = normalizedShp.length() - 4;
        if (suffix < 0 || !normalizedShp.substring(suffix).toLowerCase(Locale.ROOT).equals(".shp")) {
            throw new IllegalArgumentException("S3 Shapefile key must end in .shp");
        }
        String stem = normalizedShp.substring(0, suffix);
        EnumMap<ShapefileComponent, String> keys = new EnumMap<>(ShapefileComponent.class);
        keys.put(ShapefileComponent.SHP, normalizedShp);
        for (ShapefileComponent component : ShapefileComponent.values()) {
            if (component != ShapefileComponent.SHP) {
                keys.put(component, stem + "." + component.extension());
            }
        }
        return new S3ShapefileLocation(bucket, keys);
    }

    /** Returns a new location with one explicitly selected component key. */
    public S3ShapefileLocation withComponentKey(ShapefileComponent component, String key) {
        Objects.requireNonNull(component, "component");
        EnumMap<ShapefileComponent, String> updated = new EnumMap<>(ShapefileComponent.class);
        updated.putAll(keys);
        if (key == null) {
            if (component.required()) {
                throw new IllegalArgumentException("Required S3 Shapefile component key cannot be removed");
            }
            updated.remove(component);
        } else {
            updated.put(component, key);
        }
        return new S3ShapefileLocation(bucket, updated);
    }

    public String bucket() {
        return bucket;
    }

    public Optional<String> componentKey(ShapefileComponent component) {
        return Optional.ofNullable(keys.get(Objects.requireNonNull(component, "component")));
    }

    String requiredKey(ShapefileComponent component) {
        String key = keys.get(component);
        if (key == null) {
            throw new IllegalArgumentException("S3 Shapefile component key is not configured: " + component);
        }
        return key;
    }

    String safeLocation() {
        return "s3://" + bucket + "/" + requiredKey(ShapefileComponent.SHP);
    }

    private static String requireBucket(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("S3 bucket must not be blank");
        }
        String normalized = value.strip();
        if (normalized.contains("/")
                || normalized.contains("\\")
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("S3 bucket contains invalid characters");
        }
        return normalized;
    }

    private static String requireKey(String value, ShapefileComponent component) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("S3 Shapefile " + component + " object key must not be blank");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("S3 Shapefile object key contains control characters");
        }
        return value;
    }
}

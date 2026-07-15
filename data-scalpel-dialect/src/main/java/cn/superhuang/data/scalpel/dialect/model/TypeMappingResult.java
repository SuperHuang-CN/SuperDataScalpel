package cn.superhuang.data.scalpel.dialect.model;

/** Result of one directional type mapping. Unsupported mappings do not contain a target definition. */
public record TypeMappingResult<T>(
        T definition,
        TypeMappingQuality quality,
        String message
) {

    public TypeMappingResult {
        if (quality == null) {
            throw new IllegalArgumentException("Type mapping quality is required");
        }
        if (quality == TypeMappingQuality.UNSUPPORTED && definition != null) {
            throw new IllegalArgumentException("Unsupported mapping must not contain a target definition");
        }
        if (quality != TypeMappingQuality.UNSUPPORTED && definition == null) {
            throw new IllegalArgumentException("Supported mapping requires a target definition");
        }
        message = message == null || message.isBlank() ? null : message.trim();
    }

    public static <T> TypeMappingResult<T> exact(T definition) {
        return new TypeMappingResult<>(definition, TypeMappingQuality.EXACT, null);
    }

    public static <T> TypeMappingResult<T> normalized(T definition, String message) {
        return new TypeMappingResult<>(definition, TypeMappingQuality.NORMALIZED, message);
    }

    public static <T> TypeMappingResult<T> lossy(T definition, String message) {
        return new TypeMappingResult<>(definition, TypeMappingQuality.LOSSY, message);
    }

    public static <T> TypeMappingResult<T> unsupported(String message) {
        return new TypeMappingResult<>(null, TypeMappingQuality.UNSUPPORTED, message);
    }

    public boolean acceptable() {
        return quality.acceptable();
    }
}

package cn.superhuang.datascalpel.taskengine.language;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JavaLanguageIndexCacheTest {
    @TempDir Path root;

    @Test void fingerprintDetectsContentChangesEvenWithSameSizeAndTimestamp() throws Exception {
        Path jar = root.resolve("sdk.jar");
        Files.writeString(jar, "old-content");
        var stamp = Files.getLastModifiedTime(jar);
        String before = JavaLanguageIndexCache.fingerprintInputs(List.of(jar));
        Files.writeString(jar, "new-content");
        Files.setLastModifiedTime(jar, stamp);
        assertNotEquals(before, JavaLanguageIndexCache.fingerprintInputs(List.of(jar)));
        Path moved = root.resolve("sdk-moved.jar");
        Files.copy(jar, moved);
        Files.setLastModifiedTime(moved, stamp);
        assertNotEquals(JavaLanguageIndexCache.fingerprintInputs(List.of(jar)),
                JavaLanguageIndexCache.fingerprintInputs(List.of(moved)));
    }

    @Test void explodedSdkFingerprintIncludesAddedClasses() throws Exception {
        Path classes = Files.createDirectories(root.resolve("classes"));
        Files.writeString(classes.resolve("A.class"), "A");
        String before = JavaLanguageIndexCache.fingerprintInputs(List.of(classes));
        Files.writeString(classes.resolve("B.class"), "B");
        assertNotEquals(before, JavaLanguageIndexCache.fingerprintInputs(List.of(classes)));
    }

    @Test void copiesAreIndependentAndCorruptionIsRejected() throws Exception {
        Path published = Files.createDirectories(root.resolve("published/1.134")).getParent();
        Files.writeString(published.resolve("1.134/123.index"), "index-data");
        var manifest = new JavaLanguageIndexCache.Manifest("fingerprint", JavaLanguageIndexCache.inventory(published));
        Path a = JavaLanguageIndexCache.copyVerified(published, manifest, root.resolve("a"));
        Path b = JavaLanguageIndexCache.copyVerified(published, manifest, root.resolve("b"));
        Files.writeString(a.resolve("1.134/123.index"), "modified-by-jdt");
        assertEquals(manifest.files(), JavaLanguageIndexCache.inventory(published));
        assertEquals(manifest.files(), JavaLanguageIndexCache.inventory(b));
        Files.writeString(published.resolve("1.134/123.index"), "broken-index");
        assertThrows(java.io.IOException.class, () -> JavaLanguageIndexCache.copyVerified(published, manifest, root.resolve("c")));
    }

    @Test void invalidManifestCannotEscapeWorkspace() {
        var manifest = new JavaLanguageIndexCache.Manifest("fingerprint",
                Map.of("../escape.index", new JavaLanguageIndexCache.IndexFile(1, 1, "sha")));
        assertThrows(java.io.IOException.class, () -> JavaLanguageIndexCache.copyVerified(root, manifest, root.resolve("private")));
        assertFalse(Files.exists(root.resolve("escape.index")));
    }

    @Test void deploymentCanDisableCacheOrOverrideItsDirectory() {
        var defaults = LanguageServiceConfiguration.from(Map.of(), 8091);
        assertTrue(defaults.indexCacheEnabled());
        assertEquals(defaults.workRoot().resolve("index-cache"), defaults.indexCacheRoot());
        var disabled = LanguageServiceConfiguration.from(Map.of("DATASCALPEL_LANGUAGE_INDEX_CACHE_ENABLED", "false",
                "DATASCALPEL_LANGUAGE_INDEX_CACHE_ROOT", root.toString()), 8091);
        assertFalse(disabled.indexCacheEnabled());
        assertEquals(root, disabled.indexCacheRoot());
    }
}

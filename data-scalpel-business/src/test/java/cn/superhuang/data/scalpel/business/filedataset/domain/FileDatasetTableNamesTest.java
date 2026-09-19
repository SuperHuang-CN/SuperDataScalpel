package cn.superhuang.data.scalpel.business.filedataset.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileDatasetTableNamesTest {

    @Test
    void trimsAndCollapsesUnicodeWhitespaceToUnderscores() {
        assertEquals("Road_Data_2026", FileDatasetTableNames.normalize("  Road\t Data\u30002026  "));
    }

    @Test
    void comparesNormalizedNamesWithoutCase() {
        assertEquals(FileDatasetTableNames.uniquenessKey("A B"), FileDatasetTableNames.uniquenessKey("A_B"));
        assertEquals(FileDatasetTableNames.uniquenessKey("A_B"), FileDatasetTableNames.uniquenessKey("a_b"));
    }
}

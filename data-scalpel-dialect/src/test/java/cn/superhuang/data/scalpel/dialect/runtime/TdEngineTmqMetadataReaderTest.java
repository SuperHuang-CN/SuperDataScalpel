package cn.superhuang.data.scalpel.dialect.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TdEngineTmqMetadataReaderTest {

    @Test
    void recognizesOfficialColumnTopicSubtype() {
        assertTrue(TdEngineTmqMetadataReader.isDataTopicType("3"));
        assertTrue(TdEngineTmqMetadataReader.isDataTopicType("COLUMN"));
        assertTrue(TdEngineTmqMetadataReader.isDataTopicType("query"));
    }

    @Test
    void rejectsDatabaseStableAndUnknownTopicSubtypes() {
        assertFalse(TdEngineTmqMetadataReader.isDataTopicType("1"));
        assertFalse(TdEngineTmqMetadataReader.isDataTopicType("2"));
        assertFalse(TdEngineTmqMetadataReader.isDataTopicType("0"));
        assertFalse(TdEngineTmqMetadataReader.isDataTopicType("TABLE"));
        assertFalse(TdEngineTmqMetadataReader.isDataTopicType("DB"));
    }
}

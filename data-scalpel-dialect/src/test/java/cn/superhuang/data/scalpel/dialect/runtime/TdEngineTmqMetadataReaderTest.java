package cn.superhuang.data.scalpel.dialect.runtime;

import org.junit.jupiter.api.Test;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TdEngineTmqMetadataReaderTest {

    @Test
    void decodesWebsocketCatalogByteArraysBeforeFilteringAndParsing() throws Exception {
        String[] columns = {"topic_name", "db_name", "sql", "topic_type"};
        String[] values = {"城市测点", "qa1004", "select ts from qa1004.meters", "3"};
        var metadata = proxy(ResultSetMetaData.class, (p, m, a) -> switch (m.getName()) {
            case "getColumnCount" -> columns.length;
            case "getColumnLabel", "getColumnName" -> columns[(int) a[0] - 1];
            default -> null;
        });
        AtomicBoolean first = new AtomicBoolean(true);
        var rows = proxy(ResultSet.class, (p, m, a) -> switch (m.getName()) {
            case "getMetaData" -> metadata;
            case "next" -> first.getAndSet(false);
            case "getObject" -> values[(int) a[0] - 1].getBytes(StandardCharsets.UTF_8);
            default -> null;
        });
        var statement = proxy(Statement.class, (p, m, a) -> m.getName().equals("executeQuery") ? rows : null);
        var connection = proxy(Connection.class, (p, m, a) -> m.getName().equals("createStatement") ? statement : null);
        var topics = new TdEngineTmqMetadataReader(BuiltInDialects.registry().require("TDENGINE_WEBSOCKET"))
                .list(connection, "测点");
        assertEquals(1, topics.size());
        assertEquals("城市测点", topics.getFirst().topicName());
        assertEquals("qa1004", topics.getFirst().databaseName());
        assertEquals("仅支持 SELECT * FROM database.supertable 形式的完整超级表 Topic", topics.getFirst().unsupportedReason());
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

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

package cn.superhuang.data.scalpel.engine.query;

import cn.superhuang.data.scalpel.contract.service.JdbcDataSourceSnapshot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DataSourcePoolRegistryTest {

    @Test
    void connectionOptionsParticipateInAStablePoolFingerprint() {
        UUID dataSourceId = UUID.randomUUID();
        JdbcDataSourceSnapshot original = snapshot(
                dataSourceId,
                Map.of("sslmode", "prefer", "tcpKeepAlive", "true")
        );
        JdbcDataSourceSnapshot changed = snapshot(
                dataSourceId,
                Map.of("sslmode", "prefer", "tcpKeepAlive", "false")
        );
        Map<String, String> reordered = new LinkedHashMap<>();
        reordered.put("tcpKeepAlive", "true");
        reordered.put("sslmode", "prefer");

        assertNotEquals(
                DataSourcePoolRegistry.fingerprint(original),
                DataSourcePoolRegistry.fingerprint(changed)
        );
        assertEquals(
                DataSourcePoolRegistry.fingerprint(original),
                DataSourcePoolRegistry.fingerprint(snapshot(dataSourceId, reordered))
        );
    }

    private static JdbcDataSourceSnapshot snapshot(UUID dataSourceId, Map<String, String> options) {
        return new JdbcDataSourceSnapshot(
                dataSourceId, "POSTGRESQL", "db.internal", 5432, "sample", "public",
                "reader", "secret", options
        );
    }
}

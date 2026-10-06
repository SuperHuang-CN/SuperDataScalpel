package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.contract.task.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CanvasTmqFingerprintTest {
    private final CanvasDefinitionValidator validator = new CanvasDefinitionValidator(new CanvasDefinitionUpgrader());

    @Test
    void acceptsDiscoveredV2FingerprintAndLegacyDraftButRejectsMalformedVersions() {
        assertDoesNotThrow(() -> validator.validate(definition("v2:" + "a".repeat(64))));
        assertDoesNotThrow(() -> validator.validate(definition("b".repeat(64))));
        for (String invalid : List.of("v3:" + "a".repeat(64), "v2:" + "a".repeat(63), "v2:" + "A".repeat(64))) {
            assertThrows(ResponseStatusException.class, () -> validator.validate(definition(invalid)));
        }
    }

    private CanvasDefinition definition(String fingerprint) {
        var configuration = new TdEngineTmqInputConfiguration(UUID.randomUUID().toString(), "meters", "city",
                "sensors", fingerprint, "measurements", TdEngineTmqStartingOffsets.LATEST, 10000, 3);
        var node = new TdEngineTmqInputNodeDefinition(UUID.randomUUID().toString(), "TMQ",
                new CanvasNodeLayout(0d, 0d, 300d, 200d), configuration);
        return new CanvasDefinition(4, 78, List.of(node), List.of());
    }
}

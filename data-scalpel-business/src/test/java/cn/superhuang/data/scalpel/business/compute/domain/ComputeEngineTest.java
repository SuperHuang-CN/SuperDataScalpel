package cn.superhuang.data.scalpel.business.compute.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComputeEngineTest {

    @Test
    void ownsRegistrationLifecycleAndResetsHealthWhenConfigurationChanges() {
        ComputeEngine engine = engine();

        assertEquals(ComputeEngineRegistrationState.CREATED, engine.getRegistrationState());
        assertEquals(ComputeEngineHealthState.UNKNOWN, engine.getHealthState());
        assertNull(engine.getDispatcherInstanceId());

        engine.update(
                "本地计算引擎", null, "http://127.0.0.1:18092/", "encrypted-token",
                ComputeBackendType.LOCAL_DOCKER, "commands.local", "runner.local", "admin.events",
                20, 2, 2
        );

        engine.update(
                "本地计算引擎", "开发环境", "http://127.0.0.1:18092", "encrypted-token",
                ComputeBackendType.LOCAL_DOCKER, "commands.local", "runner.local", "admin.events",
                20, 2, 2
        );

        engine.beginRegistration();
        assertEquals(ComputeEngineRegistrationState.REGISTERING, engine.getRegistrationState());
        engine.activate("dispatcher-local", ComputeBackendType.LOCAL_DOCKER);
        assertEquals(ComputeEngineRegistrationState.ACTIVE, engine.getRegistrationState());
        assertEquals(ComputeEngineHealthState.UP, engine.getHealthState());
        engine.update(
                "本地计算引擎", "更新后的开发环境", "http://127.0.0.1:28092", "new-encrypted-token",
                ComputeBackendType.LOCAL_DOCKER, "commands.next", "runner.next", "admin.events",
                20, 2, 2
        );
        assertNull(engine.getDispatcherInstanceId());
        assertNull(engine.getReportedBackendType());
        assertEquals(ComputeEngineHealthState.UNKNOWN, engine.getHealthState());
        engine.markDraining();
        assertEquals(ComputeEngineRegistrationState.DRAINING, engine.getRegistrationState());
        engine.markInactive();
        assertEquals(ComputeEngineRegistrationState.INACTIVE, engine.getRegistrationState());
    }

    @Test
    void rejectsInvalidUrlTopicsAndAdmissionPolicy() {
        assertThrows(IllegalArgumentException.class, () -> ComputeEngine.create(
                "bad", null, "ftp://dispatcher", "cipher", ComputeBackendType.LOCAL_DOCKER,
                "command", "runner", "admin", 20, 2, 2
        ));
        assertThrows(IllegalArgumentException.class, () -> ComputeEngine.create(
                "bad", null, "http://dispatcher", "cipher", ComputeBackendType.LOCAL_DOCKER,
                "bad topic", "runner", "admin", 20, 2, 2
        ));
        assertThrows(IllegalArgumentException.class, () -> ComputeEngine.create(
                "bad", null, "http://dispatcher", "cipher", ComputeBackendType.LOCAL_DOCKER,
                "command", "runner", "admin", 20, 0, 2
        ));
    }

    @Test
    void recordsOfflineDetachAndClearsAuditAfterActivation() {
        ComputeEngine engine = engine();
        engine.activate("dispatcher-old", ComputeBackendType.LOCAL_DOCKER);

        engine.detach("原 Dispatcher 主机已永久下线");

        assertEquals(ComputeEngineRegistrationState.DETACHED, engine.getRegistrationState());
        assertEquals(ComputeEngineHealthState.DOWN, engine.getHealthState());
        assertNull(engine.getDispatcherInstanceId());
        assertNull(engine.getReportedBackendType());
        assertNotNull(engine.getDetachedAt());
        assertEquals("原 Dispatcher 主机已永久下线", engine.getDetachReason());

        engine.activate("dispatcher-new", ComputeBackendType.LOCAL_DOCKER);
        assertEquals(ComputeEngineRegistrationState.ACTIVE, engine.getRegistrationState());
        assertNull(engine.getDetachedAt());
        assertNull(engine.getDetachReason());
    }

    private static ComputeEngine engine() {
        return ComputeEngine.create(
                "本地计算引擎", null, "http://127.0.0.1:18092", "encrypted-token",
                ComputeBackendType.LOCAL_DOCKER, "commands.local", "runner.local", "admin.events",
                20, 2, 2
        );
    }
}

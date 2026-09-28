package cn.superhuang.data.scalpel.business.task.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;

class SparkResourceValidationTest {
    @Test void rejectsAlternativeHeapOverridesButAllowsOrdinaryJvmOptions() {
        for (String option : java.util.List.of("-Xmx8g", "-XX:MaxHeapSize=8589934592",
                "-XX:InitialHeapSize=8589934592", "-XX:MaxRAMPercentage=99")) {
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                    SparkJarTaskDefinitionService.class, "validateDriverJvmOptions", option))
                    .isInstanceOf(ResponseStatusException.class);
        }
        assertThatCode(() -> ReflectionTestUtils.invokeMethod(SparkJarTaskDefinitionService.class,
                "validateDriverJvmOptions", "-Duser.timezone=Asia/Shanghai -XX:+UseG1GC")).doesNotThrowAnyException();
    }
}

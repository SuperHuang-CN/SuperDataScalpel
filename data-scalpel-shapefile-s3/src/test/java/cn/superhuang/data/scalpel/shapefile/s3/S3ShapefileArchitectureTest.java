package cn.superhuang.data.scalpel.shapefile.s3;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

class S3ShapefileArchitectureTest {
    @Test
    void runtimeClasspathHasNoCrtNettyOrNativeBindings() {
        for (String forbiddenClass : List.of(
                "software.amazon.awssdk.crtcore.CrtConfigurationUtils",
                "software.amazon.awssdk.crt.CRT",
                "software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient",
                "com.sun.jna.Native")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(forbiddenClass));
        }
    }

    @Test
    void publicApiDoesNotExposeAdapterImplementationTypes() {
        for (Class<?> apiType : List.of(
                S3ShapefileLocation.class,
                S3ShapefileOptions.class,
                S3ShapefileSource.class)) {
            for (Method method : apiType.getMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                assertNotImplementation(method.getReturnType());
                for (Class<?> parameterType : method.getParameterTypes()) {
                    assertNotImplementation(parameterType);
                }
            }
        }
    }

    private static void assertNotImplementation(Class<?> type) {
        assertFalse(
                type.getName().contains("RangeCachingS3ShapefileSource")
                        || type.getName().contains("S3ObjectAccess")
                        || type.getName().contains("AwsS3ObjectAccess"),
                () -> "Public API exposes implementation type " + type.getName());
    }
}

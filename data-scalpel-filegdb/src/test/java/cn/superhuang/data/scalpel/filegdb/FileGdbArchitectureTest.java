package cn.superhuang.data.scalpel.filegdb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.superhuang.data.scalpel.filegdb.model.FileGdbEnvelope;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbFeature;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbField;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbFieldType;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayer;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayerType;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbSchema;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbSpatialReference;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbCoordinateSequence;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbGeometry;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbMultiPoint;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPoint;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolygon;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolyline;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileGdbArchitectureTest {
    @Test
    void runtimeClasspathHasNoApplicationBigDataOrNativeDependencies() {
        for (String forbiddenClass : List.of(
                "org.springframework.context.ApplicationContext",
                "org.apache.spark.sql.Row",
                "org.apache.hadoop.conf.Configuration",
                "scala.Option",
                "org.gdal.gdal.gdal",
                "com.sun.jna.Native")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(forbiddenClass));
        }
    }

    @Test
    void supportedPublicApiDoesNotExposeInternalBinaryTypes() {
        for (Class<?> apiType : List.of(
                FileGeodatabase.class,
                FileGdbOpenOptions.class,
                FileGdbReadOptions.class,
                FileGdbReadLimits.class,
                FileGdbFeatureCursor.class,
                FileGdbSource.class,
                FileGdbRandomAccessObject.class,
                FileGdbSourceInfo.class,
                FileGdbSourceType.class,
                FileGdbException.class,
                FileGdbErrorCode.class,
                FileGdbEnvelope.class,
                FileGdbFeature.class,
                FileGdbField.class,
                FileGdbFieldType.class,
                FileGdbLayer.class,
                FileGdbLayerType.class,
                FileGdbSchema.class,
                FileGdbSpatialReference.class,
                FileGdbGeometry.class,
                FileGdbPoint.class,
                FileGdbMultiPoint.class,
                FileGdbPolyline.class,
                FileGdbPolygon.class,
                FileGdbCoordinateSequence.class)) {
            for (Method method : apiType.getMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                assertNotInternal(method.getReturnType());
                for (Class<?> parameterType : method.getParameterTypes()) {
                    assertNotInternal(parameterType);
                }
            }
        }
    }

    private static void assertNotInternal(Class<?> type) {
        Class<?> componentType = type.isArray() ? type.getComponentType() : type;
        assertFalse(
                componentType.getPackageName().startsWith("cn.superhuang.data.scalpel.filegdb.internal"),
                () -> "Public API exposes internal type " + componentType.getName());
    }
}

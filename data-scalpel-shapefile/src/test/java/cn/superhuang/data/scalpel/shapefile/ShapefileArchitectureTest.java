package cn.superhuang.data.scalpel.shapefile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileFeature;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileField;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileFieldType;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileSchema;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileShapeType;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileSpatialReference;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileCoordinateSequence;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileGeometry;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileMultiPoint;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePoint;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePolygon;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePolyline;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShapefileArchitectureTest {
    @Test
    void runtimeClasspathHasNoApplicationBigDataGisOrNativeDependencies() {
        for (String forbiddenClass : List.of(
                "org.springframework.context.ApplicationContext",
                "org.apache.spark.sql.Row",
                "org.apache.hadoop.conf.Configuration",
                "scala.Option",
                "org.gdal.gdal.gdal",
                "com.sun.jna.Native",
                "com.esri.core.geometry.Geometry")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(forbiddenClass));
        }
    }

    @Test
    void supportedPublicApiDoesNotExposeInternalBinaryTypes() {
        for (Class<?> apiType : List.of(
                ShapefileDataset.class,
                ShapefileOpenOptions.class,
                ShapefileReadOptions.class,
                ShapefileReadLimits.class,
                ShapefileFeatureCursor.class,
                ShapefileComponent.class,
                ShapefileSource.class,
                ShapefileRandomAccessObject.class,
                ShapefileSourceInfo.class,
                ShapefileSourceType.class,
                ShapefileException.class,
                ShapefileErrorCode.class,
                ShapefileEnvelope.class,
                ShapefileFeature.class,
                ShapefileField.class,
                ShapefileFieldType.class,
                ShapefileSchema.class,
                ShapefileShapeType.class,
                ShapefileSpatialReference.class,
                ShapefileCoordinateSequence.class,
                ShapefileGeometry.class,
                ShapefilePoint.class,
                ShapefileMultiPoint.class,
                ShapefilePolyline.class,
                ShapefilePolygon.class)) {
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
                componentType.getPackageName().startsWith("cn.superhuang.data.scalpel.shapefile.internal"),
                () -> "Public API exposes internal type " + componentType.getName());
    }
}

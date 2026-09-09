package cn.superhuang.data.scalpel.business.metric;
import cn.superhuang.data.scalpel.business.metric.domain.*;
import cn.superhuang.data.scalpel.business.metric.repository.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
class MetricPersistenceMappingTest {
 @Test void validatesReferenceProtectionAndDirectoryProjectionWithoutDatabase() throws Exception {
  var registry=new StandardServiceRegistryBuilder().applySetting("hibernate.dialect","org.hibernate.dialect.PostgreSQLDialect")
   .applySetting("hibernate.boot.allow_jdbc_metadata_access",false).applySetting("hibernate.hbm2ddl.auto","none")
   .applySetting("hibernate.connection.provider_class","org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl").build();
  try(var factory=new MetadataSources(registry).addAnnotatedClass(DataMetric.class).addAnnotatedClass(MetricRelease.class).addAnnotatedClass(MetricReference.class).buildMetadata().buildSessionFactory();var session=factory.openSession()){
   session.createSelectionQuery(MetricReferenceRepository.class.getMethod("findProtected",Collection.class).getAnnotation(Query.class).value(),MetricReference.class);
   session.createSelectionQuery(DataMetricRepository.class.getMethod("countByDirectoryIdIn",Collection.class).getAnnotation(Query.class).value(),DataMetricRepository.DirectoryResourceCount.class);
   session.createSelectionQuery("select m from DataMetric m where m.id in (select r.metricId from MetricReference r where r.scope='CURRENT' and r.resourceKind=cn.superhuang.data.scalpel.business.metric.domain.MetricDefinition$ResourceKind.MODEL and r.referencePath='binding.modelId')",DataMetric.class);
  }finally{StandardServiceRegistryBuilder.destroy(registry);}
 }
}

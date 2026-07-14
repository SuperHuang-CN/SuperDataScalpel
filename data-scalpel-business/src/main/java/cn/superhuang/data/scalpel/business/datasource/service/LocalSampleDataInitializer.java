package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DatabaseType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.directory.domain.Directory;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.repository.DirectoryRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Set;

/** Inserts non-destructive demonstration records only for a developer's local profile. */
@Configuration(proxyBeanMethods = false)
@Profile("local")
class LocalSampleDataInitializer {

    @Bean
    ApplicationRunner initializeLocalSampleData(DirectoryRepository directoryRepository, DataSourceRepository dataSourceRepository) {
        return arguments -> {
            Directory root = findOrCreateDirectory(directoryRepository, null, "示例数据源", 900);
            Directory business = findOrCreateDirectory(directoryRepository, root.getId(), "业务系统", 910);
            Directory warehouse = findOrCreateDirectory(directoryRepository, root.getId(), "数据仓库", 920);

            createDataSourceIfMissing(
                    dataSourceRepository, "sample_business_postgresql", "示例业务 PostgreSQL", business,
                    Set.of(DataSourcePurpose.SOURCE), DatabaseType.POSTGRESQL, "业务系统示例连接", "business_demo", 5432
            );
            createDataSourceIfMissing(
                    dataSourceRepository, "sample_warehouse_postgresql", "示例数仓 PostgreSQL", warehouse,
                    Set.of(DataSourcePurpose.STORAGE), DatabaseType.POSTGRESQL, "数据仓库示例连接", "warehouse_demo", 5432
            );
            createDataSourceIfMissing(
                    dataSourceRepository, "sample_unclassified_mysql", "示例未分类 MySQL", null,
                    Set.of(DataSourcePurpose.SOURCE, DataSourcePurpose.DISTRIBUTION), DatabaseType.MYSQL,
                    "未分类示例连接", "integration_demo", 3306
            );
        };
    }

    private Directory findOrCreateDirectory(
            DirectoryRepository repository,
            java.util.UUID parentId,
            String name,
            int sortOrder
    ) {
        return repository.findAllByScopeOrderBySortOrderAscNameAsc(DirectoryScope.DATA_SOURCE).stream()
                .filter(directory -> java.util.Objects.equals(directory.getParentId(), parentId))
                .filter(directory -> directory.getName().equals(name))
                .findFirst()
                .orElseGet(() -> repository.saveAndFlush(Directory.create(
                        DirectoryScope.DATA_SOURCE, parentId, name, sortOrder, "本地开发样例目录"
                )));
    }

    private void createDataSourceIfMissing(
            DataSourceRepository repository,
            String code,
            String name,
            Directory directory,
            Set<DataSourcePurpose> purposes,
            DatabaseType databaseType,
            String description,
            String databaseName,
            int port
    ) {
        if (repository.existsByCode(code)) {
            return;
        }
        repository.save(DataSource.create(
                code,
                name,
                directory == null ? null : directory.getId(),
                purposes,
                databaseType,
                true,
                description,
                DataSourceConnection.create("127.0.0.1", port, databaseName, "public", "demo", null, java.util.Map.of())
        ));
    }
}

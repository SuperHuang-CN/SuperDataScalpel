package cn.superhuang.data.scalpel.business.datasource.repository;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.search.SearchRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DataSourceRepository extends SearchRepository<DataSource, UUID> {

    boolean existsByCode(String code);

    boolean existsByDirectoryId(UUID directoryId);

    @Query("""
            select new cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository$DirectoryResourceCount(
                    source.directoryId, count(source))
            from DataSource source
            where source.directoryId in :directoryIds
            group by source.directoryId
            """)
    List<DirectoryResourceCount> countByDirectoryIdIn(@Param("directoryIds") Collection<UUID> directoryIds);

    record DirectoryResourceCount(UUID directoryId, long resourceCount) {
    }
}

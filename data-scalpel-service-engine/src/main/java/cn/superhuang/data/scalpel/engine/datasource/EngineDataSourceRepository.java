package cn.superhuang.data.scalpel.engine.datasource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface EngineDataSourceRepository extends JpaRepository<EngineDataSource, UUID> {

    Optional<EngineDataSource> findByEngineCodeAndDataSourceId(String engineCode, UUID dataSourceId);

    List<EngineDataSource> findAllByEngineCode(String engineCode);
}

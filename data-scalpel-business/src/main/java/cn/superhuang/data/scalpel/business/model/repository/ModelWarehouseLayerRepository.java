package cn.superhuang.data.scalpel.business.model.repository;

import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayer;
import cn.superhuang.data.scalpel.search.SearchRepository;

import java.util.Optional;
import java.util.UUID;

public interface ModelWarehouseLayerRepository extends SearchRepository<ModelWarehouseLayer, UUID> {

    Optional<ModelWarehouseLayer> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);
}

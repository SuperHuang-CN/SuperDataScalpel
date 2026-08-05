package cn.superhuang.data.scalpel.business.model.repository;

import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayerInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ModelWarehouseLayerInputRepository extends JpaRepository<ModelWarehouseLayerInput, UUID> {

    List<ModelWarehouseLayerInput> findAllByTargetLayerId(UUID targetLayerId);

    List<ModelWarehouseLayerInput> findAllByTargetLayerIdIn(Collection<UUID> targetLayerIds);

    boolean existsByInputLayerIdAndTargetLayerIdNot(UUID inputLayerId, UUID targetLayerId);

    @Modifying
    @Query("delete from ModelWarehouseLayerInput relation where relation.targetLayerId = :targetLayerId")
    void deleteAllByTargetLayerId(@Param("targetLayerId") UUID targetLayerId);

    @Query("""
            select new cn.superhuang.data.scalpel.business.model.repository.ModelWarehouseLayerInputRepository$InputReferenceCount(
                    relation.inputLayerId, count(distinct relation.targetLayerId))
            from ModelWarehouseLayerInput relation
            where relation.inputLayerId in :inputLayerIds
              and relation.targetLayerId <> relation.inputLayerId
            group by relation.inputLayerId
            """)
    List<InputReferenceCount> countOtherTargetLayersByInputLayerIdIn(
            @Param("inputLayerIds") Collection<UUID> inputLayerIds
    );

    record InputReferenceCount(UUID inputLayerId, long layerCount) {
    }
}

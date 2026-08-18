package cn.superhuang.data.scalpel.business.quality.repository;

import cn.superhuang.data.scalpel.business.quality.domain.ModelQualityRule;
import cn.superhuang.data.scalpel.search.SearchRepository;

import java.util.List;
import java.util.UUID;

public interface ModelQualityRuleRepository extends SearchRepository<ModelQualityRule, UUID> {

    List<ModelQualityRule> findAllByModelIdOrderByCreatedAtAsc(UUID modelId);

    List<ModelQualityRule> findAllByReferenceModelIdOrderByCreatedAtAsc(UUID referenceModelId);

    void deleteAllByModelId(UUID modelId);
}

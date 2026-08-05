package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.DataMaskingRule;
import cn.superhuang.data.scalpel.search.SearchRepository;

import java.util.UUID;

public interface DataMaskingRuleRepository extends SearchRepository<DataMaskingRule, UUID> {

    boolean existsByCode(String code);
}

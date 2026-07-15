package cn.superhuang.data.scalpel.business.service.repository;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.search.SearchRepository;

import java.util.UUID;

public interface ServiceEngineRepository extends SearchRepository<ServiceEngine, UUID> {

    boolean existsByCode(String code);
}

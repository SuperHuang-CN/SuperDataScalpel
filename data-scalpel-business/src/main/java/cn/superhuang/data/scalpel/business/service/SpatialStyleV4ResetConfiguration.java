package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.service.domain.SpatialDataServiceDefinition;
import cn.superhuang.data.scalpel.business.service.domain.SpatialGeometryFamily;
import cn.superhuang.data.scalpel.business.service.repository.SpatialDataServiceDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/** One-time, local-only reset of pre-V4 drafts; never changes GeoServer styles. */
@Configuration(proxyBeanMethods = false)
class SpatialStyleV4ResetConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SpatialStyleV4ResetConfiguration.class);

    @Bean
    @Order(-70)
    ApplicationRunner resetLegacySpatialStyleDocuments(
            SpatialDataServiceDefinitionRepository definitionRepository,
            DataModelFieldRepository fieldRepository,
            ObjectMapper objectMapper
    ) {
        return arguments -> {
            int resetCount = 0;
            for (SpatialDataServiceDefinition definition : definitionRepository.findAll()) {
                if (!requiresReset(definition, objectMapper)) continue;
                SpatialGeometryFamily family = geometryFamily(definition, fieldRepository);
                if (family == SpatialGeometryFamily.GENERIC) continue;
                if (family == null) {
                    log.warn("跳过无法生成 V4 默认样式的空间服务定义：{}", definition.getDataServiceId());
                    continue;
                }
                String json = objectMapper.writeValueAsString(
                        SpatialStyleDocument.defaults(SpatialDataServiceStyleService.coreFamily(family))
                );
                definition.resetCartographyDocument(json);
                definitionRepository.save(definition);
                resetCount++;
            }
            if (resetCount > 0) log.info("已将 {} 个旧在线制图草稿重置为 V4 默认样式；线上样式未改变", resetCount);
        };
    }

    static boolean requiresReset(SpatialDataServiceDefinition definition, ObjectMapper objectMapper) {
        if (definition.getStyleMode() == cn.superhuang.data.scalpel.business.service.domain.SpatialStyleMode.SIMPLE) {
            return true;
        }
        if (definition.getSimpleStyleJson() != null && !definition.getSimpleStyleJson().isBlank()) return true;
        String json = definition.getStyleDocumentJson();
        if (json == null || json.isBlank()) return true;
        try {
            if (objectMapper.readTree(json).path("schemaVersion").asInt(-1)
                    < SpatialStyleDocument.CURRENT_SCHEMA_VERSION) return true;
            objectMapper.readValue(json, SpatialStyleDocument.class);
            return false;
        } catch (Exception exception) {
            return true;
        }
    }

    private static SpatialGeometryFamily geometryFamily(
            SpatialDataServiceDefinition definition, DataModelFieldRepository fieldRepository
    ) {
        List<DataModelField> geometries = fieldRepository
                .findAllByModelIdOrderBySortOrderAscCodeAsc(definition.getModelId()).stream()
                .filter(field -> field.getGeometry() != null)
                .toList();
        return geometries.size() == 1 ? SpatialGeometryFamily.from(geometries.getFirst().getGeometry().kind()) : null;
    }
}

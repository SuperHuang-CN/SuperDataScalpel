package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayer;
import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayerInput;
import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayerInputPolicy;
import cn.superhuang.data.scalpel.business.model.repository.ModelWarehouseLayerInputRepository;
import cn.superhuang.data.scalpel.business.model.repository.ModelWarehouseLayerRepository;
import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfiguration;
import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfigurationDefinition;
import cn.superhuang.data.scalpel.business.system.configuration.repository.SystemConfigurationRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
class ModelWarehouseLayerInitializer {

    @Bean
    @Order(10)
    ApplicationRunner initializeDefaultModelWarehouseLayers(
            ModelWarehouseLayerRepository layerRepository,
            SystemConfigurationRepository configurationRepository,
            PlatformTransactionManager transactionManager
    ) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return arguments -> transactionTemplate.executeWithoutResult(status -> {
            SystemConfiguration marker = configurationRepository.findByConfigKeyForUpdate(
                            SystemConfigurationDefinition.MODEL_WAREHOUSE_LAYERS_DEFAULTS_INITIALIZED.getConfigKey()
                    )
                    .orElseThrow(() -> new IllegalStateException("数仓分层初始化标记不存在"));
            if (Boolean.parseBoolean(marker.getConfigValue())) {
                return;
            }
            for (DefaultLayer definition : defaults()) {
                if (layerRepository.findByCode(definition.code()).isEmpty()) {
                    layerRepository.save(ModelWarehouseLayer.create(
                            definition.code(),
                            definition.name(),
                            definition.description(),
                            definition.color(),
                            definition.sortOrder()
                    ));
                }
            }
            marker.updateValue("true");
            layerRepository.flush();
            configurationRepository.saveAndFlush(marker);
        });
    }

    @Bean
    @Order(11)
    ApplicationRunner initializeDefaultModelWarehouseLayerRules(
            ModelWarehouseLayerRepository layerRepository,
            ModelWarehouseLayerInputRepository inputRepository,
            SystemConfigurationRepository configurationRepository,
            PlatformTransactionManager transactionManager
    ) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return arguments -> transactionTemplate.executeWithoutResult(status -> {
            SystemConfiguration marker = configurationRepository.findByConfigKeyForUpdate(
                            SystemConfigurationDefinition.MODEL_WAREHOUSE_LAYER_RULES_INITIALIZED.getConfigKey()
                    )
                    .orElseThrow(() -> new IllegalStateException("数仓分层建模规范初始化标记不存在"));
            if (Boolean.parseBoolean(marker.getConfigValue())) {
                return;
            }

            Map<String, ModelWarehouseLayer> layersByCode = new LinkedHashMap<>();
            for (ModelWarehouseLayer layer : layerRepository.findAll()) {
                layersByCode.put(layer.getCode(), layer);
            }
            for (DefaultLayerRule definition : defaultRules()) {
                ModelWarehouseLayer layer = layersByCode.get(definition.code());
                if (layer == null) {
                    continue;
                }
                boolean initializeAllowedInputs = !layer.hasConfiguredInputLayerPolicy();
                String prefix = layer.getModelCodePrefix() == null
                        ? definition.modelCodePrefix()
                        : layer.getModelCodePrefix();
                ModelWarehouseLayerInputPolicy policy = initializeAllowedInputs
                        ? ModelWarehouseLayerInputPolicy.ALLOW_LIST
                        : layer.getInputLayerPolicy();
                layer.configureModelingRules(prefix, policy);
                layerRepository.save(layer);

                if (initializeAllowedInputs) {
                    inputRepository.deleteAllByTargetLayerId(layer.getId());
                    List<ModelWarehouseLayerInput> relations = definition.allowedInputCodes().stream()
                            .map(layersByCode::get)
                            .filter(java.util.Objects::nonNull)
                            .map(inputLayer -> ModelWarehouseLayerInput.create(layer.getId(), inputLayer.getId()))
                            .toList();
                    if (!relations.isEmpty()) {
                        inputRepository.saveAll(relations);
                    }
                }
            }
            for (ModelWarehouseLayer layer : layersByCode.values()) {
                if (!layer.hasConfiguredInputLayerPolicy()) {
                    layer.configureModelingRules(
                            layer.getModelCodePrefix(),
                            ModelWarehouseLayerInputPolicy.UNRESTRICTED
                    );
                    layerRepository.save(layer);
                }
            }

            marker.updateValue("true");
            inputRepository.flush();
            layerRepository.flush();
            configurationRepository.saveAndFlush(marker);
        });
    }

    private static List<DefaultLayer> defaults() {
        return List.of(
                new DefaultLayer("ODS", "贴源数据层", "保存从业务系统接入的原始或轻度清洗数据。", "#8C8C8C", 10),
                new DefaultLayer("DIM", "公共维度层", "保存跨主题复用的公共维度数据。", "#722ED1", 20),
                new DefaultLayer("DWD", "明细数据层", "保存标准化、清洗后的业务明细数据。", "#1677FF", 30),
                new DefaultLayer("DWS", "汇总数据层", "保存面向主题的公共汇总数据。", "#13C2C2", 40),
                new DefaultLayer("ADS", "应用数据层", "保存直接服务于应用、报表或指标的数据。", "#52C41A", 50)
        );
    }

    private static List<DefaultLayerRule> defaultRules() {
        return List.of(
                new DefaultLayerRule("ODS", "ods_", List.of()),
                new DefaultLayerRule("DIM", "dim_", List.of("ODS", "DIM")),
                new DefaultLayerRule("DWD", "dwd_", List.of("ODS", "DIM", "DWD")),
                new DefaultLayerRule("DWS", "dws_", List.of("DWD", "DIM", "DWS")),
                new DefaultLayerRule("ADS", "ads_", List.of("DWD", "DWS", "DIM", "ADS"))
        );
    }

    private record DefaultLayer(
            String code,
            String name,
            String description,
            String color,
            int sortOrder
    ) {
    }

    private record DefaultLayerRule(
            String code,
            String modelCodePrefix,
            List<String> allowedInputCodes
    ) {
    }
}

package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import cn.superhuang.data.scalpel.business.cartography.sld.SpatialSldCompiler;
import cn.superhuang.data.scalpel.business.cartography.validation.SpatialStyleDocumentValidator;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeployment;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeploymentStatus;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineType;
import cn.superhuang.data.scalpel.business.service.domain.SpatialDataServiceDefinition;
import cn.superhuang.data.scalpel.business.service.domain.SpatialGeometryFamily;
import cn.superhuang.data.scalpel.business.service.domain.SpatialStyleMode;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceDeploymentRepository;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineRepository;
import cn.superhuang.data.scalpel.business.service.repository.SpatialDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateSpatialStyleRequest;
import cn.superhuang.data.scalpel.business.service.web.request.QuerySpatialStyleSldRequest;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialStyleSldResponse;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialDataServiceStyleResponse;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialStyleFieldResponse;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class SpatialDataServiceStyleService {

    private final DataServiceRepository serviceRepository;
    private final SpatialDataServiceDefinitionRepository definitionRepository;
    private final DataServiceDeploymentRepository deploymentRepository;
    private final ServiceEngineRepository engineRepository;
    private final DataModelFieldRepository fieldRepository;
    private final SpatialSldValidator sldValidator;
    private final GeoServerClient geoServerClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readTransactionTemplate;
    private final SpatialStyleDocumentValidator documentValidator = new SpatialStyleDocumentValidator();
    private final SpatialSldCompiler sldCompiler = new SpatialSldCompiler();

    public SpatialDataServiceStyleService(
            DataServiceRepository serviceRepository,
            SpatialDataServiceDefinitionRepository definitionRepository,
            DataServiceDeploymentRepository deploymentRepository,
            ServiceEngineRepository engineRepository,
            DataModelFieldRepository fieldRepository,
            SpatialSldValidator sldValidator,
            GeoServerClient geoServerClient,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.serviceRepository = serviceRepository;
        this.definitionRepository = definitionRepository;
        this.deploymentRepository = deploymentRepository;
        this.engineRepository = engineRepository;
        this.fieldRepository = fieldRepository;
        this.sldValidator = sldValidator;
        this.geoServerClient = geoServerClient;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate.setReadOnly(true);
    }

    public SpatialDataServiceStyleResponse get(UUID serviceId) {
        return required(readTransactionTemplate.execute(status -> response(serviceId)));
    }

    /** Compiles a draft without changing stored styles or contacting GeoServer. */
    public SpatialStyleSldResponse querySld(UUID serviceId, QuerySpatialStyleSldRequest request) {
        if (writeDocument(request.styleDocument()).getBytes(StandardCharsets.UTF_8).length > 256 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "样式文档不能超过 256KB");
        }
        SldCompilationInput input = required(readTransactionTemplate.execute(status -> {
            StyleContext context = context(serviceId);
            if (context.family() == SpatialGeometryFamily.GENERIC) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "通用 Geometry 使用内置 generic 样式或上传 SLD");
            }
            return new SldCompilationInput(GeoServerClient.layerName(context.service().getCode()),
                    coreFamily(context.family()), validateDocument(request.styleDocument(), context));
        }));
        return new SpatialStyleSldResponse(sldCompiler.compile(input.layerName(), input.family(), input.document()));
    }

    public SpatialDataServiceStyleResponse updateCartography(UUID serviceId, UpdateSpatialStyleRequest request) {
        return required(transactionTemplate.execute(status -> {
            StyleContext context = context(serviceId);
            SpatialStyleMode mode = request.mode() == null ? SpatialStyleMode.CARTOGRAPHY : request.mode();
            if (mode == SpatialStyleMode.UPLOADED_SLD) {
                if (request.styleDocument() != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "激活上传 SLD 时不能提交在线制图文档");
                }
                try {
                    if (context.definition().activateUploadedSld(context.deployed())) {
                        definitionRepository.saveAndFlush(context.definition());
                    }
                } catch (IllegalStateException exception) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
                }
                return response(context, retainedDocument(context));
            }
            if (mode != SpatialStyleMode.CARTOGRAPHY) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的空间样式模式");
            }
            if (request.styleDocument() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "在线制图样式文档不能为空");
            }
            SpatialStyleDocument document = validateDocument(request.styleDocument(), context);
            String json = writeDocument(document);
            if (json.getBytes(StandardCharsets.UTF_8).length > 256 * 1024) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "样式文档不能超过 256KB");
            }
            if (context.definition().saveStyleDocument(json, context.deployed())) {
                definitionRepository.saveAndFlush(context.definition());
            }
            return response(context, document);
        }));
    }

    public SpatialDataServiceStyleResponse upload(UUID serviceId, MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file == null ? null : file.getBytes();
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "读取 SLD 文件失败", exception);
        }
        return required(transactionTemplate.execute(status -> {
            StyleContext context = context(serviceId);
            SpatialSldValidator.ValidatedSld validated = sldValidator.validate(
                    file == null ? null : file.getOriginalFilename(), bytes, context.family()
            );
            context.definition().saveUploadedSld(validated.fileName(), validated.text(), context.deployed());
            definitionRepository.saveAndFlush(context.definition());
            return response(context, retainedDocument(context));
        }));
    }

    public SpatialDataServiceStyleResponse apply(UUID serviceId) {
        StyleDeployment deployment = required(transactionTemplate.execute(status -> {
            StyleContext context = context(serviceId);
            if (!context.deployed()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "空间服务尚未部署，启用服务时会自动应用当前样式");
            }
            context.definition().beginStyleSync();
            definitionRepository.saveAndFlush(context.definition());
            return deployment(context);
        }));
        String failure = null;
        try {
            if (deployment.sldText() != null) {
                geoServerClient.upsertStyle(deployment.engine(), deployment.styleName(), deployment.sldText());
            }
            geoServerClient.bindLayerStyle(deployment.engine(), deployment.serviceCode(), deployment.styleName());
        } catch (RuntimeException exception) {
            failure = safeMessage(exception);
        }
        String finalFailure = failure;
        SpatialDataServiceStyleResponse response = required(transactionTemplate.execute(status -> {
            SpatialDataServiceDefinition definition = requireDefinition(serviceId);
            if (finalFailure == null && deployment.sldText() == null) definition.styleRemoved();
            else if (finalFailure == null) definition.completeStyleSync(deployment.styleVersion());
            else definition.failStyleSync(deployment.styleVersion(), finalFailure);
            definitionRepository.saveAndFlush(definition);
            return response(serviceId);
        }));
        if (failure != null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, failure);
        }
        return response;
    }

    /** Called from the spatial service enable transaction before the external GeoServer request. */
    public StyleDeployment beginDeployment(UUID serviceId, String serviceCode) {
        StyleContext context = context(serviceId);
        context.definition().beginStyleSync();
        definitionRepository.saveAndFlush(context.definition());
        return deployment(context, serviceCode);
    }

    /** Called in the short completion transaction after an enable attempt. */
    public void completeDeployment(UUID serviceId, int styleVersion, String failure) {
        SpatialDataServiceDefinition definition = requireDefinition(serviceId);
        if (failure == null && styleVersion < 0) definition.styleRemoved();
        else if (failure == null) definition.completeStyleSync(styleVersion);
        else definition.failStyleSync(styleVersion, failure);
        definitionRepository.saveAndFlush(definition);
    }

    public void markRemoved(UUID serviceId) {
        SpatialDataServiceDefinition definition = requireDefinition(serviceId);
        definition.styleRemoved();
        definitionRepository.saveAndFlush(definition);
    }

    private SpatialDataServiceStyleResponse response(UUID serviceId) {
        StyleContext context = context(serviceId);
        return response(context, retainedDocument(context));
    }

    private SpatialStyleDocument retainedDocument(StyleContext context) {
        return context.family() == SpatialGeometryFamily.GENERIC ? null : readDocument(context);
    }

    private SpatialDataServiceStyleResponse response(StyleContext context, SpatialStyleDocument document) {
        SpatialDataServiceDefinition definition = context.definition();
        SpatialStyleDocument defaults = context.family() == SpatialGeometryFamily.GENERIC
                ? null : SpatialStyleDocument.defaults(coreFamily(context.family()));
        Integer fileSize = definition.getUploadedSldText() == null
                ? null : definition.getUploadedSldText().getBytes(StandardCharsets.UTF_8).length;
        return new SpatialDataServiceStyleResponse(
                definition.getStyleMode() == SpatialStyleMode.UPLOADED_SLD
                        ? SpatialStyleMode.UPLOADED_SLD : SpatialStyleMode.CARTOGRAPHY,
                context.geometryKind(), context.family(), context.family() != SpatialGeometryFamily.GENERIC,
                document, defaults, fieldResponses(context.fields()), definition.getSldFileName(), fileSize,
                definition.getUploadedSldText(),
                definition.getStyleVersion(), definition.getAppliedStyleVersion(),
                definition.getStyleSyncStatus(), definition.getStyleSyncError(),
                definition.getStyleAppliedAt(), context.deployed()
        );
    }

    private StyleDeployment deployment(StyleContext context) {
        return deployment(context, context.service().getCode());
    }

    private StyleDeployment deployment(StyleContext context, String serviceCode) {
        if (context.definition().getStyleMode() == SpatialStyleMode.SIMPLE
                && context.family() == SpatialGeometryFamily.GENERIC) {
            return new StyleDeployment(context.engine(), serviceCode, "generic", null, -1);
        }
        if (context.definition().getStyleMode() == SpatialStyleMode.CARTOGRAPHY
                && context.family() == SpatialGeometryFamily.GENERIC) {
            return new StyleDeployment(context.engine(), serviceCode, "generic", null, -1);
        }
        String styleName = GeoServerClient.styleName(serviceCode);
        String sld = context.definition().getStyleMode() == SpatialStyleMode.UPLOADED_SLD
                ? context.definition().getUploadedSldText()
                : sldCompiler.compile(
                        GeoServerClient.layerName(serviceCode), coreFamily(context.family()), readDocument(context)
                );
        return new StyleDeployment(
                context.engine(), serviceCode, styleName, sld, context.definition().getStyleVersion()
        );
    }

    private StyleContext context(UUID serviceId) {
        DataService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据服务不存在"));
        if (service.getType() != DataServiceType.SPATIAL_SERVICE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有空间服务支持在线配图");
        }
        SpatialDataServiceDefinition definition = requireDefinition(serviceId);
        List<DataModelField> fields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(definition.getModelId());
        List<DataModelField> geometryFields = fields.stream()
                .filter(field -> field.getGeometry() != null)
                .toList();
        if (geometryFields.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "空间模型必须且只能包含一个 Geometry 字段");
        }
        GeometryKind geometryKind = geometryFields.getFirst().getGeometry().kind();
        ServiceEngine engine = engineRepository.findById(service.getEngineId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "空间服务关联的 GeoServer 不存在"));
        if (engine.getType() != ServiceEngineType.GEOSERVER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "空间服务未绑定 GeoServer 引擎");
        }
        DataServiceDeployment deployment = deploymentRepository.findByDataServiceId(serviceId).orElse(null);
        boolean deployed = service.getStatus() == DataServiceStatus.ENABLED
                && deployment != null && deployment.getStatus() == DataServiceDeploymentStatus.DEPLOYED;
        return new StyleContext(
                service, definition, engine, geometryKind, SpatialGeometryFamily.from(geometryKind), fields, deployed
        );
    }

    private SpatialDataServiceDefinition requireDefinition(UUID serviceId) {
        return definitionRepository.findByDataServiceId(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "空间服务定义不存在"));
    }

    private SpatialStyleDocument readDocument(StyleContext context) {
        SpatialDataServiceDefinition definition = context.definition();
        SpatialStyleDocument document;
        try {
            if (definition.getStyleDocumentJson() != null && !definition.getStyleDocumentJson().isBlank()) {
                document = objectMapper.readValue(definition.getStyleDocumentJson(), SpatialStyleDocument.class);
            } else {
                document = SpatialStyleDocument.defaults(coreFamily(context.family()));
            }
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已保存的在线制图样式无法读取", exception);
        }
        return validateDocument(document, context);
    }

    private SpatialStyleDocument validateDocument(SpatialStyleDocument document, StyleContext context) {
        try {
            return documentValidator.validate(document, coreFamily(context.family()), coreFields(context.fields()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private String writeDocument(SpatialStyleDocument document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (Exception exception) {
            throw new IllegalStateException("保存在线制图样式失败", exception);
        }
    }

    static List<SpatialStyleDocument.Field> coreFields(List<DataModelField> fields) {
        return fields.stream().filter(field -> field.getGeometry() == null).map(field -> {
            PlatformDataType type = field.getFieldType();
            boolean numeric = isNumeric(type);
            SpatialStyleDocument.ValueType valueType = switch (type) {
                case STRING -> SpatialStyleDocument.ValueType.STRING;
                case BOOLEAN -> SpatialStyleDocument.ValueType.BOOLEAN;
                case BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, DECIMAL -> SpatialStyleDocument.ValueType.NUMBER;
                default -> null;
            };
            return new SpatialStyleDocument.Field(
                    field.getCode(), field.getName(), type.name(), valueType,
                    valueType != null, numeric, type != PlatformDataType.BINARY && type != PlatformDataType.GEOMETRY
            );
        }).toList();
    }

    static List<SpatialStyleFieldResponse> fieldResponses(List<DataModelField> fields) {
        return coreFields(fields).stream().map(field -> new SpatialStyleFieldResponse(
                field.code(), field.name(), field.dataType(), field.valueType(),
                field.uniqueValueSupported(), field.classBreaksSupported(), field.labelSupported()
        )).toList();
    }

    private static boolean isNumeric(PlatformDataType type) {
        return switch (type) {
            case BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, DECIMAL -> true;
            default -> false;
        };
    }

    static SpatialStyleDocument.GeometryFamily coreFamily(SpatialGeometryFamily family) {
        return SpatialStyleDocument.GeometryFamily.valueOf(family.name());
    }

    private static String safeMessage(RuntimeException exception) {
        if (exception instanceof ResponseStatusException statusException && statusException.getReason() != null) {
            return statusException.getReason();
        }
        if (exception.getMessage() == null || exception.getMessage().isBlank()) return "GeoServer 样式同步失败";
        return exception.getMessage().substring(0, Math.min(500, exception.getMessage().length()));
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalStateException("事务未返回结果");
        return value;
    }

    private record SldCompilationInput(
            String layerName,
            SpatialStyleDocument.GeometryFamily family,
            SpatialStyleDocument document
    ) {
    }

    private record StyleContext(
            DataService service,
            SpatialDataServiceDefinition definition,
            ServiceEngine engine,
            GeometryKind geometryKind,
            SpatialGeometryFamily family,
            List<DataModelField> fields,
            boolean deployed
    ) {
    }

    public record StyleDeployment(
            ServiceEngine engine,
            String serviceCode,
            String styleName,
            String sldText,
            int styleVersion
    ) {
    }
}

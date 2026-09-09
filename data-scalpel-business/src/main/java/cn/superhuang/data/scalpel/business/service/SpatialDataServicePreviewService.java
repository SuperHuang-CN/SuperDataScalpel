package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import cn.superhuang.data.scalpel.business.cartography.sld.SpatialSldCompiler;
import cn.superhuang.data.scalpel.business.cartography.validation.SpatialStyleDocumentValidator;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeployment;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeploymentStatus;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineType;
import cn.superhuang.data.scalpel.business.service.domain.SpatialDataServiceDefinition;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceDeploymentRepository;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineRepository;
import cn.superhuang.data.scalpel.business.service.repository.SpatialDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialDataServicePreviewResponse;
import cn.superhuang.data.scalpel.business.service.web.request.RenderSpatialStylePreviewRequest;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
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
public class SpatialDataServicePreviewService {

    private static final int MINIMUM_WIDTH = 256;
    private static final int MAXIMUM_WIDTH = 1_600;
    private static final int MINIMUM_HEIGHT = 256;
    private static final int MAXIMUM_HEIGHT = 1_200;
    private static final double WEB_MERCATOR_LIMIT = 20_037_508.342789244d;
    private static final String DISPLAY_CRS = "EPSG:3857";

    private final DataServiceRepository dataServiceRepository;
    private final SpatialDataServiceDefinitionRepository definitionRepository;
    private final DataServiceDeploymentRepository deploymentRepository;
    private final ServiceEngineRepository engineRepository;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final GeoServerClient geoServerClient;
    private final SpatialSldValidator sldValidator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final SpatialStyleDocumentValidator documentValidator = new SpatialStyleDocumentValidator();
    private final SpatialSldCompiler sldCompiler = new SpatialSldCompiler();

    public SpatialDataServicePreviewService(
            DataServiceRepository dataServiceRepository,
            SpatialDataServiceDefinitionRepository definitionRepository,
            DataServiceDeploymentRepository deploymentRepository,
            ServiceEngineRepository engineRepository,
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            GeoServerClient geoServerClient,
            SpatialSldValidator sldValidator,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.dataServiceRepository = dataServiceRepository;
        this.definitionRepository = definitionRepository;
        this.deploymentRepository = deploymentRepository;
        this.engineRepository = engineRepository;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.geoServerClient = geoServerClient;
        this.sldValidator = sldValidator;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setReadOnly(true);
    }

    public SpatialDataServicePreviewResponse inspect(UUID serviceId) {
        PreviewPreparation preparation = snapshot(serviceId);
        String unavailableReason = unavailableReason(preparation);
        if (unavailableReason != null) {
            return response(preparation, false, unavailableReason, List.of());
        }
        GeoServerClient.LayerPreview layer = geoServerClient.inspectLayer(
                        preparation.engine(), preparation.dataSourceId(), preparation.serviceCode()
                )
                .orElse(null);
        if (layer == null) {
            return response(
                    preparation, false,
                    "GeoServer 中未找到已发布图层，请停用后重新启用服务以修复部署",
                    List.of()
            );
        }
        if (layer.initialBounds().size() != 4) {
            return response(
                    preparation, false,
                    "GeoServer 图层没有有效空间边界，请确认物理表中存在有效 Geometry 数据",
                    List.of()
            );
        }
        return response(preparation, true, null, layer.initialBounds());
    }

    public byte[] render(UUID serviceId, String bbox, int width, int height) {
        PreviewViewport viewport = viewport(bbox, width, height);
        PreviewPreparation preparation = snapshot(serviceId);
        String unavailableReason = unavailableReason(preparation);
        if (unavailableReason != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, unavailableReason);
        }
        return geoServerClient.renderWms(
                preparation.engine(), preparation.qualifiedLayerName(),
                viewport.bbox(), viewport.width(), viewport.height()
        );
    }

    public byte[] renderDraft(UUID serviceId, RenderSpatialStylePreviewRequest request) {
        if (objectMapper.writeValueAsString(request.styleDocument()).getBytes(StandardCharsets.UTF_8).length > 256 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "样式文档不能超过 256KB");
        }
        PreviewViewport viewport = viewport(request.bbox(), request.width(), request.height());
        PreviewPreparation preparation = snapshot(serviceId);
        String unavailableReason = unavailableReason(preparation);
        if (unavailableReason != null) throw new ResponseStatusException(HttpStatus.CONFLICT, unavailableReason);
        try {
            SpatialStyleDocument normalized = documentValidator.validate(
                    request.styleDocument(),
                    SpatialStyleDocument.GeometryFamily.valueOf(preparation.geometryFamily().name()),
                    SpatialDataServiceStyleService.coreFields(preparation.fields())
            );
            String sld = sldCompiler.compile(
                    GeoServerClient.layerName(preparation.serviceCode()),
                    SpatialStyleDocument.GeometryFamily.valueOf(preparation.geometryFamily().name()),
                    normalized
            );
            return geoServerClient.renderWms(
                    preparation.engine(), preparation.qualifiedLayerName(), viewport.bbox(),
                    viewport.width(), viewport.height(), sld
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    public byte[] renderUploadedDraft(
            UUID serviceId,
            MultipartFile file,
            String bbox,
            int width,
            int height
    ) {
        PreviewViewport viewport = viewport(bbox, width, height);
        PreviewPreparation preparation = snapshot(serviceId);
        String unavailableReason = unavailableReason(preparation);
        if (unavailableReason != null) throw new ResponseStatusException(HttpStatus.CONFLICT, unavailableReason);
        try {
            byte[] bytes = file == null ? null : file.getBytes();
            SpatialSldValidator.ValidatedSld validated = sldValidator.validate(
                    file == null ? null : file.getOriginalFilename(), bytes, preparation.geometryFamily()
            );
            String previewSld = sldValidator.forPreview(
                    validated,
                    GeoServerClient.layerName(preparation.serviceCode())
            );
            return geoServerClient.renderWms(
                    preparation.engine(), preparation.qualifiedLayerName(), viewport.bbox(),
                    viewport.width(), viewport.height(), previewSld
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "读取 SLD 预览文件失败", exception);
        }
    }

    public byte[] renderLegend(UUID serviceId) {
        PreviewPreparation preparation = snapshot(serviceId);
        String unavailableReason = unavailableReason(preparation);
        if (unavailableReason != null) throw new ResponseStatusException(HttpStatus.CONFLICT, unavailableReason);
        return geoServerClient.renderLegend(preparation.engine(), preparation.qualifiedLayerName());
    }

    private PreviewPreparation snapshot(UUID serviceId) {
        PreviewPreparation result = transactionTemplate.execute(status -> {
            DataService service = dataServiceRepository.findById(serviceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据服务不存在"));
            if (service.getType() != DataServiceType.SPATIAL_SERVICE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "只有空间服务支持服务预览");
            }
            SpatialDataServiceDefinition definition = definitionRepository.findByDataServiceId(serviceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "空间服务定义不存在"));
            DataModel model = modelRepository.findById(definition.getModelId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "空间服务关联的模型不存在"));
            ServiceEngine engine = engineRepository.findById(service.getEngineId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "空间服务关联的 GeoServer 不存在"));
            if (engine.getType() != ServiceEngineType.GEOSERVER) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "空间服务未绑定 GeoServer 引擎");
            }
            DataServiceDeployment deployment = deploymentRepository.findByDataServiceId(serviceId).orElse(null);
            String layerName = GeoServerClient.layerName(service.getCode());
            List<DataModelField> fields = fieldRepository
                    .findAllByModelIdOrderBySortOrderAscCodeAsc(definition.getModelId());
            List<DataModelField> geometryFields = fields.stream().filter(field -> field.getGeometry() != null).toList();
            if (geometryFields.size() != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "空间模型必须且只能包含一个 Geometry 字段");
            }
            GeometryKind geometryKind = geometryFields.getFirst().getGeometry().kind();
            return new PreviewPreparation(
                    service.getCode(), service.getStatus(), engine, model.getStorageDataSourceId(),
                    deployment == null ? null : deployment.getStatus(),
                    deployment == null ? null : deployment.getLastError(),
                    engine.getGeoServerWorkspace() + ":" + layerName,
                    cn.superhuang.data.scalpel.business.service.domain.SpatialGeometryFamily.from(geometryKind),
                    fields
            );
        });
        if (result == null) {
            throw new IllegalStateException("读取空间服务预览快照失败");
        }
        return result;
    }

    private static String unavailableReason(PreviewPreparation preparation) {
        if (!preparation.engine().isEnabled()) {
            return "GeoServer 引擎已停用，暂时无法预览";
        }
        if (preparation.serviceStatus() == DataServiceStatus.DISABLED) {
            return "空间服务已停用，请重新启用后预览";
        }
        if (preparation.deploymentStatus() == DataServiceDeploymentStatus.FAILED) {
            return preparation.deploymentError() == null
                    ? "空间服务部署失败，请重试启用"
                    : "空间服务部署失败：" + preparation.deploymentError();
        }
        if (preparation.deploymentStatus() == DataServiceDeploymentStatus.PENDING) {
            return "空间服务正在部署，请稍后刷新";
        }
        if (preparation.deploymentStatus() == DataServiceDeploymentStatus.REMOVING) {
            return "空间服务正在停用，请稍后刷新";
        }
        if (preparation.serviceStatus() != DataServiceStatus.ENABLED
                || preparation.deploymentStatus() != DataServiceDeploymentStatus.DEPLOYED) {
            return "空间服务尚未部署，请先启用";
        }
        return null;
    }

    private static SpatialDataServicePreviewResponse response(
            PreviewPreparation preparation,
            boolean available,
            String message,
            List<Double> initialBounds
    ) {
        return new SpatialDataServicePreviewResponse(
                available, message, preparation.qualifiedLayerName(), DISPLAY_CRS, initialBounds,
                new SpatialDataServicePreviewResponse.Limits(
                        MINIMUM_WIDTH, MAXIMUM_WIDTH, MINIMUM_HEIGHT, MAXIMUM_HEIGHT
                )
        );
    }

    private static PreviewViewport viewport(String bbox, int width, int height) {
        if (width < MINIMUM_WIDTH || width > MAXIMUM_WIDTH
                || height < MINIMUM_HEIGHT || height > MAXIMUM_HEIGHT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "图片宽度必须为 256–1600，高度必须为 256–1200"
            );
        }
        if (bbox == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bbox 不能为空");
        }
        String[] values = bbox.split(",", -1);
        if (values.length != 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bbox 必须为 minX,minY,maxX,maxY");
        }
        try {
            double minX = finite(values[0]);
            double minY = finite(values[1]);
            double maxX = finite(values[2]);
            double maxY = finite(values[3]);
            if (minX >= maxX || minY >= maxY
                    || Math.abs(minX) > WEB_MERCATOR_LIMIT || Math.abs(maxX) > WEB_MERCATOR_LIMIT
                    || Math.abs(minY) > WEB_MERCATOR_LIMIT || Math.abs(maxY) > WEB_MERCATOR_LIMIT) {
                throw new IllegalArgumentException("bbox 超出 Web Mercator 有效范围");
            }
            return new PreviewViewport(
                    minX + "," + minY + "," + maxX + "," + maxY,
                    width, height
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private static PreviewViewport viewport(List<Double> bbox, int width, int height) {
        if (bbox == null || bbox.size() != 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bbox 必须包含 minX,minY,maxX,maxY");
        }
        return viewport(
                bbox.get(0) + "," + bbox.get(1) + "," + bbox.get(2) + "," + bbox.get(3),
                width,
                height
        );
    }

    private static double finite(String value) {
        double number = Double.parseDouble(value.trim());
        if (!Double.isFinite(number)) throw new IllegalArgumentException("bbox 必须是有限数字");
        return number;
    }

    private record PreviewPreparation(
            String serviceCode,
            DataServiceStatus serviceStatus,
            ServiceEngine engine,
            UUID dataSourceId,
            DataServiceDeploymentStatus deploymentStatus,
            String deploymentError,
            String qualifiedLayerName,
            cn.superhuang.data.scalpel.business.service.domain.SpatialGeometryFamily geometryFamily,
            List<DataModelField> fields
    ) {
    }

    private record PreviewViewport(String bbox, int width, int height) {
    }
}

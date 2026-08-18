package cn.superhuang.data.scalpel.business.service.web.resource;

import cn.superhuang.data.scalpel.business.lineage.service.DataServiceLineageQueryService;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageGraphResponse;
import cn.superhuang.data.scalpel.business.service.DataServiceManagementService;
import cn.superhuang.data.scalpel.business.service.DataServiceRelatedModelService;
import cn.superhuang.data.scalpel.business.service.StandardDataServiceModelCandidateService;
import cn.superhuang.data.scalpel.business.service.web.request.CreateDataServiceRequest;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateDataServiceRequest;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateDataServiceDefinitionRequest;
import cn.superhuang.data.scalpel.business.service.web.request.SqlServiceTestRequest;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceDetailResponse;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceRelatedModelResponse;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceSummaryResponse;
import cn.superhuang.data.scalpel.business.service.web.response.SqlServiceTestResponse;
import cn.superhuang.data.scalpel.business.service.web.response.StandardDataServiceModelCandidateResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/data-services")
public class DataServiceResource {

    private final DataServiceManagementService service;
    private final StandardDataServiceModelCandidateService modelCandidateService;
    private final DataServiceRelatedModelService relatedModelService;
    private final DataServiceLineageQueryService lineageQueryService;

    public DataServiceResource(
            DataServiceManagementService service,
            StandardDataServiceModelCandidateService modelCandidateService,
            DataServiceRelatedModelService relatedModelService,
            DataServiceLineageQueryService lineageQueryService
    ) {
        this.service = service;
        this.modelCandidateService = modelCandidateService;
        this.relatedModelService = relatedModelService;
        this.lineageQueryService = lineageQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('service.view')")
    public PageResponse<DataServiceSummaryResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('service.view')")
    public DataServiceDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('service.create')")
    public DataServiceDetailResponse create(@Valid @RequestBody CreateDataServiceRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('service.update')")
    public DataServiceDetailResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDataServiceRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/actions/update-definition")
    @PreAuthorize("hasAuthority('service.update')")
    public DataServiceDetailResponse updateDefinition(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDataServiceDefinitionRequest request
    ) {
        return service.updateDefinition(id, request);
    }

    @GetMapping("/{id}/standard-model-candidates")
    @PreAuthorize("hasAuthority('service.view') and hasAuthority('model.view')")
    public PageResponse<StandardDataServiceModelCandidateResponse> standardModelCandidates(
            @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request,
            @RequestParam(defaultValue = "false") boolean includeUnavailable
    ) {
        return modelCandidateService.search(id, request, includeUnavailable);
    }

    @GetMapping("/{id}/related-models")
    @PreAuthorize("hasAuthority('service.view') and hasAuthority('model.view')")
    public List<DataServiceRelatedModelResponse> relatedModels(@PathVariable UUID id) {
        return relatedModelService.get(id);
    }

    @GetMapping("/{id}/lineage/table")
    @PreAuthorize("hasAuthority('service.view') and hasAuthority('model.view') and hasAuthority('task.view')")
    public LineageGraphResponse tableLineage(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "2") int depth
    ) {
        return lineageQueryService.tableLineage(id, depth);
    }

    @GetMapping("/{id}/lineage/fields/{fieldId}")
    @PreAuthorize("hasAuthority('service.view') and hasAuthority('model.view') and hasAuthority('task.view')")
    public LineageGraphResponse fieldLineage(
            @PathVariable UUID id,
            @PathVariable UUID fieldId,
            @RequestParam(defaultValue = "2") int depth
    ) {
        return lineageQueryService.fieldLineage(id, fieldId, depth);
    }

    @PostMapping("/actions/test-sql")
    @PreAuthorize("hasAnyAuthority('service.create', 'service.update')")
    public SqlServiceTestResponse testSql(@Valid @RequestBody SqlServiceTestRequest request) {
        return service.testSql(request);
    }

    @PostMapping("/{id}/actions/enable")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse enable(@PathVariable UUID id) {
        return service.enable(id);
    }

    @PostMapping("/{id}/actions/publish")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse publish(@PathVariable UUID id) {
        return service.publish(id);
    }

    @PostMapping("/{id}/actions/reconcile-gateway")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse reconcileGateway(@PathVariable UUID id) {
        return service.reconcileGateway(id);
    }

    @PostMapping("/{id}/actions/unpublish")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse unpublish(@PathVariable UUID id) {
        return service.unpublish(id);
    }

    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse disable(@PathVariable UUID id) {
        return service.disable(id);
    }

    @PostMapping("/{id}/actions/cleanup-deployment")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse cleanupDeployment(@PathVariable UUID id) {
        return service.cleanupDeployment(id);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('service.delete')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}

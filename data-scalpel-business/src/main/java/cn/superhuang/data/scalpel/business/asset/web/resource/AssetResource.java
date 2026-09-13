package cn.superhuang.data.scalpel.business.asset.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.asset.domain.AssetType;
import cn.superhuang.data.scalpel.business.asset.service.AssetManagementService;
import cn.superhuang.data.scalpel.business.asset.service.AssetPortalService;
import cn.superhuang.data.scalpel.business.asset.web.request.RegisterAssetsRequest;
import cn.superhuang.data.scalpel.business.asset.web.request.UpdateAssetRequest;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetBatchOperationResponse;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetCandidateResponse;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetResponse;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetSourceNavigationResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/v1/assets")
@Tag(name = "资产管理")
public class AssetResource {

    private final AssetManagementService service;
    private final AssetPortalService portalService;

    public AssetResource(AssetManagementService service, AssetPortalService portalService) {
        this.service = service;
        this.portalService = portalService;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询资产",
            keywords = {"资产", "数据资产", "治理", "发布", "同步状态"},
            relatedOperations = {"GET /api/v1/assets/{id}", "GET /api/v1/assets/candidates"})
    @GetMapping
    @PreAuthorize("hasAuthority('asset.view')")
    @Operation(summary = "查询资产", description = "使用通用 Search DSL 分页查询已登记的全部草稿、已发布和已下线资产，返回治理字段、最近成功来源快照和同步状态。")
    public PageResponse<AssetResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询资产详情",
            keywords = {"资产", "详情", "来源快照", "同步状态"},
            relatedOperations = {"POST /api/v1/assets/{id}/actions/check", "POST /api/v1/assets/{id}/actions/sync"})
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('asset.view')")
    @Operation(summary = "查询资产详情", description = "返回资产门户治理信息、最近一次成功保存的来源安全快照及检查/同步状态；读取本身不会访问或修改来源资源。")
    public AssetResponse get(@Parameter(description = "资产 UUID。") @PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "获取资产来源导航",
            keywords = {"资产", "使用资产", "来源页面", "导航"},
            prerequisites = "调用者已登录，并拥有该资产来源类型对应的查看权限。")
    @GetMapping("/{id}/source-navigation")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取资产来源导航", description = "按资产来源类型检查当前用户的模型、文件数据集、码表、全景或数据服务查看权限，通过后返回现有前端详情路径；不返回来源数据或外部地址。")
    public AssetSourceNavigationResponse sourceNavigation(@Parameter(description = "已发布资产 UUID。") @PathVariable UUID id, Authentication authentication) {
        return portalService.sourceNavigation(id, authentication);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询可登记的来源资源",
            keywords = {"资产", "候选资源", "登记", "模型", "文件数据集", "码表", "全景", "数据服务"},
            relatedOperations = {"POST /api/v1/assets/actions/register"})
    @GetMapping("/candidates")
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "查询可登记的来源资源", description = "按一种资产类型查询来源资源及其当前可登记原因，并标出已登记资源对应的资产 UUID。候选状态仅用于展示，登记时会重新读取来源并校验。")
    public PageResponse<AssetCandidateResponse> candidates(
            @Parameter(description = "必选来源类型：DATA_MODEL、FILE_DATASET、DICTIONARY、DATA_SERVICE 或 PANORAMA。") @RequestParam AssetType assetType,
            @Parameter(description = "可选关键词，模糊匹配来源名称或编码。") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码，从 0 开始；省略时使用默认首页。") @RequestParam(required = false) Integer page,
            @Parameter(description = "每页数量；省略时使用默认值，并受服务端上限约束。") @RequestParam(required = false) Integer size
    ) {
        return service.candidates(assetType, keyword, page, size);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "批量登记资产草稿",
            keywords = {"资产", "批量登记", "资产草稿"},
            prerequisites = "全部 resourceIds 属于同一 assetType、当前满足该类型的登记条件，且尚未登记。",
            relatedOperations = {"GET /api/v1/assets/candidates", "POST /api/v1/assets/{id}/actions/update"})
    @PostMapping("/actions/register")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "批量登记资产草稿", description = "重新读取并校验全部来源后，在一个事务中创建 DRAFT 资产并保存首份安全快照，返回 201。任一来源不可用、重复或已登记时整批失败。")
    public List<AssetResponse> register(@Valid @RequestBody RegisterAssetsRequest request) {
        return service.register(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改资产门户信息",
            keywords = {"资产", "门户名称", "简介", "标签", "负责人", "敏感级别", "推荐"},
            relatedOperations = {"POST /api/v1/assets/{id}/actions/publish"})
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "修改资产门户信息", description = "修改业务领域、门户覆盖名称和简介、标签、负责人、更新频率、敏感级别与推荐状态；不会回写来源资源或覆盖来源快照。")
    public AssetResponse update(@Parameter(description = "资产 UUID。") @PathVariable UUID id, @Valid @RequestBody UpdateAssetRequest request) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "发布资产",
            keywords = {"资产", "发布", "门户"},
            prerequisites = "资产为 DRAFT 或 OFFLINE；来源当前可用；业务领域、有效名称、简介、负责人、更新频率和敏感级别完整。")
    @PostMapping("/{id}/actions/publish")
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "发布资产", description = "重新读取并同步来源安全快照，通过完整性检查后将 DRAFT 或 OFFLINE 资产发布到匿名门户；不会修改来源资源。")
    public AssetResponse publish(@Parameter(description = "资产 UUID。") @PathVariable UUID id) {
        return service.publish(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "下线资产",
            keywords = {"资产", "下线", "门户"}, prerequisites = "资产当前为 PUBLISHED。")
    @PostMapping("/{id}/actions/offline")
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "下线资产", description = "将已发布资产变为 OFFLINE，使其立即退出匿名门户；保留治理信息和来源快照，也不修改来源资源。")
    public AssetResponse offline(@Parameter(description = "资产 UUID。") @PathVariable UUID id) {
        return service.offline(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除资产记录",
            keywords = {"资产", "删除登记"}, prerequisites = "资产为 DRAFT 或 OFFLINE；PUBLISHED 必须先下线。")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "删除资产记录", description = "删除草稿或已下线的资产治理记录并返回 204；不会删除、停用或修改来源资源。")
    public void delete(@Parameter(description = "资产 UUID。") @PathVariable UUID id) {
        service.delete(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "检查资产来源变化",
            keywords = {"资产", "来源变化", "指纹", "检查"},
            relatedOperations = {"POST /api/v1/assets/{id}/actions/sync"})
    @PostMapping("/{id}/actions/check")
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "检查资产来源变化", description = "重新生成当前来源安全快照并比较指纹，只更新检查时间、同步状态和安全错误摘要；不会覆盖最近成功快照。来源读取失败也以返回状态表达。")
    public AssetResponse check(@Parameter(description = "资产 UUID。") @PathVariable UUID id) {
        return service.check(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "同步资产来源快照",
            keywords = {"资产", "同步", "来源快照"},
            prerequisites = "来源资源存在且当前满足该类型的可用条件。",
            relatedOperations = {"POST /api/v1/assets/{id}/actions/check"})
    @PostMapping("/{id}/actions/sync")
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "同步资产来源快照", description = "来源可用时覆盖通用来源摘要、安全快照和指纹；不覆盖门户治理字段或发布状态。来源不可用、缺失或读取失败时保留最近成功快照，并在响应中返回同步状态。")
    public AssetResponse sync(@Parameter(description = "资产 UUID。") @PathVariable UUID id) {
        return service.sync(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "全量检查已登记资产",
            keywords = {"资产", "全量检查", "来源变化"})
    @PostMapping("/actions/check-all")
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "全量检查已登记资产", description = "逐条独立检查所有已登记资产并容错汇总；不会自动登记、发布或同步来源快照。单条失败不会回滚其他资产的检查结果。")
    public AssetBatchOperationResponse checkAll() {
        return service.checkAll();
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "全量同步已登记资产",
            keywords = {"资产", "全量同步", "来源快照"})
    @PostMapping("/actions/sync-all")
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "全量同步已登记资产", description = "逐条独立同步所有已登记资产并容错汇总；来源可用的资产更新安全快照，其余资产保留最近成功快照。不会自动登记或发布资产。")
    public AssetBatchOperationResponse syncAll() {
        return service.syncAll();
    }
}

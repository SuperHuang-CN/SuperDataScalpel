package cn.superhuang.data.scalpel.business.task.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.task.service.DataMaskingRuleService;
import cn.superhuang.data.scalpel.business.task.web.request.CreateDataMaskingRuleRequest;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateDataMaskingRuleRequest;
import cn.superhuang.data.scalpel.business.task.web.response.DataMaskingRuleResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/masking-rules")
@Tag(name = "脱敏规则管理")
public class DataMaskingRuleResource {

    private final DataMaskingRuleService service;

    public DataMaskingRuleResource(DataMaskingRuleService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询脱敏规则")
    @GetMapping
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询脱敏规则", description = "分页查询可供 Canvas MASK_FIELDS 节点复制使用的全局脱敏规则，返回完整规范化 definition；不处理实际数据。任务保存后执行的是任务内定义快照，不会在运行时回查本目录。")
    public PageResponse<DataMaskingRuleResponse> search(
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询脱敏规则详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询脱敏规则详情", description = "读取脱敏规则的算法类型、参数和当前配置，不执行脱敏。")
    public DataMaskingRuleResponse get(@Parameter(description = "脱敏规则 UUID。") @PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "创建脱敏规则")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "创建脱敏规则", description = "创建可复用的脱敏规则定义；不会自动应用到已有任务或数据。")
    public DataMaskingRuleResponse create(
            @Valid @RequestBody CreateDataMaskingRuleRequest request
    ) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改脱敏规则")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "修改脱敏规则", description = "修改全局规则名称、说明和完整脱敏定义，稳定编码保持不变。已经保存到 Canvas 的 GLOBAL 规则包含独立 definition 与来源身份快照，不会自动同步本次修改；需要编辑并重新保存对应任务定义才会生效。")
    public DataMaskingRuleResponse update(
            @Parameter(description = "脱敏规则 UUID。") @PathVariable UUID id,
            @Valid @RequestBody UpdateDataMaskingRuleRequest request
    ) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除脱敏规则")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "删除脱敏规则", description = "永久删除全局脱敏规则目录记录。当前不会扫描或阻止已保存 Canvas 中的来源快照；这些任务仍按内嵌 definition 编译和运行，历史与已保存任务定义均不改变。")
    public void delete(@Parameter(description = "脱敏规则 UUID。") @PathVariable UUID id) {
        service.delete(id);
    }
}

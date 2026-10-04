package cn.superhuang.data.scalpel.business.quality.web.resource;
import cn.superhuang.data.scalpel.business.quality.service.ModelQualityStatisticsService;
import cn.superhuang.data.scalpel.business.quality.web.response.*;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/model-quality")
@PreAuthorize("hasAuthority('model.view')")
@Tag(name="模型质量统计")
public class ModelQualityStatisticsResource {
    private final ModelQualityStatisticsService service;
    public ModelQualityStatisticsResource(ModelQualityStatisticsService service) { this.service=service; }
    @GetMapping("/statistics")
    @Operation(summary="查询已发布模型质量统计",description="只读管理库，按每模型最近正式有效结果去重；不读取制品、不触发检查，历史结果不代表当前合格。无模型查看权限返回403。")
    public ModelQualityStatisticsResponse statistics() { return service.statistics(); }
    @GetMapping("/models")
    @Operation(summary="查询质量统计对应模型",description="只读分页，范围与统计一致；不改变Search DSL。分类或分页无效返回400，无查看权限返回403。")
    public PageResponse<ModelQualityStatisticsItem> items(
      @Parameter(description="分类：ALL全部、PASSED最近通过、FAILED最近不通过、NONE无正式有效结果、EXECUTION_FAILED最近正式执行失败。默认ALL。")
      @RequestParam(defaultValue="ALL") String result,
      @Parameter(description="从0开始的页码，默认0。") @RequestParam(defaultValue="0") int page,
      @Parameter(description="每页条数1～100，默认20。") @RequestParam(defaultValue="20") int size) {
        return service.items(result,page,size);
    }
}

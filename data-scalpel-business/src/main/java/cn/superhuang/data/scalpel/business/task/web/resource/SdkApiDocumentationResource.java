package cn.superhuang.data.scalpel.business.task.web.resource;

import cn.superhuang.data.scalpel.business.task.service.TaskCompilationService;
import cn.superhuang.data.scalpel.contract.task.SdkApiDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spark-jar-sdk-api")
@Tag(name = "Spark JAR SDK 说明")
public class SdkApiDocumentationResource {
    private final TaskCompilationService compilation;
    public SdkApiDocumentationResource(TaskCompilationService compilation) { this.compilation = compilation; }

    @GetMapping
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "读取当前 SDK API 说明", description = "只读查询配置的 TaskEngine 实际配套 SDK 文档；从 SDK 源码注释随构建生成，含批流适用范围、公开方法、参数及示例。不保存或执行任务，不启动 Java 语言服务。需 task.view 权限（无权限 403）；引擎不可用或文档缺失返回 502，超时返回 504。不缓存旧版本兜底，fingerprint 用于识别同一 SNAPSHOT 下的变化。")
    public ResponseEntity<SdkApiDocumentation> get() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(compilation.sdkApi());
    }
}

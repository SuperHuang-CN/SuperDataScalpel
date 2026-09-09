package cn.superhuang.data.scalpel.business.datasource.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.datasource.service.DataSourceRuntimeService;
import cn.superhuang.data.scalpel.business.datasource.web.response.DataSourceTypeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/data-source-types")
@Tag(name = "数据源类型")
public class DataSourceTypeResource {

    private final DataSourceRuntimeService service;

    public DataSourceTypeResource(DataSourceRuntimeService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询支持的数据库类型和连接能力")
    @GetMapping
    @PreAuthorize("hasAuthority('datasource.view')")
    @Operation(summary = "查询支持的数据库类型和连接能力")
    public List<DataSourceTypeResponse> list() {
        return service.dataSourceTypes();
    }
}

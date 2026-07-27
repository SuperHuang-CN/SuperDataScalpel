package cn.superhuang.data.scalpel.engine.route;

import cn.superhuang.data.scalpel.contract.service.ServiceQueryResponse;
import cn.superhuang.data.scalpel.contract.service.SqlServiceQueryRequest;
import cn.superhuang.data.scalpel.engine.query.SqlServiceQueryExecutor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/** Handler dynamically bound to every published SQL query service route. */
@Component
public class SqlServiceQueryHandler {

    private final DynamicServiceRouteRegistry routeRegistry;
    private final SqlServiceQueryExecutor executor;

    public SqlServiceQueryHandler(DynamicServiceRouteRegistry routeRegistry, SqlServiceQueryExecutor executor) {
        this.routeRegistry = routeRegistry;
        this.executor = executor;
    }

    @ResponseBody
    public ServiceQueryResponse execute(
            HttpServletRequest request,
            @Valid @RequestBody SqlServiceQueryRequest query
    ) {
        var deployment = routeRegistry.deployment(request.getRequestURI())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "服务未部署"));
        return executor.execute(deployment, query);
    }
}

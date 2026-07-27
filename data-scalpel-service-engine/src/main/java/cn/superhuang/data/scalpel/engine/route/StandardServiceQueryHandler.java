package cn.superhuang.data.scalpel.engine.route;

import cn.superhuang.data.scalpel.contract.service.StandardServiceQueryRequest;
import cn.superhuang.data.scalpel.contract.service.ServiceQueryResponse;
import cn.superhuang.data.scalpel.engine.query.StandardServiceQueryExecutor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/** The one handler method dynamically bound to every published standard-service route. */
@Component
public class StandardServiceQueryHandler {

    private final DynamicServiceRouteRegistry routeRegistry;
    private final StandardServiceQueryExecutor executor;

    public StandardServiceQueryHandler(DynamicServiceRouteRegistry routeRegistry, StandardServiceQueryExecutor executor) {
        this.routeRegistry = routeRegistry;
        this.executor = executor;
    }

    @ResponseBody
    public ServiceQueryResponse execute(
            HttpServletRequest request,
            @Valid @RequestBody StandardServiceQueryRequest query
    ) {
        var deployment = routeRegistry.deployment(request.getRequestURI())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "服务未部署"));
        return executor.execute(deployment, query);
    }
}

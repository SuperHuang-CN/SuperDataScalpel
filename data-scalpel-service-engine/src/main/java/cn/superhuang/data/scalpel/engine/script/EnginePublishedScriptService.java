package cn.superhuang.data.scalpel.engine.script;

import cn.superhuang.data.scalpel.contract.service.ScriptCompletionResponse;
import cn.superhuang.data.scalpel.contract.service.ScriptDraftExecutionResponse;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentRequest;
import cn.superhuang.data.scalpel.engine.datasource.EngineApiStudioDataSourceService;
import cn.superhuang.data.scalpel.engine.deployment.StoredServiceDeployment;
import cn.superhuang.superops.api.studio.completion.application.CompletionService;
import cn.superhuang.superops.api.studio.completion.model.MethodVo;
import cn.superhuang.superops.api.studio.entity.vo.RunApiRes;
import cn.superhuang.superops.api.studio.published.PublishedScriptApiDefinition;
import cn.superhuang.superops.api.studio.published.PublishedScriptApiService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Thin adapter from Engine deployment contracts to API Studio's stable embedding facade. */
@Service
public class EnginePublishedScriptService {

    private final PublishedScriptApiService publishedScriptApiService;
    private final CompletionService completionService;
    private final EngineApiStudioDataSourceService dataSourceService;
    private final ObjectMapper objectMapper;

    public EnginePublishedScriptService(
            PublishedScriptApiService publishedScriptApiService,
            CompletionService completionService,
            EngineApiStudioDataSourceService dataSourceService,
            ObjectMapper objectMapper
    ) {
        this.publishedScriptApiService = publishedScriptApiService;
        this.completionService = completionService;
        this.dataSourceService = dataSourceService;
        this.objectMapper = objectMapper;
    }

    public void upsert(StoredServiceDeployment deployment) {
        ServiceDeploymentRequest request = deployment.request();
        dataSourceService.resolve(request.dataSourceId());
        publishedScriptApiService.upsert(new PublishedScriptApiDefinition(
                request.serviceId().toString(),
                request.serviceCode(),
                request.routePath(),
                request.dataSourceId().toString(),
                request.definition().scriptDefinition().script()
        ));
    }

    public void delete(UUID serviceId) {
        publishedScriptApiService.delete(serviceId.toString());
    }

    public ScriptDraftExecutionResponse executeDraft(
            cn.superhuang.data.scalpel.contract.service.ScriptDraftExecutionRequest request
    ) {
        dataSourceService.resolve(request.dataSourceId());
        RunApiRes report = publishedScriptApiService.executeDraft(
                new cn.superhuang.superops.api.studio.published.ScriptDraftExecutionRequest(
                        null,
                        request.routePath(),
                        request.dataSourceId().toString(),
                        request.script(),
                        request.body(),
                        request.query(),
                        request.headers(),
                        request.pathVariables(),
                        request.cookies(),
                        request.session()
                )
        );
        return objectMapper.convertValue(report, ScriptDraftExecutionResponse.class);
    }

    public ScriptCompletionResponse completion(UUID dataSourceId) {
        var runtimeDataSource = dataSourceService.resolve(dataSourceId);
        try {
            var completion = completionService.provideCompletionTypes();
            Map<String, List<ScriptCompletionResponse.ScriptCompletionMethod>> classes =
                    completion.getClazzs().entrySet().stream().collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().stream().map(this::method).toList(),
                            (left, right) -> right,
                            LinkedHashMap::new
                    ));
            var tableInfos = runtimeDataSource.jdbcDataSource().buildTableInfo();
            List<Map<String, Object>> tables = (tableInfos == null ? List.of() : tableInfos)
                    .stream()
                    .map(this::table)
                    .toList();
            return new ScriptCompletionResponse(
                    classes,
                    new LinkedHashMap<>(completion.getVariables()),
                    new LinkedHashMap<>(completion.getSyntax()),
                    Map.of(dataSourceId.toString(), tables),
                    dataSourceId.toString()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load API Studio script completion data", exception);
        }
    }

    private ScriptCompletionResponse.ScriptCompletionMethod method(MethodVo method) {
        return new ScriptCompletionResponse.ScriptCompletionMethod(
                method.getType(),
                method.getVarName(),
                method.getResultType(),
                method.getParams(),
                method.getDocs()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> table(Object table) {
        return objectMapper.convertValue(table, Map.class);
    }
}

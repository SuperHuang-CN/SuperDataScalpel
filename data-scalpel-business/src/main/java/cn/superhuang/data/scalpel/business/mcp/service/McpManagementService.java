package cn.superhuang.data.scalpel.business.mcp.service;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.mcp.config.McpPlatformProperties;
import cn.superhuang.data.scalpel.business.mcp.domain.McpServer;
import cn.superhuang.data.scalpel.business.mcp.domain.McpServerRelease;
import cn.superhuang.data.scalpel.business.mcp.domain.McpTool;
import cn.superhuang.data.scalpel.business.mcp.domain.McpToolRelease;
import cn.superhuang.data.scalpel.business.mcp.repository.McpAccessTokenServerGrantRepository;
import cn.superhuang.data.scalpel.business.mcp.repository.McpServerReleaseRepository;
import cn.superhuang.data.scalpel.business.mcp.repository.McpServerRepository;
import cn.superhuang.data.scalpel.business.mcp.repository.McpToolReleaseRepository;
import cn.superhuang.data.scalpel.business.mcp.repository.McpToolRepository;
import cn.superhuang.data.scalpel.business.mcp.web.request.CreateMcpServerRequest;
import cn.superhuang.data.scalpel.business.mcp.web.request.ExecuteMcpToolDraftRequest;
import cn.superhuang.data.scalpel.business.mcp.web.request.SaveMcpToolRequest;
import cn.superhuang.data.scalpel.business.mcp.web.request.UpdateMcpServerRequest;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpDraftExecutionResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpReleaseResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpReleaseToolResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpServerResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpTokenResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpToolResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class McpManagementService {
    private final McpServerRepository serverRepository;
    private final McpToolRepository toolRepository;
    private final McpServerReleaseRepository releaseRepository;
    private final McpToolReleaseRepository toolReleaseRepository;
    private final McpAccessTokenServerGrantRepository accessTokenGrantRepository;
    private final DirectoryService directoryService;
    private final SearchEngine searchEngine;
    private final McpSchemaService schemaService;
    private final McpScriptExecutionService scriptService;
    private final McpPlatformProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public McpManagementService(McpServerRepository serverRepository, McpToolRepository toolRepository,
            McpServerReleaseRepository releaseRepository, McpToolReleaseRepository toolReleaseRepository,
            McpAccessTokenServerGrantRepository accessTokenGrantRepository, DirectoryService directoryService, SearchEngine searchEngine,
            McpSchemaService schemaService, McpScriptExecutionService scriptService,
            McpPlatformProperties properties, ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.serverRepository=serverRepository; this.toolRepository=toolRepository; this.releaseRepository=releaseRepository;
        this.toolReleaseRepository=toolReleaseRepository; this.accessTokenGrantRepository=accessTokenGrantRepository; this.directoryService=directoryService;
        this.searchEngine=searchEngine; this.schemaService=schemaService; this.scriptService=scriptService;
        this.properties=properties; this.objectMapper=objectMapper;
        this.transactionTemplate=new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public PageResponse<McpServerResponse> search(SearchRequest request) {
        var page=searchEngine.search(request, McpServer.class, serverRepository);
        Map<UUID, Long> counts = new java.util.HashMap<>();
        if (!page.isEmpty()) {
            toolRepository.countForServers(page.getContent().stream().map(McpServer::getId).toList())
                    .forEach(count -> counts.put(count.getServerId(), count.getToolCount()));
        }
        List<McpServerResponse> content=page.getContent().stream()
                .map(server -> response(server, counts.getOrDefault(server.getId(), 0L))).toList();
        return new PageResponse<>(content,page.getTotalElements(),page.getTotalPages(),page.getNumber(),page.getSize());
    }
    @Transactional(readOnly = true) public McpServerResponse get(UUID id){return response(requireServer(id));}

    @Transactional
    public McpServerResponse create(CreateMcpServerRequest request) {
        directoryService.validateAssignment(DirectoryScope.MCP_SERVER, request.directoryId());
        if(serverRepository.existsByCode(request.code())) throw conflict("MCP Server 编码已存在");
        try {
            McpServer server=serverRepository.saveAndFlush(McpServer.create(request.code(),request.name(),request.directoryId(),request.description(),request.instructions()));
            return response(server);
        } catch(DataIntegrityViolationException exception){throw conflict("MCP Server 编码已存在");}
    }
    @Transactional
    public McpServerResponse update(UUID id, UpdateMcpServerRequest request){
        directoryService.validateAssignment(DirectoryScope.MCP_SERVER,request.directoryId());
        McpServer server=requireServerForUpdate(id); server.update(request.name(),request.directoryId(),request.description(),request.instructions());
        return response(serverRepository.saveAndFlush(server));
    }
    @Transactional(readOnly = true) public List<McpToolResponse> tools(UUID serverId){requireServer(serverId);return toolRepository.findAllByServerIdOrderBySortOrderAscCodeAsc(serverId).stream().map(McpToolResponse::from).toList();}
    @Transactional(readOnly = true)
    public List<cn.superhuang.data.scalpel.business.mcp.web.response.McpToolSummaryResponse> toolSummaries(UUID serverId){
        requireServer(serverId);
        return toolRepository.findSummariesByServerIdOrderBySortOrderAscCodeAsc(serverId);
    }
    @Transactional(readOnly = true) public McpToolResponse tool(UUID serverId,UUID toolId){return McpToolResponse.from(requireTool(serverId,toolId));}
    public McpToolResponse createTool(UUID serverId, SaveMcpToolRequest request){
        requireServer(serverId);
        validateTool(request);
        return transactionTemplate.execute(status -> createToolLocked(serverId, request));
    }
    private McpToolResponse createToolLocked(UUID serverId, SaveMcpToolRequest request){
        McpServer server=requireServerForUpdate(serverId);
        if(toolRepository.countByServerId(serverId)>=properties.maxToolsPerServer()) throw conflict("每个 MCP Server 最多配置 "+properties.maxToolsPerServer()+" 个 Tool");
        if(toolRepository.existsByServerIdAndCode(serverId,request.code())) throw conflict("Tool 编码已存在");
        McpTool tool=McpTool.create(serverId,request.code(),request.name(),request.description(),request.inputSchemaJson(),request.outputSchemaJson(),request.script(),request.examplesJson(),request.enabled(),request.sortOrder());
        try { tool=toolRepository.saveAndFlush(tool); } catch(DataIntegrityViolationException exception){throw conflict("Tool 编码已存在");}
        server.touchDraft(); serverRepository.save(server); return McpToolResponse.from(tool);
    }
    public McpToolResponse updateTool(UUID serverId,UUID toolId,SaveMcpToolRequest request){
        long expectedRevision = requireTool(serverId, toolId).getRevision();
        if (request.expectedRevision() != null && request.expectedRevision() != expectedRevision) {
            throw conflict("Tool 已被其他操作修改，请保留本地内容后重新加载");
        }
        validateTool(request);
        return transactionTemplate.execute(status -> updateToolLocked(serverId, toolId, request, expectedRevision));
    }
    private McpToolResponse updateToolLocked(UUID serverId, UUID toolId, SaveMcpToolRequest request, long expectedRevision){
        McpServer server=requireServerForUpdate(serverId); McpTool tool=requireTool(serverId,toolId);
        if (tool.getRevision() != expectedRevision) throw conflict("Tool 在保存校验期间发生变化，请重新加载");
        if(toolRepository.existsByServerIdAndCodeAndIdNot(serverId,request.code(),toolId)) throw conflict("Tool 编码已存在");
        boolean changed=tool.update(request.code(),request.name(),request.description(),request.inputSchemaJson(),request.outputSchemaJson(),request.script(),request.examplesJson(),request.enabled(),request.sortOrder());
        tool=toolRepository.saveAndFlush(tool); if(changed){server.touchDraft();serverRepository.save(server);} return McpToolResponse.from(tool);
    }
    @Transactional public void deleteTool(UUID serverId,UUID toolId){McpServer server=requireServerForUpdate(serverId);toolRepository.delete(requireTool(serverId,toolId));server.touchDraft();serverRepository.save(server);}

    public McpDraftExecutionResponse executeDraft(UUID serverId, ExecuteMcpToolDraftRequest request){
        McpServer server = requireServer(serverId);
        long started = System.nanoTime();
        try {
            JsonNode args=schemaService.parseArguments(request.argumentsJson());
            var result=scriptService.execute(request.inputSchemaJson(),request.outputSchemaJson(),request.script(),args,
                    Map.of("draft",true,"serverCode",server.getCode(),"releaseVersion",0,"toolCode","draft","executedAt",Instant.now().toString()));
            return new McpDraftExecutionResponse(true,result.structuredContent(),result.text(),result.durationMillis(),null,result.logs());
        } catch(ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 429) throw exception;
            return new McpDraftExecutionResponse(false,null,null,
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started),safe(exception),List.of());
        } catch(RuntimeException exception){
            Throwable detail = exception.getCause() == null ? exception : exception.getCause();
            return new McpDraftExecutionResponse(false,null,null,
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started),safe(detail),List.of());
        }
    }

    public McpServerResponse publish(UUID serverId){
        McpServer snapshotServer=requireServer(serverId);
        List<McpTool> tools=toolRepository.findAllByServerIdAndEnabledTrueOrderBySortOrderAscCodeAsc(serverId);
        if(tools.isEmpty()) throw conflict("至少需要启用一个 Tool 才能发布");
        tools.forEach(tool->scriptService.validateDefinition(tool.getInputSchemaJson(),tool.getOutputSchemaJson(),tool.getScript()));
        String digest=digest(snapshotServer,tools);
        long expectedDraftRevision=snapshotServer.getDraftRevision();
        return transactionTemplate.execute(status->publishLocked(serverId,expectedDraftRevision,digest,tools));
    }
    private McpServerResponse publishLocked(UUID serverId,long expectedDraftRevision,String digest,List<McpTool> tools){
        McpServer server=requireServerForUpdate(serverId);
        if(server.getDraftRevision()!=expectedDraftRevision) throw conflict("MCP 定义在发布校验期间发生变化，请重新发布");
        McpServerRelease latest=releaseRepository.findFirstByServerIdOrderByVersionDesc(serverId).orElse(null);
        Instant now=Instant.now();
        boolean unchanged = latest != null && (latest.getDigest().equals(digest)
                || digest(latest, toolReleaseRepository.findAllByReleaseIdOrderBySortOrderAscCodeAsc(latest.getId())).equals(digest));
        if(unchanged) {
            server.publish(latest.getId(),latest.getVersion(),now);
        } else {
            int version=latest==null?1:latest.getVersion()+1;
            McpServerRelease release=releaseRepository.saveAndFlush(McpServerRelease.create(serverId,version,server.getCode(),server.getName(),server.getInstructions(),digest,tools.size(),server.getDraftRevision(),now));
            toolReleaseRepository.saveAllAndFlush(tools.stream().map(tool->McpToolRelease.from(release.getId(),tool)).toList());
            server.publish(release.getId(),version,now);
        }
        return response(serverRepository.saveAndFlush(server));
    }
    @Transactional public McpServerResponse disable(UUID id){McpServer server=requireServerForUpdate(id);server.disable();return response(serverRepository.saveAndFlush(server));}
    @Transactional public McpServerResponse enable(UUID id){McpServer server=requireServerForUpdate(id);try{server.enable();}catch(IllegalStateException e){throw conflict(e.getMessage());}return response(serverRepository.saveAndFlush(server));}
    @Transactional public void delete(UUID id){McpServer server=requireServerForUpdate(id);if(server.getPublishedVersion()!=null)throw conflict("已发布过的 MCP Server 不能删除，可停用保留审计记录");toolRepository.deleteAllByServerId(id);accessTokenGrantRepository.deleteAllByServerId(id);serverRepository.delete(server);}
    public McpTokenResponse token(UUID serverId){requireServer(serverId);throw new ResponseStatusException(HttpStatus.GONE,"Server 级 Token 已停用，请使用 MCP 访问凭证管理");}
    public McpTokenResponse rotateToken(UUID serverId){requireServer(serverId);throw new ResponseStatusException(HttpStatus.GONE,"Server 级 Token 已停用，请使用 MCP 访问凭证管理");}
    @Transactional(readOnly=true) public List<McpReleaseResponse> releases(UUID serverId){requireServer(serverId);return releaseRepository.findAllByServerIdOrderByVersionDesc(serverId).stream().map(release->releaseResponse(release,false)).toList();}
    @Transactional(readOnly=true) public McpReleaseResponse release(UUID serverId,int version){requireServer(serverId);return releaseResponse(releaseRepository.findByServerIdAndVersion(serverId,version).orElseThrow(()->notFound("发布版本不存在")),true);}

    private McpReleaseResponse releaseResponse(McpServerRelease release,boolean withTools){List<McpReleaseToolResponse> tools=withTools?toolReleaseRepository.findAllByReleaseIdOrderBySortOrderAscCodeAsc(release.getId()).stream().map(McpReleaseToolResponse::from).toList():List.of();return McpReleaseResponse.from(release,tools);}
    private void validateTool(SaveMcpToolRequest r){scriptService.validateDefinition(r.inputSchemaJson(),r.outputSchemaJson(),r.script());if(r.examplesJson()!=null&&!r.examplesJson().isBlank()){try{JsonNode n=objectMapper.readTree(r.examplesJson());if(!n.isArray()||n.size()>10)throw new IllegalArgumentException();}catch(RuntimeException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"测试样例必须是最多 10 项的 JSON 数组");}}}
    private String digest(McpServer server,List<McpTool> tools){
        List<Map<String,Object>> snapshots=tools.stream().sorted(Comparator.comparingInt(McpTool::getSortOrder).thenComparing(McpTool::getCode))
                .map(t -> toolSnapshot(t.getCode(), t.getName(), t.getDescription(), t.getInputSchemaJson(),
                        t.getOutputSchemaJson(), t.getScript(), t.getSortOrder())).toList();
        return definitionDigest(server.getCode(), server.getName(), server.getInstructions(), snapshots);
    }
    private String digest(McpServerRelease server, List<McpToolRelease> tools) {
        List<Map<String,Object>> snapshots=tools.stream().sorted(Comparator.comparingInt(McpToolRelease::getSortOrder).thenComparing(McpToolRelease::getCode))
                .map(t -> toolSnapshot(t.getCode(), t.getName(), t.getDescription(), t.getInputSchemaJson(),
                        t.getOutputSchemaJson(), t.getScript(), t.getSortOrder())).toList();
        return definitionDigest(server.getServerCode(), server.getServerName(), server.getInstructions(), snapshots);
    }
    private Map<String, Object> toolSnapshot(String code, String name, String description, String input,
                                             String output, String script, int sortOrder) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", code); snapshot.put("name", name); snapshot.put("description", description);
        snapshot.put("inputSchema", canonicalSchema(input));
        snapshot.put("outputSchema", canonicalSchema(output));
        snapshot.put("script", script); snapshot.put("sortOrder", sortOrder);
        return snapshot;
    }
    private Object canonicalSchema(String source) {
        return source == null || source.isBlank() ? null : canonicalNode(objectMapper.readTree(source));
    }
    private Object canonicalNode(JsonNode node) {
        if (node.isObject()) {
            Map<String, Object> sorted = new java.util.TreeMap<>();
            for (String name : node.propertyNames()) sorted.put(name, canonicalNode(node.get(name)));
            return sorted;
        }
        if (node.isArray()) {
            List<Object> values = new java.util.ArrayList<>();
            for (JsonNode item : node) values.add(canonicalNode(item));
            return values;
        }
        return objectMapper.convertValue(node, Object.class);
    }
    private String definitionDigest(String code, String name, String instructions, List<Map<String, Object>> snapshots) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("code", code);
        definition.put("name", name);
        definition.put("instructions", instructions == null ? "" : instructions);
        definition.put("tools", snapshots);
        return sha256(objectMapper.writeValueAsString(definition));
    }
    private McpServerResponse response(McpServer server){return response(server,toolRepository.countByServerId(server.getId()));}
    private McpServerResponse response(McpServer server,long count){boolean changed=server.getPublishedDraftRevision()==null||server.getPublishedDraftRevision()!=server.getDraftRevision();return McpServerResponse.from(server,count,changed,properties.endpoint(server.getCode()));}
    private McpServer requireServer(UUID id){return serverRepository.findById(id).orElseThrow(()->notFound("MCP Server 不存在"));}
    private McpServer requireServerForUpdate(UUID id){return serverRepository.findLockedById(id).orElseThrow(()->notFound("MCP Server 不存在"));}
    private McpTool requireTool(UUID serverId,UUID id){return toolRepository.findByIdAndServerId(id,serverId).orElseThrow(()->notFound("Tool 不存在"));}
    private static String sha256(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String safe(Throwable e){String m=e instanceof ResponseStatusException r?r.getReason():e.getMessage();return m==null?e.getClass().getSimpleName():m.substring(0,Math.min(800,m.length()));}
    private static ResponseStatusException notFound(String m){return new ResponseStatusException(HttpStatus.NOT_FOUND,m);} private static ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);}
}

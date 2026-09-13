package cn.superhuang.data.scalpel.business.dsh.service;

import cn.superhuang.data.scalpel.business.dsh.config.DshProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
@Service
public class DshService {
    private final DshBridgeClient bridge;
    private final DshBindingService bindings;
    private final DshProperties properties;
    private final ObjectMapper mapper;
    public DshService(DshBridgeClient b,DshBindingService s,DshProperties p,ObjectMapper m) { bridge=b;bindings=s;properties=p;mapper=m; }
    public JsonNode capabilities() {
        if(!properties.getEnabled()) return mapper.valueToTree(Map.of("enabled",false,"ready",false));
        try {
            if(java.util.Base64.getDecoder().decode(properties.getCredentialKey()).length!=32) throw new IllegalArgumentException();
        } catch(IllegalArgumentException e) {
            return mapper.valueToTree(Map.of("enabled",true,"ready",false,"code","DSH_CREDENTIAL_UNAVAILABLE"));
        }
        return bridge.get("/bridge/v3/capabilities");
    }
    public JsonNode ensure(UUID owner) {
        bridge.requireEnabled();
        var credential=bindings.prepare(owner);bridge.provision(credential);
        var workspace=bridge.post(bridge.userPath(owner,"/workspaces/actions/ensure"),Map.of());
        bindings.ready(owner,workspace.path("workspaceId").asText());return workspace;
    }
    public JsonNode list(UUID owner, int offset, int limit, String query, boolean archived) {
        String encoded = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        return get(owner, "/sessions?offset="+offset+"&limit="+limit+"&query="+encoded+"&archived="+archived);
    }
    public JsonNode messages(UUID owner, UUID id, Integer offset, int limit, String mode, Long beforeSeq) {
        if (mode != null || beforeSeq != null) {
            if (!"cursor".equals(mode) || offset != null) throw DshProblems.error(400,"DSH_ARGUMENT_INVALID","游标模式必须指定 mode=cursor，且不能与 offset 混用。");
            return get(owner,"/sessions/"+id+"/messages?mode=cursor&limit="+limit+(beforeSeq == null ? "" : "&beforeSeq="+beforeSeq));
        }
        return get(owner,"/sessions/"+id+"/messages?offset="+(offset == null ? 0 : offset)+"&limit="+limit);
    }
    public JsonNode get(UUID owner,String suffix) { ensure(owner);return bridge.get(bridge.userPath(owner,suffix)); }
    public JsonNode post(UUID owner,String suffix,Object body) { ensure(owner);return bridge.post(bridge.userPath(owner,suffix),body); }
    public org.springframework.http.ResponseEntity<JsonNode> command(UUID owner,String suffix,Object body) {
        ensure(owner);return bridge.exchangeResponse("POST",bridge.userPath(owner,suffix),body);
    }
    public JsonNode send(UUID owner,UUID sessionId,Object body) {
        ensure(owner);
        bridge.post(bridge.userPath(owner,"/sessions/"+sessionId+"/actions/resume"),Map.of());
        return bridge.post(bridge.userPath(owner,"/sessions/"+sessionId+"/messages"),body);
    }
}

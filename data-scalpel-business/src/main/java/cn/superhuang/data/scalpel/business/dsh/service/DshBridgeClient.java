package cn.superhuang.data.scalpel.business.dsh.service;

import cn.superhuang.data.scalpel.business.dsh.config.DshProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.UUID;
@Service
public class DshBridgeClient {
    private final DshProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;
    public DshBridgeClient(DshProperties p,ObjectMapper m) {
        properties=p; mapper=m;
        http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(p.getConnectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public void requireEnabled() {
        if(!properties.getEnabled()) throw DshProblems.error(503,"DSH_DISABLED","DSH 接入尚未启用。");
        if(properties.getBridgeKey().length()<32) throw DshProblems.error(503,"DSH_CONFIGURATION_INVALID","DSH Bridge 配置未就绪。");
    }
    public URI uri(String path) {
        URI root=URI.create(properties.getBaseUrl());
        if(!java.util.Set.of("http","https").contains(root.getScheme()) || root.getHost()==null || root.getUserInfo()!=null || root.getQuery()!=null || root.getFragment()!=null || root.getPath()!=null&&!root.getPath().isEmpty()&&!root.getPath().equals("/"))
            throw DshProblems.error(503,"DSH_CONFIGURATION_INVALID","DSH 地址必须是固定 HTTP 服务源地址。");
        return root.resolve(path);
    }
    public String userPath(UUID userId,String suffix) { return "/bridge/v3/users/"+userId+suffix; }
    public JsonNode get(String path) { return exchange("GET",path,null); }
    public JsonNode post(String path,Object body) { return exchange("POST",path,body); }
    public JsonNode exchange(String method,String path,Object body) {
        return exchangeResponse(method,path,body).getBody();
    }
    public ResponseEntity<JsonNode> exchangeResponse(String method,String path,Object body) {
        requireEnabled();
        byte[] bytes=body==null?new byte[0]:mapper.writeValueAsBytes(body);
        int requestLimit = path.matches("/bridge/v3/users/[a-f0-9-]{36}/sessions/[a-f0-9-]{36}/attachments")
                ? DshAttachmentService.MAX_BRIDGE_REQUEST_BYTES : properties.getMaxRequestBytes();
        if(bytes.length>requestLimit) throw DshProblems.error(413,"DSH_REQUEST_TOO_LARGE","请求超过 DSH 大小限制。");
        var request=HttpRequest.newBuilder(uri(path)).timeout(properties.getRequestTimeout())
                .header("Authorization","Bearer "+properties.getBridgeKey()).header("Content-Type","application/json")
                .method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        CompletableFuture<HttpResponse<byte[]>> pending=http.sendAsync(request, info -> new BoundedBody(properties.getMaxResponseBytes()));
        try {
            long timeout = path.equals("/bridge/v3/active-users") || path.equals("/bridge/v3/actions/renew-leases") ? 3000 : properties.getRequestTimeout().toMillis();
            var response=pending.get(timeout,TimeUnit.MILLISECONDS);
            JsonNode value=mapper.readTree(response.body());
            if(value==null || !value.isObject()) throw DshProblems.error(502,"DSH_RESULT_UNCERTAIN","未取得完整结果，请查询会话状态。");
            if(response.statusCode()>=300) throw DshProblems.error(response.statusCode(),value.path("code").asText("DSH_UPSTREAM_ERROR"),value.path("detail").asText("DSH 请求失败。"));
            return ResponseEntity.status(response.statusCode()).body(value);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();throw DshProblems.error(502,"DSH_RESULT_UNCERTAIN","请求连接中断，结果无法确认，请查询状态。");
        } catch(ExecutionException | TimeoutException | tools.jackson.core.JacksonException e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("DSH response unavailable: {} / {}",e.getClass().getSimpleName(), e.getCause()==null?"none":e.getCause().getClass().getSimpleName());
            throw DshProblems.error(502,"DSH_RESULT_UNCERTAIN","DSH 连接超时或响应无法完整读取，结果无法确认，请查询状态，不要直接重复提交。");
        } finally { if(!pending.isDone()) pending.cancel(true); }
    }
    /** Enforce the byte budget while receiving, including chunked responses. */
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private java.util.concurrent.Flow.Subscription subscription;
        BoundedBody(int limit) { this.limit=limit; }
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(java.util.concurrent.Flow.Subscription s) { subscription=s;s.request(1); }
        public void onNext(java.util.List<java.nio.ByteBuffer> buffers) {
            for(var buffer:buffers) {
                if(buffer.remaining()>limit-data.size()) { subscription.cancel();result.completeExceptionally(new java.io.IOException("DSH response limit"));return; }
                byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);data.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(data.toByteArray()); }
    }
    public java.util.concurrent.CompletableFuture<WebSocket> connect(UUID userId,UUID sessionId,WebSocket.Listener listener) {
        requireEnabled();
        String address=uri(userPath(userId,"/events?sessionId="+sessionId)).toString().replaceFirst("^http","ws");
        return http.newWebSocketBuilder().connectTimeout(properties.getConnectTimeout())
                .header("Authorization","Bearer "+properties.getBridgeKey()).buildAsync(URI.create(address),listener);
    }
    public void provision(DshBindingService.Credential c) {
        post(userPath(c.userId(),"/identity/actions/provision"),Map.of("tokenId",c.tokenId(),"revision",c.revision(),"secret",c.secret()));
    }
}

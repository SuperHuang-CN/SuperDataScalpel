package cn.superhuang.data.scalpel.business.systemmcp.service;
import cn.superhuang.data.scalpel.business.systemmcp.config.SystemMcpProperties;
import cn.superhuang.data.scalpel.business.systemmcp.security.*;
import cn.superhuang.data.scalpel.business.systemmcp.web.response.SystemMcpInvokeResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ObjectNode;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
@Service
public class SystemMcpInvocationService {
    private final SystemMcpAuthorization authorization;
    private final SystemMcpTokenService tokens;
    private final SystemMcpSchemaService schemas;
    private final SystemMcpLocalEndpoint local;
    private final SystemMcpProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;
    public SystemMcpInvocationService(SystemMcpAuthorization a,SystemMcpTokenService t,SystemMcpSchemaService s,SystemMcpLocalEndpoint l,SystemMcpProperties p,ObjectMapper m) {
        authorization=a;
        tokens=t;
        schemas=s;
        local=l;
        properties=p;
        mapper=m;
        client=HttpClient.newBuilder().connectTimeout(p.getConnectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public SystemMcpInvokeResponse invoke(SystemMcpAuthentication original,JsonNode arguments) {
        String id=arguments.path("operationId").asText();
        var identity=tokens.authenticate(original.forwardingBearer());
        var api=authorization.require(id,identity);
        JsonNode contract=mapper.readTree(api.getContractJson());
        ObjectNode input=((ObjectNode)arguments).deepCopy();
        input.remove("operationId");
        schemas.validate(contract.path("inputSchema"),input);
        String path=api.getPath();
        for(String key:input.path("pathParams").propertyNames()) {
            String value=input.path("pathParams").path(key).asText();
            if(value.equals(".")||value.equals("..")||value.contains("/")||value.contains("\\")||value.contains("%"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"路径参数包含不支持的字符");
            path=path.replace("{"+key+"}",encode(value));
        }
        if(path.contains("{")||path.contains("}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"路径参数不完整");
        List<String> query=new ArrayList<>();
        for(JsonNode parameter:contract.path("parameters")) {
            if(!"query".equals(parameter.path("in").asText()))continue;
            String key=parameter.path("name").asText();
            JsonNode value=input.path("queryParams").get(key);
            if(value==null)continue;
            if(value.isArray()) {
                List<String> values=new ArrayList<>();
                for(JsonNode item:value)values.add(scalar(item));
                if(parameter.path("explode").asBoolean(true))values.forEach(v->query.add(encode(key)+"="+encode(v)));
                else query.add(encode(key)+"="+encode(String.join(",",values)));
            }
            else query.add(encode(key)+"="+encode(scalar(value)));
        }
        URI target=URI.create(local.base().toString()+path+(query.isEmpty()?"":"?"+String.join("&",query)));
        var builder=HttpRequest.newBuilder(target).timeout(properties.getInvokeTimeout()).header("Authorization","Bearer "+identity.forwardingBearer()).header("Accept","application/json, application/problem+json");
        byte[] body=input.has("body")?mapper.writeValueAsBytes(input.get("body")):new byte[0];
        if(body.length>properties.getMaxRequestBytes())throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"业务请求超过上限");
        if(input.has("body")) {
            String media="application/json";
            for(String candidate:contract.path("requestBody").path("content").propertyNames())if(candidate.equals("application/json")||candidate.endsWith("+json")) {
                media=candidate;
                break;
            }
            builder.header("Content-Type",media);
        }
        builder.method(api.getMethod(),body.length==0?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));
        AtomicInteger status=new AtomicInteger();
        CompletableFuture<HttpResponse<byte[]>> future=null;
        try {
            future=client.sendAsync(builder.build(), info-> {
                status.set(info.statusCode());return new LimitedSubscriber(properties.getMaxResponseBytes());
            });
            var response=future.get(properties.getInvokeTimeout().toMillis(),TimeUnit.MILLISECONDS);
            int code=response.statusCode();
            byte[] bytes=response.body();
            if(bytes.length==0)return new SystemMcpInvokeResponse(id,"RESPONDED",code,null,null,code>=300?"UNSUCCESSFUL_HTTP_STATUS":null,null);
            String media=response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].trim().toLowerCase(Locale.ROOT);
            if(!media.equals("application/json")&&!(media.startsWith("application/")&&media.endsWith("+json")))return incomplete(id,code,"UNSUPPORTED_RESPONSE","接口已响应，但响应格式不受支持；请核实业务结果");
            JsonNode data;
            try {
                data=mapper.readTree(bytes);
            }
            catch(RuntimeException e) {
                return incomplete(id,code,"INVALID_JSON_RESPONSE","接口已响应，但 JSON 无法解析；请核实业务结果");
            }
            return new SystemMcpInvokeResponse(id,"RESPONDED",code,code<400?data:null,code>=400?data:null,code>=400?data.path("code").asText("BUSINESS_ERROR"):code>=300?"REDIRECT_NOT_FOLLOWED":null,null);
        }
        catch(Exception e) {
            if(future!=null)future.cancel(true);
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            return status.get()>0?incomplete(id,status.get(),"RESPONSE_INCOMPLETE","已收到 HTTP 状态，但响应未完整读取；请查询业务状态，不要直接重复提交"):new SystemMcpInvokeResponse(id,"UNKNOWN",null,null,null,"OUTCOME_UNKNOWN","调用结果不确定，请查询业务状态；不要直接重复提交");
        }
    }
    @jakarta.annotation.PreDestroy
    public void close() { client.shutdownNow(); }
    private static SystemMcpInvokeResponse incomplete(String id,int status,String code,String message) {
        return new SystemMcpInvokeResponse(id,"RESPONDED_INCOMPLETE",status,null,null,code,message);
    }
    private static String scalar(JsonNode v) {
        if((v.isObject()||v.isArray())||v.isNull())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"查询参数仅支持标量及标量数组");
        return v.asText();
    }
    public static String encode(String value) {
        return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");
    }
    private static final class LimitedSubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private Flow.Subscription subscription;
        LimitedSubscriber(int limit) {
            this.limit=limit;
        }
        public CompletionStage<byte[]> getBody() {
            return result;
        }
        public void onSubscribe(Flow.Subscription s) {
            subscription=s;
            s.request(1);
            result.whenComplete((v,e)-> {
                if(result.isCancelled())s.cancel();
            });
        }
        public void onNext(List<ByteBuffer> items) {
            for(var item:items) {
                if(item.remaining()>limit-bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("响应超过读取上限"));
                    return;
                }
                byte[] chunk=new byte[item.remaining()];
                item.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable e) {
            result.completeExceptionally(e);
        }
        public void onComplete() {
            result.complete(bytes.toByteArray());
        }
    }
}

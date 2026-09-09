package cn.superhuang.data.scalpel.business.systemmcp.web.resource;
import cn.superhuang.data.scalpel.business.systemmcp.service.SystemMcpProtocolService;
import cn.superhuang.data.scalpel.business.systemmcp.config.SystemMcpProperties;
import cn.superhuang.data.scalpel.business.systemmcp.security.SystemMcpAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.annotation.PreDestroy;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.*;
import java.io.IOException;
@RestController
public class SystemMcpProtocolResource {
    private final SystemMcpProtocolService protocol;
    private final SystemMcpProperties properties;
    private final ThreadPoolExecutor executor;
    public SystemMcpProtocolResource(SystemMcpProtocolService s,SystemMcpProperties p) {
        protocol=s;
        properties=p;
        executor=new ThreadPoolExecutor(p.getConcurrency(),p.getConcurrency(),0,TimeUnit.MILLISECONDS,new SynchronousQueue<>(),Thread.ofPlatform().name("system-mcp-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
    }
    @PostMapping(value="/system-mcp",consumes="application/json",produces="application/json")
    public DeferredResult<ResponseEntity<?>> handle(HttpServletRequest request,SystemMcpAuthentication identity)throws IOException {
        if(request.getContentLengthLong()>properties.getMaxRequestBytes())throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"MCP 请求超过上限");
        byte[] bytes=request.getInputStream().readNBytes(properties.getMaxRequestBytes()+1);
        if(bytes.length>properties.getMaxRequestBytes())throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"MCP 请求超过上限");
        var result=new DeferredResult<ResponseEntity<?>>(properties.getInvokeTimeout().plusSeconds(5).toMillis());
        String version=request.getHeader("MCP-Protocol-Version");
        try {
            executor.execute(()-> {
                try {
                    result.setResult(protocol.handle(identity,bytes,version));
                }
                catch(Exception e) {
                    result.setErrorResult(e);
                }
            });
        }
        catch(RejectedExecutionException e) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"系统 MCP 正忙，请稍后再试");
        }
        return result;
    }
    @PreDestroy public void close() {
        executor.shutdownNow();
    }
}

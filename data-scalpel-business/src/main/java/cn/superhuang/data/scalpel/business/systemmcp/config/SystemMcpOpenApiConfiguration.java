package cn.superhuang.data.scalpel.business.systemmcp.config;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.AnnotatedElementUtils;
import java.util.Map;
@Configuration(proxyBeanMethods=false)
public class SystemMcpOpenApiConfiguration {
    @Bean OperationCustomizer systemMcpSemantics() {
        return (operation,handler)-> {
            var meta=AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(),SystemMcpOperation.class);
            if(meta!=null && (operation.getSummary()==null||operation.getSummary().isBlank()))operation.setSummary(meta.summary());
            if(meta!=null)operation.addExtension("x-system-mcp",Map.of("effect",meta.value().name(),"keywords",meta.keywords(),"prerequisites",meta.prerequisites(),"relatedOperations",meta.relatedOperations()));
            return operation;
        };
    }
}

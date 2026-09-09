package cn.superhuang.data.scalpel.business.systemmcp.metadata;
import java.lang.annotation.*;
/** Explicit business semantics; new undeclared operations cannot be exposed. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SystemMcpOperation {
    Effect value();
    String summary() default "";
    String[] keywords() default {
    };
    String prerequisites() default "";
    String[] relatedOperations() default {
    };
    enum Effect {
        READ, WRITE, EXECUTE
    }
}

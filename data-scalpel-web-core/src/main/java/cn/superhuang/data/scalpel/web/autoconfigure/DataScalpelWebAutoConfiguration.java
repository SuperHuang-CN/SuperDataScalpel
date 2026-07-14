package cn.superhuang.data.scalpel.web.autoconfigure;

import cn.superhuang.data.scalpel.web.error.ProblemDetailsExceptionHandler;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(ProblemDetailsExceptionHandler.class)
public class DataScalpelWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SearchEngine searchEngine() {
        return new SearchEngine();
    }
}

package cn.superhuang.data.scalpel.web.autoconfigure;

import cn.superhuang.data.scalpel.web.error.ProblemDetailsExceptionHandler;
import cn.superhuang.data.scalpel.web.error.ProblemDetailFactory;
import cn.superhuang.data.scalpel.web.error.ProblemDetailWriter;
import cn.superhuang.data.scalpel.search.SearchEngine;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class DataScalpelWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ProblemDetailFactory problemDetailFactory() {
        return new ProblemDetailFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    ProblemDetailWriter problemDetailWriter(ObjectMapper objectMapper, ProblemDetailFactory problemDetailFactory) {
        return new ProblemDetailWriter(objectMapper, problemDetailFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    ProblemDetailsExceptionHandler problemDetailsExceptionHandler(ProblemDetailFactory problemDetailFactory) {
        return new ProblemDetailsExceptionHandler(problemDetailFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    SearchEngine searchEngine() {
        return new SearchEngine();
    }
}

package cn.superhuang.data.scalpel.dispatcher.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DispatcherWebConfiguration implements WebMvcConfigurer {
    private final DispatcherTokenInterceptor tokenInterceptor;

    public DispatcherWebConfiguration(DispatcherTokenInterceptor tokenInterceptor) {
        this.tokenInterceptor = tokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenInterceptor).addPathPatterns("/api/v1/**");
    }
}

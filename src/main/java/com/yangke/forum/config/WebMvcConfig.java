package com.yangke.forum.config;

import com.yangke.forum.module.auth.interceptor.AuthInterceptor;
import com.yangke.forum.module.auth.interceptor.AdminInterceptor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private AuthInterceptor authInterceptor;

    @Resource
    private com.yangke.forum.common.RateLimitInterceptor rateLimitInterceptor;

    @Resource
    private AdminInterceptor adminInterceptor;

    @Bean
    public FilterRegistrationBean<CharacterEncodingFilter> encodingFilter() {
        FilterRegistrationBean<CharacterEncodingFilter> bean = new FilterRegistrationBean<>();
        CharacterEncodingFilter filter = new CharacterEncodingFilter();
        filter.setEncoding("UTF-8");
        filter.setForceEncoding(true);
        bean.setFilter(filter);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/captcha",
                        "/api/auth/activate/**",
                        "/api/search/**",
                        "/api/user/{userId:\\d+}",
                        "/api/sse/**",
                        "/api/categories"
                );

        // 限流拦截器：匹配所有 /api/**，依赖 AuthInterceptor 先设置 userId
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");

        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 头像静态资源映射
        registry.addResourceHandler("/avatar/**")
                .addResourceLocations("file:./uploads/avatar/");
        // Swagger 静态资源
        registry.addResourceHandler("/doc.html", "/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/");
    }
}

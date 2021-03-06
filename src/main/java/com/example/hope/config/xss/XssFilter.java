package com.example.hope.config.xss;

import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * @description: XSS过滤器
 * @author: DHY
 * @created: 2020/10/23 23:56
 */

@WebFilter
@Component
public class XssFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig){
    }
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        XssAndSqlHttpServletRequestWrapper xssRequestWrapper = new XssAndSqlHttpServletRequestWrapper(req);
        chain.doFilter(xssRequestWrapper, response);
    }
    @Override
    public void destroy() {
    }
//    /**
//     * 过滤json类型的
//     * @param builder
//     * @return
//     */
//    @Bean
//    @Primary
//    public ObjectMapper xssObjectMapper(Jackson2ObjectMapperBuilder builder) {
//        //解析器
//        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
//        //注册xss解析器
//        SimpleModule xssModule = new SimpleModule("XssStringJsonSerializer");
//        xssModule.addSerializer(new XssStringJsonSerializer());
//        objectMapper.registerModule(xssModule);
//        //返回
//        return objectMapper;
//    }
}

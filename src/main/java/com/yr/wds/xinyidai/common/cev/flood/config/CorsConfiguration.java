//package com.yr.wds.xinyidai.common.cev.flood.config;
//
//import org.springframework.boot.web.servlet.FilterRegistrationBean;
//import org.springframework.boot.web.servlet.ServletRegistrationBean;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
//import org.springframework.web.filter.CorsFilter;
//
//@Configuration
//public class CorsConfiguration {
//
//    @Bean
//    public FilterRegistrationBean corsFilter() {
//    	UrlBasedCorsConfigurationSource configSource = new UrlBasedCorsConfigurationSource();
//    	org.springframework.web.cors.CorsConfiguration corsConfiguration = new org.springframework.web.cors.CorsConfiguration();
//        corsConfiguration.setAllowCredentials(true);
//        corsConfiguration.addAllowedOrigin("*");
//        corsConfiguration.addAllowedHeader("*");
//        corsConfiguration.addAllowedMethod("*");
//        configSource.registerCorsConfiguration("/**", corsConfiguration);
//        FilterRegistrationBean bean = new FilterRegistrationBean(new CorsFilter(configSource), new ServletRegistrationBean[0]);
//        bean.setOrder(1);
//        return bean;
//    }
//}

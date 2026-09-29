package com.example.config;

import com.example.grade.GradesJdbc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 성적 집계 MS-SQL 연결 설정 — application.yml 의 {@code grades.datasource.*}. */
@Configuration
public class GradesJdbcConfig {

    @Bean(destroyMethod = "close")
    public GradesJdbc gradesJdbc(
        @Value("${grades.datasource.url}") String url,
        @Value("${grades.datasource.username}") String username,
        @Value("${grades.datasource.password:}") String password,
        @Value("${grades.datasource.maximum-pool-size:2}") int maximumPoolSize,
        @Value("${grades.datasource.connection-timeout:3000}") long connectionTimeoutMs) {
        return new GradesJdbc(url, username, password, maximumPoolSize, connectionTimeoutMs);
    }
}

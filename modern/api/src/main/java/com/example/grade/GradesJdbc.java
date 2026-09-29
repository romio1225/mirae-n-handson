package com.example.grade;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 성적 집계 MS-SQL(grades DB) 전용 연결.
 *
 * <p>일부러 {@code DataSource} · {@code JdbcOperations} 타입의 빈으로 드러내지 않는다.
 * 그런 빈이 하나 더 생기면 Spring Boot 가 기본 DataSource(MariaDB · itembank-pool) 자동 설정을 건너뛰어 JPA 가 깨진다.
 *
 * <p>풀은 첫 조회 때 연결한다(설정자 방식의 {@code HikariDataSource}). 그래서 MS-SQL 이 없는 환경
 * (테스트 프로필 H2, MariaDB 만 띄운 개발 환경)에서도 애플리케이션은 기동한다.
 */
public final class GradesJdbc implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final NamedParameterJdbcTemplate template;

    public GradesJdbc(String url, String username, String password, int maximumPoolSize, long connectionTimeoutMs) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("grades-pool");
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(maximumPoolSize);
        ds.setConnectionTimeout(connectionTimeoutMs);
        ds.setReadOnly(true);
        this.dataSource = ds;
        this.template = new NamedParameterJdbcTemplate(ds);
    }

    public NamedParameterJdbcTemplate template() {
        return template;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}

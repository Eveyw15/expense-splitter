package com.elec5619.group3.trip_planner_backend.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds the DataSource from Neon's libpq-style connection string
 * (postgresql://user:pass@host/db?sslmode=require), which JDBC can't take as-is.
 * Prefers the direct (unpooled) endpoint: Hibernate runs schema updates at startup
 * and Hikari already pools connections in this long-running server.
 */
@Configuration
@ConditionalOnExpression("'${DATABASE_URL_UNPOOLED:${DATABASE_URL:}}' != ''")
public class NeonDataSourceConfig {

    @Bean
    public DataSource dataSource(@Value("${DATABASE_URL_UNPOOLED:${DATABASE_URL:}}") String connectionString) {
        URI uri = URI.create(connectionString);
        String[] credentials = uri.getRawUserInfo().split(":", 2);
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery().replace("channel_binding=", "channelBinding=");

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + uri.getRawPath() + query);
        dataSource.setUsername(URLDecoder.decode(credentials[0], StandardCharsets.UTF_8));
        dataSource.setPassword(URLDecoder.decode(credentials[1], StandardCharsets.UTF_8));
        dataSource.setMaximumPoolSize(5);
        return dataSource;
    }
}

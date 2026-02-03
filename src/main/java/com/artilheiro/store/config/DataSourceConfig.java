package com.artilheiro.store.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Normaliza DATABASE_URL no formato de provedores cloud (ex.: Render, Heroku)
 * "postgresql://user:password@host/database" para o formato JDBC esperado pelo driver.
 * Quando DATABASE_URL está definido, este bean substitui o DataSource padrão.
 */
@Configuration
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
public class DataSourceConfig {

    @Bean
    @ConditionalOnProperty(name = "DATABASE_URL", matchIfMissing = false)
    public DataSource dataSourceFromDatabaseUrl(Environment env) {
        String databaseUrl = env.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            throw new IllegalStateException("DATABASE_URL está vazio.");
        }

        if (databaseUrl.startsWith("jdbc:")) {
            return buildFromJdbcUrl(env, databaseUrl);
        }
        return buildFromPostgresUrl(databaseUrl);
    }

    private static DataSource buildFromJdbcUrl(Environment env, String jdbcUrl) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(env.getProperty("DATABASE_USERNAME", ""));
        config.setPassword(env.getProperty("DATABASE_PASSWORD", ""));
        config.setDriverClassName("org.postgresql.Driver");
        return new HikariDataSource(config);
    }

    /**
     * Parse postgresql://user:password@host:port/database?params ou postgres://...
     * e monta DataSource com URL JDBC + user/password (compatível com Render, Heroku, etc.).
     */
    private static DataSource buildFromPostgresUrl(String databaseUrl) {
        try {
            if (!databaseUrl.startsWith("postgresql://") && !databaseUrl.startsWith("postgres://")) {
                throw new IllegalArgumentException("DATABASE_URL deve começar com postgresql:// ou jdbc:postgresql://");
            }
            String uriPart = databaseUrl.replaceFirst("^postgres(ql)?://", "");
            int at = uriPart.indexOf('@');
            if (at <= 0) {
                throw new IllegalArgumentException("DATABASE_URL deve conter user:password@host");
            }
            String userInfo = uriPart.substring(0, at);
            String hostDb = uriPart.substring(at + 1);

            int firstColon = userInfo.indexOf(':');
            String username = firstColon > 0
                    ? URLDecoder.decode(userInfo.substring(0, firstColon), StandardCharsets.UTF_8)
                    : userInfo;
            String password = firstColon > 0 && firstColon < userInfo.length() - 1
                    ? URLDecoder.decode(userInfo.substring(firstColon + 1), StandardCharsets.UTF_8)
                    : "";

            // host/database ou host:port/database
            String hostPort;
            String pathAndQuery;
            int slash = hostDb.indexOf('/');
            if (slash > 0) {
                hostPort = hostDb.substring(0, slash);
                pathAndQuery = hostDb.substring(slash);
            } else {
                hostPort = hostDb;
                pathAndQuery = "/";
            }
            if (!hostPort.contains(":")) {
                hostPort = hostPort + ":5432";
            }
            if (!pathAndQuery.startsWith("/")) {
                pathAndQuery = "/" + pathAndQuery;
            }
            String query = "";
            int q = pathAndQuery.indexOf('?');
            if (q > 0) {
                query = pathAndQuery.substring(q);
                pathAndQuery = pathAndQuery.substring(0, q);
            }
            if (query.isEmpty() || !query.contains("sslmode")) {
                query = query.isEmpty() ? "?sslmode=require" : query + "&sslmode=require";
            } else if (!query.startsWith("?")) {
                query = "?" + query;
            }

            String jdbcUrl = "jdbc:postgresql://" + hostPort + pathAndQuery + query;

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("org.postgresql.Driver");
            return new HikariDataSource(config);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao interpretar DATABASE_URL. Use jdbc:postgresql://host:port/db ou postgresql://user:pass@host/db.", e);
        }
    }
}

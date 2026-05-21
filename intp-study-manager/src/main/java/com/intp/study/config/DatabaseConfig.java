package com.intp.study.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class DatabaseConfig {
    @Bean
    public DataSource dataSource(@Value("${app.database.path:data/study_manager.db}") String databasePath) throws Exception {
        Path path = Path.of(databasePath).toAbsolutePath().normalize();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        return DataSourceBuilder.create()
                .driverClassName("org.sqlite.JDBC")
                .url("jdbc:sqlite:" + path)
                .build();
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("PRAGMA foreign_keys = ON");
        jdbcTemplate.execute("PRAGMA busy_timeout = 5000");
        jdbcTemplate.execute("PRAGMA synchronous = NORMAL");
        return jdbcTemplate;
    }
}

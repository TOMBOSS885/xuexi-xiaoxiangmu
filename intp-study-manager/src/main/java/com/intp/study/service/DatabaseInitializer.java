package com.intp.study.service;

import com.intp.study.repository.SqlRepository;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;

@Service
public class DatabaseInitializer implements ApplicationRunner {
    private static final String SCHEMA_RESOURCE = "db/schema.sql";

    private final SqlRepository repo;
    private final DataSource dataSource;

    public DatabaseInitializer(SqlRepository repo, DataSource dataSource) {
        this.repo = repo;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        initialize();
    }

    public void initialize() {
        repo.jdbc().execute("PRAGMA journal_mode = WAL");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(SCHEMA_RESOURCE));
        populator.setContinueOnError(false);
        populator.execute(dataSource);
        ensureLegacyColumns();
    }

    private void ensureLegacyColumns() {
        ensureColumn("ppt_decks", "category", "TEXT DEFAULT ''");
        ensureColumn("ppt_decks", "sort_order", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("ppt_decks", "status", "TEXT NOT NULL DEFAULT '使用中'");
        ensureColumn("ppt_slides", "image_path", "TEXT DEFAULT ''");
        ensureColumn("slide_questions", "category", "TEXT DEFAULT ''");
        ensureColumn("slide_questions", "sort_order", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("slide_questions", "status", "TEXT NOT NULL DEFAULT '未整理'");
        repo.jdbc().execute("""
                CREATE INDEX IF NOT EXISTS idx_ppt_decks_manage
                    ON ppt_decks(status, category, sort_order ASC, created_at DESC, id DESC)
                """);
        repo.jdbc().execute("""
                CREATE INDEX IF NOT EXISTS idx_slide_questions_manage
                    ON slide_questions(status, category, sort_order ASC, created_at DESC, id DESC)
                """);
    }

    private void ensureColumn(String table, String column, String definition) {
        Set<String> columns = repo.query("PRAGMA table_info(" + table + ")").stream()
                .map(row -> String.valueOf(row.get("name")))
                .collect(Collectors.toSet());
        if (!columns.contains(column)) {
            repo.jdbc().execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }
}

package com.iflytek.skillhub.infra.repository;

import com.iflytek.skillhub.domain.knowledge.KnowledgeContentSearchRepository;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL implementation of knowledge search. Title and description keep substring matching;
 * summaries and extracted text use the application-built {@code simple} tsquery. Only the current
 * published version is joined, and archived or hidden files never match.
 */
@Repository
public class JdbcKnowledgeContentSearchRepository implements KnowledgeContentSearchRepository {
    private static final int SNIPPET_BEFORE = 60;
    private static final int SNIPPET_LENGTH = 200;

    private static final String FROM = """
            FROM knowledge_document d
            LEFT JOIN knowledge_document_text t ON t.version_id = d.published_version_id AND t.status = 'COMPLETED'
            LEFT JOIN knowledge_document_summary s ON s.version_id = d.published_version_id AND s.status = 'COMPLETED'
            CROSS JOIN (SELECT CASE WHEN :tsq = '' THEN NULL ELSE to_tsquery('simple', :tsq) END AS q) query
            WHERE d.knowledge_base_id IN (:baseIds) AND d.status = 'ACTIVE' AND d.hidden = FALSE
              AND (lower(d.title) LIKE :pattern ESCAPE '\\' OR lower(coalesce(d.description, '')) LIKE :pattern ESCAPE '\\'
                   OR s.search_vector @@ query.q OR t.search_vector @@ query.q)
            """;

    // Title hits rank first, then summary or keyword hits, then description, then body text.
    private static final String SELECT = """
            SELECT d.id,
              CASE WHEN :first <> '' AND t.content IS NOT NULL AND strpos(lower(t.content), :first) > 0
                THEN substring(t.content FROM greatest(strpos(lower(t.content), :first) - %d, 1) FOR %d) END AS snippet
            """.formatted(SNIPPET_BEFORE, SNIPPET_LENGTH) + FROM + """
            ORDER BY (CASE WHEN lower(d.title) LIKE :pattern ESCAPE '\\' THEN 8 ELSE 0 END
                    + CASE WHEN s.search_vector @@ query.q THEN 4 ELSE 0 END
                    + CASE WHEN lower(coalesce(d.description, '')) LIKE :pattern ESCAPE '\\' THEN 2 ELSE 0 END
                    + CASE WHEN t.search_vector @@ query.q THEN 1 ELSE 0 END) DESC,
                     coalesce(ts_rank(t.search_vector, query.q), 0) DESC, d.updated_at DESC, d.id DESC
            LIMIT :limit OFFSET :offset
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcKnowledgeContentSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page<Hit> search(Collection<Long> knowledgeBaseIds, String keyword, String tsQuery, Pageable pageable) {
        String normalized = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || normalized.isEmpty()) {
            return Page.empty(pageable);
        }
        var params = new MapSqlParameterSource()
                .addValue("baseIds", knowledgeBaseIds)
                .addValue("pattern", "%" + escapeLike(normalized) + "%")
                .addValue("tsq", tsQuery == null ? "" : tsQuery)
                .addValue("first", normalized.split("\\s+", 2)[0])
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        Long total = jdbc.queryForObject("SELECT count(*) " + FROM, params, Long.class);
        if (total == null || total == 0) {
            return Page.empty(pageable);
        }
        List<Hit> hits = jdbc.query(SELECT, params,
                (row, index) -> new Hit(row.getLong("id"), cleanSnippet(row.getString("snippet"))));
        return new PageImpl<>(hits, pageable, total);
    }

    static String cleanSnippet(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.replaceAll("[#>*`|]+", " ").replaceAll("\\s+", " ").strip();
        return text.isEmpty() ? null : "…" + text + "…";
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

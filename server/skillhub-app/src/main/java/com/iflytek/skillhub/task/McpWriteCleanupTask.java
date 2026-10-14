package com.iflytek.skillhub.task;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Idempotency results contain metadata only; keep them for the documented 30-day retry window. */
@Component
public class McpWriteCleanupTask {
    private final JdbcTemplate jdbc;
    public McpWriteCleanupTask(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Scheduled(cron = "0 15 2 * * ?")
    @Transactional
    public void cleanup() { jdbc.update("delete from mcp_write_request where created_at < now() - interval '30 days'"); }
}

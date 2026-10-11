package com.iflytek.skillhub;

import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;

/**
 * PostgreSQL dialect for the H2 (MODE=PostgreSQL) test database.
 *
 * <p>Hibernate 6.5+ makes {@link PostgreSQLDialect} fetch generated ids with
 * {@code INSERT ... RETURNING}, which H2 does not support. Disabling RETURNING makes
 * Hibernate fall back to JDBC generated keys, which H2 does support, while keeping the
 * PostgreSQL SQL/HQL semantics the application is written against.
 */
public class H2CompatiblePostgreSQLDialect extends PostgreSQLDialect {

    public H2CompatiblePostgreSQLDialect() {
        super();
    }

    public H2CompatiblePostgreSQLDialect(DialectResolutionInfo info) {
        super(info);
    }

    public H2CompatiblePostgreSQLDialect(DatabaseVersion version) {
        super(version);
    }

    @Override
    public boolean supportsInsertReturning() {
        return false;
    }

    @Override
    public boolean supportsUpdateReturning() {
        return false;
    }
}

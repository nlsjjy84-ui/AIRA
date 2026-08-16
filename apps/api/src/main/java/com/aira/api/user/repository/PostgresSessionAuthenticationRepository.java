package com.aira.api.user.repository;

import com.aira.api.auth.security.AiraPrincipal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresSessionAuthenticationRepository implements SessionAuthenticationRepository {
    private static final String AUTHENTICATE_AND_TOUCH = """
            WITH authentication_time AS MATERIALIZED (
                SELECT clock_timestamp() AS now
            )
            UPDATE user_session AS session
            SET last_seen_at = GREATEST(session.last_seen_at, authentication_time.now),
                idle_expires_at = LEAST(
                    GREATEST(session.last_seen_at, authentication_time.now) + interval '30 minutes',
                    session.absolute_expires_at)
            FROM authentication_time, app_user AS app_user,
                 authentication_credential AS credential
            WHERE session.token_hash = ?
              AND session.revoked_at IS NULL
              AND authentication_time.now < session.absolute_expires_at
              AND authentication_time.now < session.idle_expires_at
              AND app_user.id = session.user_id
              AND app_user.status = 'ACTIVE'
              AND credential.id = session.credential_id
              AND credential.user_id = app_user.id
              AND credential.status = 'ACTIVE'
            RETURNING session.user_id, app_user.nickname
            """;

    private final JdbcTemplate jdbc;

    public PostgresSessionAuthenticationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AiraPrincipal> authenticateAndTouch(byte[] tokenHash) {
        if (tokenHash == null || tokenHash.length == 0) return Optional.empty();
        List<AiraPrincipal> principals = jdbc.query(
                AUTHENTICATE_AND_TOUCH,
                (result, row) -> new AiraPrincipal(
                        result.getObject("user_id", java.util.UUID.class),
                        result.getString("nickname")),
                tokenHash.clone());
        return principals.stream().findFirst();
    }
}

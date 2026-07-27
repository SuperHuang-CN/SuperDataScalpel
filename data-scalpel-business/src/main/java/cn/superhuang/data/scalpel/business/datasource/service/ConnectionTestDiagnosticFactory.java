package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.business.datasource.web.response.ConnectionTestCauseResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.ConnectionTestDiagnosticResponse;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class ConnectionTestDiagnosticFactory {

    static final int MAX_CAUSE_COUNT = 8;
    static final int MAX_MESSAGE_LENGTH = 2_000;
    private static final String REDACTED = "[REDACTED]";
    private static final String TRUNCATED = "…（已截断）";

    private ConnectionTestDiagnosticFactory() {
    }

    static ConnectionTestDiagnosticResponse create(Throwable failure, String... sensitiveValues) {
        Objects.requireNonNull(failure, "failure");
        SQLException sqlException = findSqlException(failure);
        return new ConnectionTestDiagnosticResponse(
                failure.getClass().getName(),
                diagnosticMessage(failure, sensitiveValues),
                sqlException == null ? null : sqlException.getSQLState(),
                sqlException == null ? null : sqlException.getErrorCode(),
                null,
                null,
                causes(failure, sensitiveValues)
        );
    }

    static String sanitizedStackTrace(Throwable failure, String... sensitiveValues) {
        StringWriter output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return redact(output.toString(), sensitiveValues);
    }

    static String sanitizedText(String value, String... sensitiveValues) {
        return value == null ? null : redact(value, sensitiveValues);
    }

    static String[] httpSensitiveValues(HttpApiContracts.RuntimeConnection connection) {
        if (connection == null || connection.credentials() == null) {
            return new String[0];
        }
        HttpApiContracts.CredentialBundle credentials = connection.credentials();
        return java.util.stream.Stream.of(
                        credentials.password(), credentials.bearerToken(), credentials.apiKey(),
                        credentials.clientSecret(), credentials.tokenEndpointPassword(),
                        credentials.signingSecret(), credentials.signingPrivateKey())
                .filter(value -> value != null && !value.isEmpty())
                .distinct()
                .toArray(String[]::new);
    }

    private static List<ConnectionTestCauseResponse> causes(Throwable failure, String... sensitiveValues) {
        List<ConnectionTestCauseResponse> causes = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.add(failure);
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        enqueueRelated(failure, pending);

        while (!pending.isEmpty() && causes.size() < MAX_CAUSE_COUNT) {
            Throwable current = pending.removeFirst();
            if (!seen.add(current)) {
                continue;
            }
            causes.add(new ConnectionTestCauseResponse(
                    current.getClass().getName(),
                    diagnosticMessage(current, sensitiveValues)
            ));
            enqueueRelated(current, pending);
        }
        return List.copyOf(causes);
    }

    private static SQLException findSqlException(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!seen.add(current)) {
                continue;
            }
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            enqueueRelated(current, pending);
        }
        return null;
    }

    private static void enqueueRelated(Throwable failure, ArrayDeque<Throwable> pending) {
        if (failure.getCause() != null) {
            pending.addLast(failure.getCause());
        }
        if (failure instanceof SQLException sqlException && sqlException.getNextException() != null) {
            pending.addLast(sqlException.getNextException());
        }
    }

    private static String diagnosticMessage(Throwable failure, String... sensitiveValues) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return null;
        }
        String sanitized = redact(message, sensitiveValues);
        if (sanitized.length() <= MAX_MESSAGE_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_MESSAGE_LENGTH - TRUNCATED.length()) + TRUNCATED;
    }

    private static String redact(String value, String... sensitiveValues) {
        String sanitized = value;
        if (sensitiveValues == null) {
            return sanitized;
        }
        for (String sensitiveValue : sensitiveValues) {
            if (sensitiveValue != null && !sensitiveValue.isEmpty()) {
                sanitized = sanitized.replace(sensitiveValue, REDACTED);
            }
        }
        return sanitized;
    }
}

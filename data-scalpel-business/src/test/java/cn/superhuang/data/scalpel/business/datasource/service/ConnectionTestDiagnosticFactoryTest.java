package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionTestDiagnosticFactoryTest {

    @Test
    void exposesStructuredJdbcDiagnosticsWhileRedactingSecrets() {
        SQLException failure = new SQLException(
                "Communications link failure using password draft-secret",
                "08S01",
                1045
        );
        failure.initCause(new ConnectException("Connection refused for draft-secret"));
        failure.setNextException(new SQLException("Server did not accept the connection", "08006", 0));

        var diagnostic = ConnectionTestDiagnosticFactory.create(failure, "draft-secret");

        assertEquals(SQLException.class.getName(), diagnostic.exceptionType());
        assertEquals("Communications link failure using password [REDACTED]", diagnostic.rawMessage());
        assertEquals("08S01", diagnostic.sqlState());
        assertEquals(1045, diagnostic.vendorCode());
        assertEquals(2, diagnostic.causes().size());
        assertEquals(ConnectException.class.getName(), diagnostic.causes().getFirst().exceptionType());
        assertEquals("Connection refused for [REDACTED]", diagnostic.causes().getFirst().message());
        assertFalse(ConnectionTestDiagnosticFactory.sanitizedStackTrace(failure, "draft-secret")
                .contains("draft-secret"));
    }

    @Test
    void boundsCauseCountAndIndividualMessageLength() {
        Throwable cause = new RuntimeException("tail");
        for (int index = 0; index < 12; index++) {
            cause = new RuntimeException("cause-" + index, cause);
        }
        Throwable failure = new RuntimeException(
                "x".repeat(ConnectionTestDiagnosticFactory.MAX_MESSAGE_LENGTH + 100),
                cause
        );

        var diagnostic = ConnectionTestDiagnosticFactory.create(failure);

        assertEquals(ConnectionTestDiagnosticFactory.MAX_CAUSE_COUNT, diagnostic.causes().size());
        assertTrue(diagnostic.rawMessage().length() <= ConnectionTestDiagnosticFactory.MAX_MESSAGE_LENGTH);
    }

    @Test
    void collectsEveryHttpCredentialForDiagnosticsAndLogs() {
        HttpApiContracts.RuntimeConnection connection = new HttpApiContracts.RuntimeConnection(
                new HttpApiContracts.ConnectionConfiguration(
                        "https://api.example.test", java.util.List.of(), 1_000, 2_000, 0, 0,
                        new HttpApiContracts.NoneAuthentication(), true, true),
                new HttpApiContracts.CredentialBundle(
                        "password-value", "bearer-value", "api-key-value", "client-secret-value",
                        "endpoint-password-value", "signing-secret-value", "private-key-value"));
        RuntimeException failure = new RuntimeException(
                "password-value bearer-value api-key-value client-secret-value endpoint-password-value "
                        + "signing-secret-value private-key-value");
        String[] sensitiveValues = ConnectionTestDiagnosticFactory.httpSensitiveValues(connection);

        String sanitized = ConnectionTestDiagnosticFactory.sanitizedStackTrace(failure, sensitiveValues);

        assertEquals(7, sensitiveValues.length);
        assertFalse(sanitized.contains("password-value"));
        assertFalse(sanitized.contains("bearer-value"));
        assertFalse(sanitized.contains("api-key-value"));
        assertFalse(sanitized.contains("client-secret-value"));
        assertFalse(sanitized.contains("endpoint-password-value"));
        assertFalse(sanitized.contains("signing-secret-value"));
        assertFalse(sanitized.contains("private-key-value"));
    }
}

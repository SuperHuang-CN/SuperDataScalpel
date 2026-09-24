package cn.superhuang.data.scalpel.business.system.access.service;

import cn.superhuang.data.scalpel.business.system.access.repository.SystemPermissionRepository;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemRolePermissionRepository;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemRoleRepository;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SystemAccessInitializerTest {
    @Test
    void locksPermissionCatalogInTransactionBeforeReadingOrReplacingBootstrapRows() throws Exception {
        Fixture fixture = new Fixture();
        fixture.run();
        var order = inOrder(fixture.transactions, fixture.entityManager, fixture.query, fixture.initializer);
        order.verify(fixture.transactions).getTransaction(any());
        order.verify(fixture.entityManager).createNativeQuery("lock table sys_permission in share row exclusive mode");
        order.verify(fixture.query).executeUpdate();
        order.verify(fixture.initializer).synchronize(fixture.permissions, fixture.roles, fixture.grants,
                fixture.users, fixture.encoder, "admin", "test-password");
        order.verify(fixture.transactions).commit(any());
    }

    @Test
    void lockFailureRollsBackWithoutRunningSynchronization() {
        Fixture fixture = new Fixture();
        when(fixture.query.executeUpdate()).thenThrow(new IllegalStateException("lock unavailable"));
        assertThrows(IllegalStateException.class, fixture::run);
        verify(fixture.initializer, never()).synchronize(any(), any(), any(), any(), any(), any(), any());
        verify(fixture.transactions).rollback(any());
        verifyNoInteractions(fixture.permissions, fixture.roles, fixture.grants, fixture.users);
    }

    private static final class Fixture {
        final SystemAccessInitializer initializer = spy(new SystemAccessInitializer());
        final SystemPermissionRepository permissions = mock(SystemPermissionRepository.class);
        final SystemRoleRepository roles = mock(SystemRoleRepository.class);
        final SystemRolePermissionRepository grants = mock(SystemRolePermissionRepository.class);
        final SystemUserRepository users = mock(SystemUserRepository.class);
        final PasswordEncoder encoder = mock(PasswordEncoder.class);
        final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        final EntityManager entityManager = mock(EntityManager.class);
        final Query query = mock(Query.class);

        Fixture() {
            when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            when(entityManager.createNativeQuery("lock table sys_permission in share row exclusive mode"))
                    .thenReturn(query);
            doNothing().when(initializer).synchronize(permissions, roles, grants, users, encoder,
                    "admin", "test-password");
        }

        void run() throws Exception {
            initializer.initializeSystemAccess(permissions, roles, grants, users, encoder, transactions,
                    entityManager, "admin", "test-password").run(null);
        }
    }
}

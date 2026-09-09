package cn.superhuang.data.scalpel.admin.operations;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import cn.superhuang.data.scalpel.business.operations.repository.*;
import cn.superhuang.data.scalpel.business.operations.service.*;
import cn.superhuang.data.scalpel.business.operations.web.request.*;
import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.business.task.repository.*;
import cn.superhuang.data.scalpel.business.compute.domain.*;
import cn.superhuang.data.scalpel.business.compute.repository.ComputeEngineRepository;
import cn.superhuang.data.scalpel.business.system.access.repository.*;
import cn.superhuang.data.scalpel.contract.quality.*;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "data-scalpel.operations.background-enabled=false",
        "data-scalpel.operations.credential-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "data-scalpel.operations.public-base-url=https://platform.example.internal"
})
@Transactional
class OperationsIntegrationTests {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class StorageConfiguration {
        @org.springframework.context.annotation.Bean
        cn.superhuang.data.scalpel.business.task.service.TaskRunArtifactStorage operationsArtifactStorage() {
            return new cn.superhuang.data.scalpel.business.task.service.TaskRunArtifactStorage() {
                public void store(String key, byte[] content, String type) { throw new AssertionError("Unexpected artifact write"); }
                public void delete(String key) { throw new AssertionError("Unexpected artifact deletion"); }
                public java.net.URI presignGet(String key, java.time.Duration lifetime) { throw new AssertionError("Unexpected artifact access"); }
                public java.net.URI presignPut(String key, String type, java.time.Duration lifetime) { throw new AssertionError("Unexpected artifact access"); }
                public Optional<byte[]> readIfPresent(String key, int maximumBytes) { return Optional.empty(); }
            };
        }
    }
    @Autowired AlertRuleService ruleService;
    @Autowired AlertRuleRepository rules;
    @Autowired TaskRunAlertService capture;
    @Autowired AlertSignalRepository signals;
    @Autowired AlertIncidentRepository incidents;
    @Autowired AlertActionRepository actions;
    @Autowired AlertDeliveryRepository deliveries;
    @Autowired InAppNotificationRepository notifications;
    @Autowired AlertEvaluationService evaluation;
    @Autowired AlertIncidentService lifecycle;
    @Autowired AlertDeliveryService deliveryService;
    @Autowired AlertChannelService channelService;
    @Autowired AlertChannelRepository channels;
    @Autowired AlertWebhookSender webhookSender;
    @Autowired AlertSilenceRepository silences;
    @Autowired RuntimeWorkbenchService workbench;
    @Autowired InAppNotificationService inbox;
    @Autowired AlertRecipientService recipients;
    @Autowired DataTaskRepository tasks;
    @Autowired TaskRunRepository runs;
    @Autowired ComputeEngineRepository engines;
    @Autowired EngineObservationRepository observations;
    @Autowired EngineObservationService observationService;
    @Autowired SystemUserRepository users;
    @Autowired SystemRolePermissionRepository rolePermissions;
    @Autowired SystemPermissionRepository permissions;
    @Autowired EntityManager em;
    @Autowired tools.jackson.databind.ObjectMapper json;
    private UUID admin;

    @BeforeEach void authenticate() {
        admin = users.findByUsername("admin").orElseThrow().getId();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", "unused", List.of()));
    }
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void workbenchUsesRealCountsProjectionAndCurrentScope() {
        var task = task();
        var old = TaskRun.queue(task.getId(), 1, "{\"sql\":\"not returned in global list\"}");
        ReflectionTestUtils.setField(old, "queuedAt", Instant.now().minusSeconds(90000));
        runs.saveAndFlush(old);
        var success = runs.save(TaskRun.queue(task.getId(), 1, "{}")); success.start(); success.succeed(3);
        var simulated = TaskRun.scheduledSuccess(task.getId(), UUID.randomUUID(), 1, "{}", Instant.now());
        runs.saveAndFlush(simulated);
        var overview = workbench.overview(null, null);
        assertThat(overview.tasks().current().get(TaskRunStatus.QUEUED)).isEqualTo(1);
        assertThat(overview.tasks().completed().get(TaskRunStatus.SUCCESS)).isEqualTo(1);
        assertThat(overview.tasks().successRate()).isEqualTo(1.0);
        assertThat(overview.tasks().trend()).hasSizeBetween(24, 25);
        assertThat(overview.tasks().trend().stream().mapToLong(p -> p.count()).sum()).isEqualTo(1);
        assertThat(overview.tasks().trend().getFirst().from()).isEqualTo(overview.from());
        assertThat(overview.tasks().trend().getLast().to()).isEqualTo(overview.to());
        var list = workbench.runs(new SearchRequest(null, 0, 20, "-queuedAt"), new RuntimeRunFilter(null, null, true, null, null, null, null, false));
        assertThat(list.content()).extracting(r -> r.id()).containsExactly(old.getId());
        assertThat(recipients.search(AlertRuleType.RUN_FAILED, "admin", 0, 100).content()).extracting(r -> r.id()).contains(admin);
        assertThat(workbench.streaming(SearchRequest.empty()).totalElements()).isZero();
        assertThat(workbench.engines(SearchRequest.empty()).totalElements()).isZero();
    }

    @Test void terminalFactsAreDeduplicatedAndLaterSuccessDoesNotCloseFailure() {
        configure(AlertRuleType.RUN_FAILED, 0, 0, List.of());
        var task = task(); var failed = failed(task);
        capture.capture(failed);
        assertThat(signals.count()).isEqualTo(1);
        processSignals(); processSignals();
        assertThat(incidents.count()).isEqualTo(1); assertThat(notifications.count()).isEqualTo(1);
        var success = runs.save(TaskRun.queue(task.getId(), 1, "{}")); success.start(); success.succeed(1); capture.capture(success);
        var i = incidents.findAll().getFirst();
        assertThat(i.getStatus()).isEqualTo(AlertHandlingStatus.OPEN);
        lifecycle.acknowledge(i.getId(), new AlertAcknowledgementRequest("正在补数"));
        assertThat(inbox.unread().unreadCount()).isEqualTo(1);
        inbox.read(notifications.findAll().getFirst().getId());
        assertThat(incidents.findById(i.getId()).orElseThrow().getStatus()).isEqualTo(AlertHandlingStatus.ACKNOWLEDGED);
        lifecycle.close(i.getId(), new CloseAlertRequest("已核对并补回缺失数据"));
        assertThat(incidents.findById(i.getId()).orElseThrow().getStatus()).isEqualTo(AlertHandlingStatus.CLOSED);
        assertThat(notifications.findAll()).allMatch(n -> n.getEventType() == AlertEventType.TRIGGERED);
    }

    @Test void qualityFailureIsSeparateFromExecutionFailureAndTrialIsExcluded() {
        configure(AlertRuleType.QUALITY_FAILED, 0, 0, List.of());
        var task = task(); var quality = TaskRun.queue(task.getId(), 1, "{}"); quality.useTaskType(TaskType.SPARK_MODEL_QUALITY);
        runs.save(quality); quality.externalSucceed(null, Instant.now(), Instant.now(), new QualitySummary(QualityConclusion.FAILED, 3, 2, 1, 0, 100)); capture.capture(quality);
        var trial = TaskRun.queueDispatchedCanvasTrial(UUID.randomUUID(), task.getId(), 1, "{}", UUID.randomUUID(), 1, Instant.now().plusSeconds(60), UUID.randomUUID(), "commands.test");
        runs.save(trial); trial.fail("trial", ""); capture.capture(trial); processSignals();
        assertThat(incidents.findAll()).singleElement().satisfies(i -> assertThat(i.getRuleType()).isEqualTo(AlertRuleType.QUALITY_FAILED));
        var overview = workbench.overview(null, null);
        assertThat(overview.tasks().qualityFailed()).isEqualTo(1); assertThat(overview.tasks().successRate()).isEqualTo(1.0);
    }

    @Test void disabledOverridePreventsFallbackAndRulesDoNotReplayTerminalHistory() {
        var task = task();
        ruleService.save(null, new SaveAlertRuleRequest(AlertRuleType.RUN_FAILED, task.getId(), false, AlertSeverity.CRITICAL, 0, 0, List.of(admin), List.of()));
        var run = failed(task); assertThat(signals.count()).isZero();
        var override = rules.findByScopeKey(AlertRuleService.key(AlertRuleType.RUN_FAILED, task.getId())).orElseThrow();
        ruleService.setEnabled(override.getId(), true); capture.capture(run);
        assertThat(signals.count()).isZero();
        ruleService.resetOverride(override.getId());
        failed(task); processSignals(); assertThat(incidents.count()).isEqualTo(1);
    }

    @Test void queueEndingInFailureNeverProducesRecoveredNotification() {
        configure(AlertRuleType.QUEUE_TOO_LONG, 1, 0, List.of());
        var run = TaskRun.queue(task().getId(), 1, "{}");
        ReflectionTestUtils.setField(run, "queuedAt", Instant.now().minusSeconds(120));
        runs.saveAndFlush(run); evaluation.run(run.getId());
        var i = incidents.findAll().getFirst();
        assertThatThrownBy(() -> lifecycle.close(i.getId(), new CloseAlertRequest("忽略"))).isInstanceOf(ResponseStatusException.class);
        run.fail("failed", "do not forward SQL secret"); capture.capture(run); evaluation.run(run.getId()); processSignals();
        assertThat(incidents.findById(i.getId()).orElseThrow().getCloseReason()).contains("FAILED");
        assertThat(notifications.findAll()).noneMatch(n -> n.getEventType() == AlertEventType.RECOVERED);
        assertThat(incidents.findAll()).anyMatch(n -> n.getRuleType() == AlertRuleType.RUN_FAILED);
    }

    @Test void engineNeedsSustainedFailureAndTwoHealthySamplesAndUnknownNeverRecovers() {
        configure(AlertRuleType.ENGINE_UNREACHABLE, 90, 0, List.of());
        var e = engine(); var o = new EngineObservation(); o.setEngineId(e.getId());
        o.setState(EngineObservationState.UNREACHABLE); o.setObservedAt(Instant.now()); o.setUnreachableSince(Instant.now().minusSeconds(30));
        observations.saveAndFlush(o); evaluation.engine(e.getId()); assertThat(incidents.count()).isZero();
        o.setUnreachableSince(Instant.now().minusSeconds(100)); evaluation.engine(e.getId()); assertThat(incidents.count()).isEqualTo(1);
        var i = incidents.findAll().getFirst();
        o.setObservedAt(Instant.now().minusSeconds(300)); evaluation.engine(e.getId());
        assertThat(i.getConditionState()).isEqualTo(AlertConditionState.UNKNOWN); assertThat(i.getStatus()).isEqualTo(AlertHandlingStatus.OPEN);
        o.setObservedAt(Instant.now()); o.setState(EngineObservationState.REACHABLE); o.setDependenciesReady(true); o.setReachableSamples(1);
        evaluation.engine(e.getId()); assertThat(i.getStatus()).isEqualTo(AlertHandlingStatus.OPEN);
        o.setReachableSamples(2); evaluation.engine(e.getId()); assertThat(i.getStatus()).isEqualTo(AlertHandlingStatus.CLOSED);
        assertThat(notifications.findAll()).anyMatch(n -> n.getEventType() == AlertEventType.RECOVERED);
    }

    @Test void cooldownKeepsEveryIncidentWhileSilenceStopsNotifications() {
        configure(AlertRuleType.RUN_FAILED, 0, 600, List.of());
        var task = task(); failed(task); processSignals();
        var first = incidents.findAll().getFirst();
        failed(task); processSignals();
        assertThat(incidents.count()).isEqualTo(2); assertThat(notifications.count()).isEqualTo(1);
        lifecycle.silence(first.getId(), new SilenceAlertRequest(Instant.now().plusSeconds(1800), "维护中"));
        failed(task); processSignals(); assertThat(incidents.count()).isEqualTo(3); assertThat(notifications.count()).isEqualTo(1);
    }

    @Test void engineConfigurationChangeResetsDebounceEvidence() {
        var e = engine(); var old = Instant.now().minusSeconds(300);
        var o = new EngineObservation(); o.setEngineId(e.getId()); o.setEngineConfigurationAt(Instant.EPOCH);
        o.setObservedAt(Instant.now()); o.setState(EngineObservationState.UNREACHABLE); o.setUnreachableSince(old);
        observations.saveAndFlush(o);
        // The synthetic engine credential is invalid, so no external Dispatcher is contacted.
        observationService.observe(e.getId());
        assertThat(o.getUnreachableSince()).isAfter(old.plusSeconds(200));
        evaluation.engine(e.getId()); assertThat(incidents.count()).isZero();
    }

    @Test void channelRequestsAllowOmittedCredentialFlagsAndPreserveExistingSecrets() {
        var create = json.readValue("""
                {"name":"browser channel","url":"http://127.0.0.1:1/hooks","enabled":true}
                """, SaveAlertChannelRequest.class);
        var channel = channelService.save(null, create);
        assertThat(channel.bearerTokenConfigured()).isFalse();
        assertThat(channel.hmacSecretConfigured()).isFalse();
        channelService.save(channel.id(), new SaveAlertChannelRequest(channel.name(), channel.url(), true,
                "saved bearer", "saved hmac", false, false));
        var update = json.readValue("""
                {"name":"renamed channel","url":"http://127.0.0.1:1/hooks","enabled":true}
                """, SaveAlertChannelRequest.class);
        var saved = channelService.save(channel.id(), update);
        assertThat(saved.bearerTokenConfigured()).isTrue();
        assertThat(saved.hmacSecretConfigured()).isTrue();
        var clear = json.readValue("""
                {"name":"renamed channel","url":"http://127.0.0.1:1/hooks","enabled":true,
                 "clearBearerToken":true,"clearHmacSecret":true}
                """, SaveAlertChannelRequest.class);
        var cleared = channelService.save(channel.id(), clear);
        assertThat(cleared.bearerTokenConfigured()).isFalse();
        assertThat(cleared.hmacSecretConfigured()).isFalse();
    }

    @Test void deliveryLeasesRetriesAndConfigurationVersionAreEnforced() {
        var channel = channelService.save(null, new SaveAlertChannelRequest("test", "http://127.0.0.1:1/hooks", true, "secret", null, false, false));
        assertThat(channel.bearerTokenConfigured()).isTrue();
        configure(AlertRuleType.RUN_FAILED, 0, 0, List.of(channel.id())); failed(task()); processSignals();
        var d = deliveries.findAll().getFirst(); String payload = d.getPayloadJson();
        assertThat(payload).contains(d.getId().toString()).contains("https://platform.example.internal/operations/alerts").doesNotContain("secret", "password", "definitionSnapshot");
        var attempt = deliveryService.claim(d.getId(), d.getRuleType()); assertThat(attempt).isNotNull();
        deliveryService.complete(d.getId(), UUID.randomUUID(), 200, 1, false, null, null); assertThat(d.getStatus()).isEqualTo(AlertDeliveryStatus.SENDING);
        deliveryService.complete(d.getId(), attempt.token(), 503, 1, true, null, "Webhook 返回 HTTP 503"); assertThat(d.getStatus()).isEqualTo(AlertDeliveryStatus.PENDING);
        d.setNextAttemptAt(Instant.now().minusSeconds(1)); var second = deliveryService.claim(d.getId(), d.getRuleType());
        assertThat(second.id()).isEqualTo(attempt.id()); assertThat(second.token()).isNotEqualTo(attempt.token());
        deliveryService.complete(d.getId(), attempt.token(), 200, 1, false, null, null); assertThat(d.getStatus()).isEqualTo(AlertDeliveryStatus.SENDING);
        deliveryService.complete(d.getId(), second.token(), 400, 1, false, null, "HTTP 400"); assertThat(d.getStatus()).isEqualTo(AlertDeliveryStatus.FAILED);
        channelService.save(channel.id(), new SaveAlertChannelRequest("test", "http://127.0.0.1:2/hooks", true, null, null, false, false));
        assertThatThrownBy(() -> deliveryService.retry(d.getId())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("配置已变化");
        d.setStatus(AlertDeliveryStatus.PENDING); d.setNextAttemptAt(Instant.now().minusSeconds(1));
        assertThat(deliveryService.claim(d.getId(), d.getRuleType())).isNull(); assertThat(d.getStatus()).isEqualTo(AlertDeliveryStatus.SUPPRESSED);
    }

    @Test void revokedSourcePermissionRemovesCountsAndPersonalNotifications() {
        configure(AlertRuleType.RUN_FAILED, 0, 0, List.of()); failed(task()); processSignals();
        UUID permission = permissions.findByCode("task.view").orElseThrow().getId();
        UUID role = users.findById(admin).orElseThrow().getRoleId();
        rolePermissions.findAllByRoleId(role).stream().filter(p -> p.getPermissionId().equals(permission)).forEach(rolePermissions::delete);
        em.flush();
        assertThat(workbench.overview(null, null).tasks()).isNull();
        assertThat(inbox.unread().unreadCount()).isZero();
        assertThat(lifecycle.search(SearchRequest.empty()).totalElements()).isZero();
    }

    @Test void expiredSilenceRemindsOncePerObjectAndOrdersReminderBeforeRecovery() {
        var channel = channelService.save(null, new SaveAlertChannelRequest("ordering", "http://127.0.0.1:1/hooks", true, null, null, false, false));
        configure(AlertRuleType.QUEUE_TOO_LONG, 1, 0, List.of(channel.id()));
        var task = task();
        var first = TaskRun.queue(task.getId(), 1, "{}"); var second = TaskRun.queue(task.getId(), 1, "{}");
        for (var run : List.of(first, second)) { ReflectionTestUtils.setField(run, "queuedAt", Instant.now().minusSeconds(120)); runs.saveAndFlush(run); evaluation.run(run.getId()); }
        var incident = incidents.findAll().stream().filter(i -> first.getId().equals(i.getRunId())).findFirst().orElseThrow();
        lifecycle.silence(incident.getId(), new SilenceAlertRequest(Instant.now().plusSeconds(60), "维护"));
        var silence = silences.findByScopeKey(AlertRuleService.key(AlertRuleType.QUEUE_TOO_LONG, task.getId())).orElseThrow();
        silence.setUntilAt(Instant.now());
        evaluation.run(first.getId()); evaluation.run(second.getId()); evaluation.run(first.getId());
        assertThat(notifications.count()).isEqualTo(3);
        assertThat(silence.getReminderSentAt()).isNotNull();
        assertThat(deliveries.findAllByIncidentIdAndChannelIdOrderBySequenceAsc(incident.getId(), channel.id())).extracting(d -> d.getSequence()).containsExactly(1, 2);
        first.start(); evaluation.run(first.getId());
        var ordered = deliveries.findAllByIncidentIdAndChannelIdOrderBySequenceAsc(incident.getId(), channel.id());
        assertThat(ordered).extracting(d -> d.getSequence()).containsExactly(1, 2, 3);
        assertThat(deliveryService.claim(ordered.getLast().getId(), AlertRuleType.QUEUE_TOO_LONG)).isNull();
        assertThat(ordered.getLast().getStatus()).isEqualTo(AlertDeliveryStatus.PENDING);
        assertThat(lifecycle.get(incident.getId()).notifications().inApp()).isEqualTo(3);
    }

    @Test void ruleDisableEndsContinuousConditionWithoutRecoveryAndSuppressesQueuedDelivery() {
        var channel = channelService.save(null, new SaveAlertChannelRequest("disable", "http://127.0.0.1:1/hooks", true, null, null, false, false));
        configure(AlertRuleType.QUEUE_TOO_LONG, 1, 0, List.of(channel.id()));
        var run = TaskRun.queue(task().getId(), 1, "{}"); ReflectionTestUtils.setField(run, "queuedAt", Instant.now().minusSeconds(120));
        runs.saveAndFlush(run); evaluation.run(run.getId());
        ruleService.setEnabled(rules.findByScopeKey(AlertRuleService.key(AlertRuleType.QUEUE_TOO_LONG, null)).orElseThrow().getId(), false);
        evaluation.run(run.getId());
        assertThat(incidents.findAll()).allMatch(i -> i.getStatus() == AlertHandlingStatus.CLOSED);
        var d = deliveries.findAll().getFirst(); assertThat(deliveryService.claim(d.getId(), d.getRuleType())).isNull();
        assertThat(d.getStatus()).isEqualTo(AlertDeliveryStatus.SUPPRESSED);
        assertThat(notifications.findAll()).noneMatch(n -> n.getEventType() == AlertEventType.RECOVERED);
    }

    @Test void webhookPostsSignedPayloadAndRetriesWithStableDeliveryIdentity() throws Exception {
        var requests = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var timestamps = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var signatures = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var identities = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var bearer = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            timestamps.add(exchange.getRequestHeaders().getFirst("X-DataScalpel-Timestamp"));
            signatures.add(exchange.getRequestHeaders().getFirst("X-DataScalpel-Signature"));
            identities.add(exchange.getRequestHeaders().getFirst("X-DataScalpel-Delivery-Id"));
            bearer.add(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(requests.size() == 1 ? 503 : 204, -1); exchange.close();
        });
        server.start();
        try {
            var channel = channelService.save(null, new SaveAlertChannelRequest("loopback", "http://127.0.0.1:" + server.getAddress().getPort() + "/hook", true, "test-bearer", "test-hmac", false, false));
            configure(AlertRuleType.RUN_FAILED, 0, 0, List.of(channel.id())); failed(task()); processSignals();
            var d = deliveries.findAll().getFirst();
            webhookSender.send(deliveryService.claim(d.getId(), d.getRuleType()));
            assertThat(d.getStatus()).isEqualTo(AlertDeliveryStatus.PENDING);
            assertThat(d.getHttpStatus()).isEqualTo(503);
            d.setNextAttemptAt(Instant.now()); webhookSender.send(deliveryService.claim(d.getId(), d.getRuleType()));
            assertThat(d.getStatus()).isEqualTo(AlertDeliveryStatus.SENT);
            assertThat(requests).containsExactly(d.getPayloadJson(), d.getPayloadJson());
            assertThat(identities).containsExactly(d.getId().toString(), d.getId().toString());
            assertThat(bearer).containsExactly("Bearer test-bearer", "Bearer test-bearer");
            for (int n = 0; n < requests.size(); n++) {
                var mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec("test-hmac".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
                assertThat(signatures.get(n)).isEqualTo("sha256=" + HexFormat.of().formatHex(mac.doFinal((timestamps.get(n) + "." + requests.get(n)).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            }
        } finally { server.stop(0); }
    }

    @Test void webhookTimeoutIsRetryableAndRedirectIsNeverFollowed() throws Exception {
        var redirected = new java.util.concurrent.atomic.AtomicBoolean();
        var release = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newCachedThreadPool();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0); server.setExecutor(pool);
        server.createContext("/slow", exchange -> {
            try { release.await(10, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.createContext("/redirect", exchange -> { exchange.getResponseHeaders().set("Location", "/target"); exchange.sendResponseHeaders(302, -1); exchange.close(); });
        server.createContext("/target", exchange -> { redirected.set(true); exchange.sendResponseHeaders(204, -1); exchange.close(); }); server.start();
        try {
            for (String path : List.of("/slow", "/redirect")) {
                var c = channelService.save(null, new SaveAlertChannelRequest(path, "http://127.0.0.1:" + server.getAddress().getPort() + path, true, null, null, false, false));
                configure(AlertRuleType.RUN_FAILED, 0, 0, List.of(c.id())); failed(task()); processSignals();
                var d = deliveries.findAll().stream().filter(row -> row.getChannelId().equals(c.id())).findFirst().orElseThrow();
                webhookSender.send(deliveryService.claim(d.getId(), d.getRuleType()));
                assertThat(d.getStatus()).isEqualTo(path.equals("/slow") ? AlertDeliveryStatus.PENDING : AlertDeliveryStatus.FAILED);
            }
            assertThat(redirected).isFalse();
        } finally { release.countDown(); server.stop(0); pool.shutdownNow(); }
    }

    private DataTask task() { return tasks.saveAndFlush(DataTask.create("ops-test-" + UUID.randomUUID(), null, TaskType.LOCAL_SQL, null)); }
    private TaskRun failed(DataTask task) {
        var run = runs.saveAndFlush(TaskRun.queue(task.getId(), 1, "{}")); run.fail("SQL token=do-not-forward", "secret"); capture.capture(run); em.flush(); return run;
    }
    private ComputeEngine engine() {
        var e = ComputeEngine.create("ops-engine-" + UUID.randomUUID(), null, "http://127.0.0.1:1", "unused", ComputeBackendType.LOCAL_DOCKER,
                "commands." + UUID.randomUUID(), "runner." + UUID.randomUUID(), "admin.events", 10, 2, 3, null);
        e.activate("dispatcher-test", ComputeBackendType.LOCAL_DOCKER); return engines.saveAndFlush(e);
    }
    private void configure(AlertRuleType type, int threshold, int cooldown, List<UUID> channelIds) {
        var rule = rules.findByScopeKey(AlertRuleService.key(type, null)).orElseThrow();
        ruleService.save(rule.getId(), new SaveAlertRuleRequest(type, null, true, AlertSeverity.CRITICAL, threshold, cooldown, List.of(admin), channelIds));
    }
    private void processSignals() { signals.findAll().stream().filter(s -> s.getStatus() == AlertSignalStatus.PENDING).forEach(s -> evaluation.signal(s.getId(), s.getRuleType())); em.flush(); }
}

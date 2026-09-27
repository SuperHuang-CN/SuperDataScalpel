package cn.superhuang.data.scalpel.business.compute.service;

import cn.superhuang.data.scalpel.business.compute.client.ComputeEngineDispatcherClient;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngine;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngineRegistrationState;
import cn.superhuang.data.scalpel.business.compute.repository.ComputeEngineRepository;
import cn.superhuang.data.scalpel.business.compute.web.request.DiscoverComputeTargetsRequest;
import cn.superhuang.data.scalpel.business.compute.web.request.RegisterComputeTargetsRequest;
import cn.superhuang.data.scalpel.business.compute.web.response.ComputeEngineResponse;
import cn.superhuang.data.scalpel.business.compute.web.response.ComputeTargetDiscoveryResponse;
import cn.superhuang.data.scalpel.business.compute.web.response.RegisterComputeTargetsResponse;
import cn.superhuang.data.scalpel.business.task.execution.service.ExecutionKafkaProperties;
import cn.superhuang.data.scalpel.contract.execution.DispatcherTargetDirectoryResponse;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.util.*;

@Service
public class ComputeEngineDiscoveryService {
    private final ComputeEngineRepository repository;
    private final ComputeEngineDispatcherClient client;
    private final ComputeEngineManagementService management;
    private final ComputeEngineCredentialCipher cipher;
    private final SparkExecutionResourceConfigurationService resources;
    private final ExecutionKafkaProperties kafka;
    private final TransactionTemplate transaction;

    public ComputeEngineDiscoveryService(ComputeEngineRepository repository, ComputeEngineDispatcherClient client,
            ComputeEngineManagementService management, ComputeEngineCredentialCipher cipher,
            SparkExecutionResourceConfigurationService resources, ExecutionKafkaProperties kafka,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.client = client;
        this.management = management;
        this.cipher = cipher;
        this.resources = resources;
        this.kafka = kafka;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public ComputeTargetDiscoveryResponse discover(DiscoverComputeTargetsRequest request) {
        var directory = readDirectory(request.dispatcherBaseUrl(), request.accessToken());
        return new ComputeTargetDiscoveryResponse(directory.dispatcherInstanceId(), directory.controlPlaneVersion(),
                directory.messaging(),
                directory.targets().stream().map(target -> new ComputeTargetDiscoveryResponse.Target(target,
                        repository.findByTargetDispatcherInstanceIdAndTargetKey(directory.dispatcherInstanceId(), target.targetKey())
                                .map(this::response).orElse(null))).toList());
    }

    public RegisterComputeTargetsResponse register(RegisterComputeTargetsRequest request) {
        String url = normalizeUrl(request.dispatcherBaseUrl());
        var directory = readDirectory(url, request.accessToken());
        if (!directory.dispatcherInstanceId().equals(request.dispatcherInstanceId())) {
            throw conflict("Dispatcher 实例已变化，请重新连接并选择目标");
        }
        if (request.targets().stream().map(RegisterComputeTargetsRequest.Target::targetKey).distinct().count() != request.targets().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能重复选择同一执行目标");
        }
        List<RegisterComputeTargetsResponse.Item> results = new ArrayList<>();
        for (var selection : request.targets()) {
            ComputeEngineResponse saved = null;
            try {
                var target = directory.targets().stream().filter(t -> t.targetKey().equals(selection.targetKey())).findFirst()
                        .orElseThrow(() -> conflict("目标已被禁用或移除，请重新发现"));
                if (!target.targetFingerprint().equals(selection.targetFingerprint())) throw conflict("目标环境已变化，请重新发现");
                if (!target.ready()) throw conflict("目标尚未就绪，请检查目标状态后重试");
                var existing = repository.findByTargetDispatcherInstanceIdAndTargetKey(directory.dispatcherInstanceId(), target.targetKey());
                if (target.registeredEngineId() != null && (existing.isEmpty()
                        || !target.registeredEngineId().equals(existing.get().getId()))) {
                    throw conflict("此目标已被其他引擎占用，请先处理原注册关系");
                }
                ComputeEngine engine;
                try {
                    engine = existing.orElseGet(() -> Objects.requireNonNull(transaction.execute(status -> {
                        var concurrent = repository.findByTargetDispatcherInstanceIdAndTargetKey(directory.dispatcherInstanceId(), target.targetKey());
                        if (concurrent.isPresent()) return concurrent.get();
                        if (repository.findByNameIgnoreCase(selection.name().trim()).isPresent()) throw conflict("引擎名称已存在，请修改名称");
                        String suffix = UUID.randomUUID().toString();
                        var policy = target.resourcePolicy();
                        if (policy == null) throw conflict("Dispatcher 未提供目标资源策略，请升级 Dispatcher");
                        var created = ComputeEngine.create(selection.name(), null, url, cipher.encrypt(request.accessToken()),
                                ComputeBackendType.valueOf(target.backendType().name()),
                                directory.messaging() == null ? "datascalpel.execution.command." + suffix : directory.messaging().commandTopic(),
                                directory.messaging() == null ? "datascalpel.runner.event." + suffix : directory.messaging().runnerEventTopic(),
                                directory.messaging() == null ? kafka.adminEventTopics().getFirst() : directory.messaging().adminEventTopic(),
                                selection.maxQueuedExecutions(), selection.maxConcurrentSubmissions(), selection.maxInFlightApplications(), resources.write(policy));
                        created.bindTarget(directory.dispatcherInstanceId(), target.targetKey(), target.targetFingerprint());
                        return repository.saveAndFlush(created);
                    })));
                } catch (DataIntegrityViolationException concurrentCreate) {
                    engine = repository.findByTargetDispatcherInstanceIdAndTargetKey(directory.dispatcherInstanceId(), target.targetKey())
                            .orElseThrow(() -> conflict("名称或目标已被占用，请刷新后重试"));
                }
                saved = response(engine);
                if (directory.messaging() != null && (!engine.getCommandTopic().equals(directory.messaging().commandTopic())
                        || !engine.getRunnerEventTopic().equals(directory.messaging().runnerEventTopic())
                        || !engine.getAdminEventTopic().equals(directory.messaging().adminEventTopic()))) {
                    throw conflict("已有引擎仍使用旧消息通道，请先完成维护窗口迁移，不能通过重复注册直接切换");
                }
                if (!Objects.equals(engine.getTargetFingerprint(), target.targetFingerprint())) throw conflict("已有引擎绑定的物理环境与当前目标不一致");
                if (engine.getRegistrationState() == ComputeEngineRegistrationState.ACTIVE) {
                    if (!engine.getId().equals(target.registeredEngineId()) || !"ACTIVE".equals(target.registrationState())) {
                        throw conflict("本地和远端注册状态不一致，请到引擎详情处理");
                    }
                } else {
                    saved = management.register(engine.getId());
                }
                results.add(new RegisterComputeTargetsResponse.Item(target.targetKey(), true, saved, null));
            } catch (RuntimeException failure) {
                if (saved != null) saved = management.get(saved.id());
                String message = failure instanceof ResponseStatusException response && response.getStatusCode().is4xxClientError()
                        ? response.getReason() : "注册失败；已创建的引擎记录保留，请查看错误状态并重试";
                results.add(new RegisterComputeTargetsResponse.Item(selection.targetKey(), false, saved, message));
            }
        }
        return new RegisterComputeTargetsResponse(List.copyOf(results));
    }

    private DispatcherTargetDirectoryResponse readDirectory(String baseUrl, String token) {
        String url = normalizeUrl(baseUrl);
        try {
            var directory = client.targets(url, token);
            if (directory == null || (directory.controlPlaneVersion() != 2 && directory.controlPlaneVersion() != 3) || directory.dispatcherInstanceId() == null
                    || directory.dispatcherInstanceId().isBlank() || directory.targets() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Dispatcher 目标发现协议不兼容，请升级 Dispatcher");
            }
            if (directory.controlPlaneVersion() == 3) {
                var channels = directory.messaging();
                if (channels == null || channels.commandTopic() == null || channels.runnerEventTopic() == null
                        || channels.adminEventTopic() == null
                        || !(channels.runnerEventTopic() + ".control").equals(channels.runnerControlTopic())) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Dispatcher 实例消息通道发现结果不完整");
                }
                if (!kafka.listensTo(channels.adminEventTopic())) {
                    throw conflict("Dispatcher 的 Admin 事件 Topic 与本平台监听配置不一致，请调整部署配置");
                }
                var inputs = Set.of(channels.commandTopic(), channels.runnerEventTopic(), channels.runnerControlTopic());
                for (var other : repository.findAll()) {
                    if (directory.dispatcherInstanceId().equals(other.getTargetDispatcherInstanceId())) continue;
                    if (inputs.contains(other.getCommandTopic()) || inputs.contains(other.getRunnerEventTopic())
                            || inputs.contains(other.getRunnerEventTopic() + ".control") || inputs.contains(other.getAdminEventTopic())
                            || channels.adminEventTopic().equals(other.getCommandTopic())
                            || channels.adminEventTopic().equals(other.getRunnerEventTopic())) {
                        throw conflict("消息通道已被其他 Dispatcher 使用，请为此实例配置独立通道");
                    }
                }
            }
            return directory;
        } catch (RestClientResponseException failure) {
            String message = switch (failure.getStatusCode().value()) {
                case 401, 403 -> "Dispatcher 认证失败，请检查访问 Token";
                case 404 -> "此 Dispatcher 不支持目标发现，请升级 Dispatcher";
                default -> "Dispatcher 目标发现失败，请检查服务状态";
            };
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message, failure);
        } catch (ResponseStatusException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法连接 Dispatcher，请检查地址和网络", failure);
        }
    }

    private ComputeEngineResponse response(ComputeEngine engine) {
        return ComputeEngineResponse.from(engine, resources.policy(engine.getResourcePolicyJson(), engine.getExpectedBackendType()));
    }

    private static String normalizeUrl(String value) {
        try {
            var uri = URI.create(value.trim());
            if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) throw new IllegalArgumentException();
            return value.trim().replaceFirst("/+$", "");
        } catch (RuntimeException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dispatcher 地址必须是无凭据、查询串或片段的 HTTP/HTTPS 地址");
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}

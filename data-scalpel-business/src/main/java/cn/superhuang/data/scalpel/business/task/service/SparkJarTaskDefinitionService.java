package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.service.DataSourceRuntimeService;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngine;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineSelectionService;
import cn.superhuang.data.scalpel.business.compute.service.SparkExecutionResourceConfigurationService;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.business.task.repository.*;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateSparkJarTaskDefinitionRequest;
import cn.superhuang.data.scalpel.business.task.web.response.SparkJarTaskDefinitionResponse;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceType;
import cn.superhuang.data.scalpel.contract.execution.SparkJarJobMode;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SparkJarTaskDefinitionService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SparkJarTaskDefinitionService.class);
    public static final int JOB_API_VERSION = 1;
    public static final long MAX_JAR_BYTES = 100L * 1024 * 1024;
    private static final Set<String> LEGACY_RESOURCE_KEYS = Set.of(
            "spark.driver.cores", "spark.driver.memory", "spark.executor.instances",
            "spark.executor.cores", "spark.executor.memory");
    private static final String DRIVER_JVM_OPTIONS_KEY = "spark.driver.extrajavaoptions";
    private static final Pattern DRIVER_JVM_OPTION = Pattern.compile(
            "(?:-D[A-Za-z_][A-Za-z0-9_.-]*=[^\\s\"']+|-XX:[+-][A-Za-z][A-Za-z0-9_.]*"
                    + "|-XX:[A-Za-z][A-Za-z0-9_.]*=[^\\s\"']+|--add-(?:opens|exports)=[^\\s\"']+)");

    private final DataTaskRepository taskRepository;
    private final SparkJarTaskDefinitionRepository definitionRepository;
    private final SparkJarTaskResourceBindingRepository bindingRepository;
    private final DataModelRepository modelRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DataSourceRuntimeService dataSourceRuntimeService;
    private final ComputeEngineSelectionService computeEngineSelectionService;
    private final SparkExecutionResourceConfigurationService resourceConfigurationService;
    private final TaskRunArtifactStorage storage;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public SparkJarTaskDefinitionService(
            DataTaskRepository taskRepository,
            SparkJarTaskDefinitionRepository definitionRepository,
            SparkJarTaskResourceBindingRepository bindingRepository,
            DataModelRepository modelRepository,
            DataSourceRepository dataSourceRepository,
            DataSourceRuntimeService dataSourceRuntimeService,
            ComputeEngineSelectionService computeEngineSelectionService,
            SparkExecutionResourceConfigurationService resourceConfigurationService,
            TaskRunArtifactStorage storage,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.taskRepository = taskRepository;
        this.definitionRepository = definitionRepository;
        this.bindingRepository = bindingRepository;
        this.modelRepository = modelRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.dataSourceRuntimeService = dataSourceRuntimeService;
        this.computeEngineSelectionService = computeEngineSelectionService;
        this.resourceConfigurationService = resourceConfigurationService;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public SparkJarTaskDefinitionResponse get(UUID taskId) {
        DataTask task = requireJarTask(taskId);
        return definitionRepository.findByTaskId(taskId)
                .map(this::response)
                .orElseGet(() -> new SparkJarTaskDefinitionResponse(
                        taskId, false, 0, expectedMode(task), null,
                        List.of(), List.of(), List.of(), defaultResources(task), 3600, null));
    }

    @Transactional(readOnly = true)
    public SparkExecutionResourceSpec resolvedExecutionResources(UUID taskId, SparkJarTaskDefinition definition) {
        DataTask task = requireJarTask(taskId);
        return resolvedResources(task, definition, null,
                extractLegacyResources(parseEntries(definition.getSparkConfJson())).resources());
    }

    @Transactional
    public SparkJarTaskDefinitionResponse update(UUID taskId, UpdateSparkJarTaskDefinitionRequest request) {
        DataTask task = requireJarTaskForUpdate(taskId);
        requireEditable(task);
        List<UpdateSparkJarTaskDefinitionRequest.Entry> parameters = normalizedEntries(request.parameters(), false);
        LegacyResources legacyResources = extractLegacyResources(request.sparkConf());
        if (request.executionResources() != null && legacyResources.resources() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "运行资源不能同时通过 Spark Conf 与“运行资源”配置，请移除 Spark Conf 中的资源参数");
        }
        List<UpdateSparkJarTaskDefinitionRequest.Entry> sparkConf = normalizedEntries(legacyResources.sparkConf(), true);
        List<UpdateSparkJarTaskDefinitionRequest.ResourceBinding> bindings = normalizedBindings(request.resourceBindings());
        validateBindings(task, bindings, false);

        SparkJarTaskDefinition definition = definitionRepository.findByTaskId(taskId)
                .orElseGet(() -> SparkJarTaskDefinition.create(taskId, expectedMode(task)));
        SparkExecutionResourceSpec resources = resolvedResources(task, definition, request.executionResources(), legacyResources.resources());
        boolean changed = definition.updateConfiguration(
                json(parameters), json(sparkConf), resourceConfigurationService.write(resources), request.timeoutSeconds());
        List<SparkJarTaskResourceBinding> existing = bindingRepository.findAllByTaskIdOrderByCreatedAtAsc(taskId);
        if (!sameBindings(existing, bindings)) {
            bindingRepository.deleteAllByTaskId(taskId);
            bindingRepository.flush();
            bindingRepository.saveAll(bindings.stream().map(binding -> SparkJarTaskResourceBinding.create(
                    taskId, binding.bindingName(), binding.resourceType(), binding.resourceId(),
                    binding.topicName(), binding.accessMode()
            )).toList());
            if (!changed) definition.resourceBindingsChanged();
        }
        return response(definitionRepository.saveAndFlush(definition));
    }

    public SparkJarTaskDefinitionResponse upload(UUID taskId, MultipartFile file) {
        byte[] content = readJar(file);
        JarMetadata metadata = inspectJar(content);
        String fileName = safeFileName(file.getOriginalFilename());
        String sha256 = sha256(content);
        String objectKey = "tasks/%s/spark-jar/current/%s.jar".formatted(taskId, sha256);
        boolean objectAlreadyReferenced = definitionRepository.existsByJarObjectKey(objectKey);
        storage.store(objectKey, content, "application/java-archive");

        UploadResult result;
        try {
            result = Objects.requireNonNull(transactionTemplate.execute(status -> {
                DataTask task = requireJarTaskForUpdate(taskId);
                requireEditable(task);
                SparkJarJobMode expectedMode = expectedMode(task);
                if (metadata.jobMode() != expectedMode) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "JAR Job Mode 与任务类型不匹配，当前任务要求 " + expectedMode);
                }
                SparkJarTaskDefinition definition = definitionRepository.findByTaskId(taskId)
                        .orElseGet(() -> SparkJarTaskDefinition.create(taskId, expectedMode));
                String previousKey = definition.getJarObjectKey();
                definition.updateJar(objectKey, fileName, sha256, content.length,
                        metadata.jobClass(), metadata.apiVersion(), metadata.jobMode());
                SparkJarTaskDefinition saved = definitionRepository.saveAndFlush(definition);
                return new UploadResult(previousKey, response(saved));
            }));
        } catch (RuntimeException exception) {
            // The current object key is content-addressed. A rejected upload of the
            // same JAR must never delete the object still referenced by the saved definition.
            if (!objectAlreadyReferenced && !definitionRepository.existsByJarObjectKey(objectKey)) {
                try {
                    storage.delete(objectKey);
                } catch (RuntimeException cleanupException) {
                    exception.addSuppressed(cleanupException);
                    log.warn("Rejected Spark JAR upload cleanup failed: taskId={}", taskId, cleanupException);
                }
            }
            throw exception;
        }
        if (result.previousKey() != null && !result.previousKey().equals(objectKey)) {
            try {
                storage.delete(result.previousKey());
            } catch (RuntimeException cleanupException) {
                // The definition transaction already committed. Keep the successful upload
                // visible to the caller and leave the now-unreferenced object for operations cleanup.
                log.warn("Replaced Spark JAR cleanup failed: taskId={}", taskId, cleanupException);
            }
        }
        return result.response();
    }

    public void validatePublishable(UUID taskId) {
        DataTask task = requireJarTask(taskId);
        SparkJarTaskDefinition definition = definitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "请先配置 Spark JAR 任务定义"));
        if (!definition.hasJar() || !JOB_API_VERSION_EQUALS(definition.getJobApiVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先上传受支持的 Spark JAR");
        }
        if (definition.getJobMode() != expectedMode(task)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Spark JAR Job Mode 与任务类型不匹配，请重新上传");
        }
        // Publishing is another explicit boundary: a definition saved before an
        // engine policy was lowered must not become runnable accidentally.
        resolvedResources(task, definition, null,
                extractLegacyResources(parseEntries(definition.getSparkConfJson())).resources());
        validateBindings(task, bindingRequests(taskId), true);
    }

    /** Validates an existing JAR definition before its task is moved to another engine. */
    public void validateExecutionResourcesForEngine(SparkJarTaskDefinition definition, UUID computeEngineId) {
        ComputeEngine engine = computeEngineSelectionService.requireExisting(computeEngineId);
        SparkExecutionResourcePolicy policy = resourceConfigurationService.policy(
                engine.getResourcePolicyJson(), engine.getExpectedBackendType());
        SparkExecutionResourceSpec configured = resourceConfigurationService.resources(
                definition.getExecutionResourcesJson());
        if (configured != null && configured.exceeds(policy.maximums())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "任务运行资源超过计算引擎“" + engine.getName() + "”的单次任务上限");
        }
    }

    public void validateArtifactAvailable(UUID taskId) {
        ArtifactValidation snapshot = transactionTemplate.execute(status -> {
            SparkJarTaskDefinition definition = definitionRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "请先配置 Spark JAR 任务定义"));
            if (!definition.hasJar()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "请先上传 Spark JAR");
            }
            return new ArtifactValidation(definition.getJarObjectKey(), definition.getJarSha256(),
                    definition.getJarSizeBytes());
        });
        if (snapshot == null) {
            throw new IllegalStateException("无法读取 Spark JAR 制品快照");
        }
        byte[] content;
        try {
            content = storage.readIfPresent(snapshot.objectKey(), (int) MAX_JAR_BYTES)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "当前用户 JAR 制品不存在，请重新上传"));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "任务制品存储当前不可用", exception);
        }
        if (content.length != snapshot.sizeBytes() || !sha256(content).equals(snapshot.sha256())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "当前用户 JAR 制品摘要不一致，请重新上传");
        }
    }

    @Transactional(readOnly = true)
    public SparkJarTaskDefinition requireConfigured(UUID taskId) {
        SparkJarTaskDefinition definition = definitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "请先配置 Spark JAR 任务定义"));
        if (!definition.hasJar()) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先上传 Spark JAR");
        return definition;
    }

    public byte[] template(UUID taskId) {
        DataTask task = requireJarTask(taskId);
        SparkJarJobMode mode = expectedMode(task);
        boolean streaming = mode == SparkJarJobMode.STREAMING;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
            add(zip, "datascalpel-spark-job/pom.xml", templatePom(mode));
            add(zip, "datascalpel-spark-job/README.md", templateReadme(mode));
            add(zip, "datascalpel-spark-job/.gitignore", "target/\n.idea/\n*.iml\n");
            add(zip, "datascalpel-spark-job/src/main/java/com/example/datascalpel/"
                            + (streaming ? "ExampleSparkStreamingJob.java" : "ExampleSparkJob.java"),
                    streaming ? templateStreamingJob() : templateBatchJob());
            add(zip, "datascalpel-spark-job/src/test/java/com/example/datascalpel/"
                            + (streaming ? "ExampleSparkStreamingJobTest.java" : "ExampleSparkJobTest.java"),
                    streaming ? templateStreamingJobTest() : templateBatchJobTest());
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("无法生成 Spark JAR Maven 模板", exception);
        }
    }

    public void deleteObjectAfterCommit(UUID taskId, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return;
        Runnable delete = () -> {
            try {
                storage.delete(objectKey);
            } catch (RuntimeException exception) {
                log.warn("Spark JAR current artifact cleanup failed: taskId={}", taskId, exception);
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { delete.run(); }
            });
        } else {
            delete.run();
        }
    }

    private SparkJarTaskDefinitionResponse response(SparkJarTaskDefinition definition) {
        DataTask task = requireJarTask(definition.getTaskId());
        List<UpdateSparkJarTaskDefinitionRequest.Entry> parameters = parseEntries(definition.getParametersJson());
        LegacyResources legacyResources = extractLegacyResources(parseEntries(definition.getSparkConfJson()));
        List<UpdateSparkJarTaskDefinitionRequest.Entry> sparkConf = legacyResources.sparkConf();
        List<SparkJarTaskResourceBinding> bindings = bindingRepository
                .findAllByTaskIdOrderByCreatedAtAsc(definition.getTaskId());
        Map<UUID, String> modelNames = new HashMap<>();
        modelRepository.findAllById(bindings.stream().filter(binding -> binding.getResourceType() == SparkJarResourceType.MODEL)
                .map(SparkJarTaskResourceBinding::getResourceId).toList()).forEach(model -> modelNames.put(model.getId(), model.getName()));
        Map<UUID, String> sourceNames = new HashMap<>();
        dataSourceRepository.findAllById(bindings.stream().filter(binding -> binding.getResourceType() != SparkJarResourceType.MODEL)
                .map(SparkJarTaskResourceBinding::getResourceId).toList()).forEach(source -> sourceNames.put(source.getId(), source.getName()));
        SparkJarTaskDefinitionResponse.Jar jar = definition.hasJar() ? new SparkJarTaskDefinitionResponse.Jar(
                definition.getJarFileName(), definition.getJarSha256(), definition.getJarSizeBytes(),
                definition.getJobClass(), definition.getJobApiVersion(), definition.getJobMode()) : null;
        return new SparkJarTaskDefinitionResponse(
                definition.getTaskId(), definition.hasJar(), definition.getVersion(), definition.getJobMode(), jar,
                parameters.stream().map(entry -> new SparkJarTaskDefinitionResponse.Entry(entry.name(), entry.value())).toList(),
                sparkConf.stream().map(entry -> new SparkJarTaskDefinitionResponse.Entry(entry.name(), entry.value())).toList(),
                bindings.stream().map(binding -> new SparkJarTaskDefinitionResponse.ResourceBinding(
                        binding.getBindingName(), binding.getResourceType(), binding.getResourceId(),
                        binding.getResourceType() == SparkJarResourceType.MODEL
                                ? modelNames.get(binding.getResourceId()) : sourceNames.get(binding.getResourceId()),
                        binding.getTopicName(), binding.getAccessMode())).toList(),
                resolvedResources(task, definition, null, legacyResources.resources()),
                definition.getTimeoutSeconds(), definition.getUpdatedAt());
    }

    private List<UpdateSparkJarTaskDefinitionRequest.Entry> normalizedEntries(
            List<UpdateSparkJarTaskDefinitionRequest.Entry> values, boolean conf) {
        List<UpdateSparkJarTaskDefinitionRequest.Entry> normalized = values.stream()
                .map(entry -> new UpdateSparkJarTaskDefinitionRequest.Entry(entry.name().trim(), entry.value()))
                .toList();
        requireUnique(normalized.stream().map(UpdateSparkJarTaskDefinitionRequest.Entry::name).toList(),
                conf ? "Spark Conf Key 不能重复" : "参数 Key 不能重复");
        if (conf && normalized.stream().filter(entry -> DRIVER_JVM_OPTIONS_KEY.equals(
                entry.name().trim().toLowerCase(Locale.ROOT))).count() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "spark.driver.extraJavaOptions 不能重复配置");
        }
        if (conf) normalized.forEach(entry -> {
            validateSparkConf(entry.name(), entry.value());
            if (entry.value().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Spark Conf Value 不能为空");
            }
        });
        else normalized.forEach(entry -> {
            if (entry.name().length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数 Key 最长 100 个字符");
            if (containsControlCharacter(entry.name())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数 Key 包含不支持的字符");
            }
        });
        if (conf) normalized.forEach(entry -> {
            if (entry.value().length() > 2000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Spark Conf Value 最长 2000 个字符");
            }
        });
        return normalized;
    }

    private SparkExecutionResourceSpec defaultResources(DataTask task) {
        ComputeEngine engine = computeEngineSelectionService.requireExisting(task.getComputeEngineId());
        return resourceConfigurationService.policy(engine.getResourcePolicyJson(), engine.getExpectedBackendType()).defaults();
    }

    private SparkExecutionResourceSpec resolvedResources(
            DataTask task,
            SparkJarTaskDefinition definition,
            SparkExecutionResourceSpec requested,
            SparkExecutionResourceSpec legacy
    ) {
        ComputeEngine engine = computeEngineSelectionService.requireExisting(task.getComputeEngineId());
        SparkExecutionResourcePolicy policy = resourceConfigurationService.policy(
                engine.getResourcePolicyJson(), engine.getExpectedBackendType());
        SparkExecutionResourceSpec current = resourceConfigurationService.resources(definition.getExecutionResourcesJson());
        SparkExecutionResourceSpec result = requested != null ? requested
                : legacy != null ? legacy
                : current != null ? current : policy.defaults();
        if (result.exceeds(policy.maximums())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "任务运行资源超过计算引擎“" + engine.getName() + "”的单次任务上限");
        }
        return result;
    }

    private static LegacyResources extractLegacyResources(List<UpdateSparkJarTaskDefinitionRequest.Entry> entries) {
        if (entries == null || entries.isEmpty()) return new LegacyResources(List.of(), null);
        Map<String, String> legacy = new LinkedHashMap<>();
        List<UpdateSparkJarTaskDefinitionRequest.Entry> remaining = new ArrayList<>();
        for (UpdateSparkJarTaskDefinitionRequest.Entry entry : entries) {
            String key = entry.name() == null ? "" : entry.name().trim().toLowerCase(Locale.ROOT);
            if (LEGACY_RESOURCE_KEYS.contains(key)) legacy.put(key, entry.value());
            else remaining.add(entry);
        }
        if (legacy.isEmpty()) return new LegacyResources(remaining, null);
        try {
            int driverCores = integer(legacy.getOrDefault("spark.driver.cores", "1"), "spark.driver.cores");
            int driverMemory = memoryMiB(legacy.getOrDefault("spark.driver.memory", "2048m"), "spark.driver.memory");
            int executorInstances = integer(legacy.getOrDefault("spark.executor.instances", "2"), "spark.executor.instances");
            int executorCores = integer(legacy.getOrDefault("spark.executor.cores", "2"), "spark.executor.cores");
            int executorMemory = memoryMiB(legacy.getOrDefault("spark.executor.memory", "2048m"), "spark.executor.memory");
            return new LegacyResources(remaining,
                    new SparkExecutionResourceSpec(driverCores, driverMemory, executorInstances,
                            executorCores, executorMemory));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "旧 Spark Conf 中的运行资源格式无效，请改用运行资源配置", exception);
        }
    }

    private static int integer(String value, String key) {
        if (value == null || !value.trim().matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(key + " 必须是正整数");
        }
        return Integer.parseInt(value.trim());
    }

    private static int memoryMiB(String value, String key) {
        if (value == null || !value.trim().matches("[1-9][0-9]*[mMgG]")) {
            throw new IllegalArgumentException(key + " 必须使用 MiB 或 GiB 单位");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        long amount = Long.parseLong(normalized.substring(0, normalized.length() - 1));
        long mib = normalized.endsWith("g") ? amount * 1024 : amount;
        if (mib > Integer.MAX_VALUE) throw new IllegalArgumentException(key + " 过大");
        return (int) mib;
    }

    private List<UpdateSparkJarTaskDefinitionRequest.ResourceBinding> normalizedBindings(
            List<UpdateSparkJarTaskDefinitionRequest.ResourceBinding> values) {
        List<UpdateSparkJarTaskDefinitionRequest.ResourceBinding> normalized = values.stream()
                .map(binding -> new UpdateSparkJarTaskDefinitionRequest.ResourceBinding(
                        binding.bindingName().trim(), binding.resourceType(), binding.resourceId(),
                        normalizeTopicName(binding.resourceType(), binding.topicName()), binding.accessMode()))
                .toList();
        requireUnique(normalized.stream().map(UpdateSparkJarTaskDefinitionRequest.ResourceBinding::bindingName).toList(),
                "同一任务内资源绑定名不能重复");
        if (normalized.stream().anyMatch(binding -> containsControlCharacter(binding.bindingName()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "资源绑定名包含不支持的字符");
        }
        return normalized;
    }

    private void validateBindings(DataTask task,
                                  List<UpdateSparkJarTaskDefinitionRequest.ResourceBinding> bindings,
                                  boolean publish) {
        if (task.getType() == TaskType.SPARK_JAR
                && bindings.stream().anyMatch(binding -> binding.resourceType() == SparkJarResourceType.KAFKA_TOPIC)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批处理 Spark JAR 不支持 Kafka Topic 绑定");
        }
        Map<UUID, Set<String>> kafkaTopics = new LinkedHashMap<>();
        for (var binding : bindings) {
            if (binding.resourceType() == SparkJarResourceType.MODEL) {
                DataModel model = modelRepository.findById(binding.resourceId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "绑定的模型不存在：" + binding.bindingName()));
                if (model.getStatus() != DataModelStatus.PUBLISHED) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "绑定的模型未发布：" + binding.bindingName());
                }
                DataSource source = dataSourceRepository.findById(model.getStorageDataSourceId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "模型底层数据源不存在：" + binding.bindingName()));
                if (!source.isEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "模型底层数据源已停用：" + binding.bindingName());
                if (binding.accessMode().canRead() && !source.getPurposes().contains(DataSourcePurpose.SOURCE)
                        && !source.getPurposes().contains(DataSourcePurpose.STORAGE)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "模型读取绑定的数据源用途不正确：" + binding.bindingName());
                }
                if (binding.accessMode().canWrite() && !source.getPurposes().contains(DataSourcePurpose.STORAGE)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "模型写入要求底层数据源具有 STORAGE 用途：" + binding.bindingName());
                }
            } else if (binding.resourceType() == SparkJarResourceType.JDBC_DATA_SOURCE) {
                DataSource source = dataSourceRepository.findById(binding.resourceId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "绑定的数据源不存在：" + binding.bindingName()));
                if (!source.getType().isJdbc()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "资源绑定只支持 JDBC 数据源");
                if (!source.isEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "绑定的数据源已停用：" + binding.bindingName());
                if (binding.accessMode().canRead() && !source.getPurposes().contains(DataSourcePurpose.SOURCE)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "JDBC 读取要求 SOURCE 用途：" + binding.bindingName());
                }
                if (binding.accessMode().canWrite() && !source.getPurposes().contains(DataSourcePurpose.DISTRIBUTION)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "JDBC 写入要求 DISTRIBUTION 用途：" + binding.bindingName());
                }
            } else {
                DataSource source = dataSourceRepository.findById(binding.resourceId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "绑定的 Kafka 数据源不存在：" + binding.bindingName()));
                if (source.getType() != cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType.KAFKA) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Kafka Topic 绑定必须选择 Kafka 数据源：" + binding.bindingName());
                }
                if (!source.isEnabled()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "绑定的 Kafka 数据源已停用：" + binding.bindingName());
                }
                if (binding.accessMode().canRead() && !source.getPurposes().contains(DataSourcePurpose.SOURCE)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Kafka Topic 读取要求 SOURCE 用途：" + binding.bindingName());
                }
                if (binding.accessMode().canWrite() && !source.getPurposes().contains(DataSourcePurpose.DISTRIBUTION)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Kafka Topic 写入要求 DISTRIBUTION 用途：" + binding.bindingName());
                }
                kafkaTopics.computeIfAbsent(binding.resourceId(), ignored -> new LinkedHashSet<>())
                        .add(binding.topicName());
            }
        }
        if (publish) kafkaTopics.forEach(dataSourceRuntimeService::requireKafkaTopics);
    }

    private static void validateSparkConf(String key, String configuredValue) {
        String value = key.toLowerCase(Locale.ROOT);
        if (!value.startsWith("spark.")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Spark Conf Key 必须以 spark. 开头");
        if (containsControlCharacter(key) || key.indexOf('=') >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Spark Conf Key 包含不支持的字符");
        }
        if (DRIVER_JVM_OPTIONS_KEY.equals(value)) {
            validateDriverJvmOptions(configuredValue);
            return;
        }
        List<String> forbidden = List.of("spark.master", "spark.submit.deploymode", "spark.app.name", "spark.jars",
                "spark.files", "spark.archives", "spark.jars.packages", "extraclasspath", "javaoptions", ".env.",
                "spark.hadoop.", "spark.kubernetes.", "spark.yarn.queue", "spark.yarn.tags",
                "spark.yarn.submit.waitappcompletion", "spark.driver.host", "spark.driver.bindaddress",
                "spark.driver.port", "spark.blockmanager.port", "spark.network.", "spark.rpc.",
                "spark.authenticate", "spark.ssl", "datascalpel", "spark.driver.memoryoverhead",
                "spark.executor.memoryoverhead", "spark.dynamicallocation.");
        if (forbidden.stream().anyMatch(value::startsWith) || forbidden.stream().anyMatch(value::contains)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该 Spark 配置由平台控制：" + key);
        }
    }

    private static void validateDriverJvmOptions(String value) {
        if (value == null || value.isBlank() || value.length() > 2_000 || containsControlCharacter(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Driver JVM 参数不能为空、不能包含控制字符且总长度不能超过 2000");
        }
        String[] options = value.split(" ", -1);
        if (options.length > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver JVM 参数最多 100 项");
        }
        for (String option : options) {
            String normalized = option.toLowerCase(Locale.ROOT);
            if (option.isBlank() || option.chars().anyMatch(Character::isWhitespace)
                    || option.indexOf('\"') >= 0 || option.indexOf('\'') >= 0
                    || normalized.startsWith("-ddatascalpel.")
                    || normalized.startsWith("-djava.class.path")
                    || normalized.contains("heapdump")
                    || normalized.startsWith("-xx:onerror")
                    || normalized.startsWith("-xx:onoutofmemoryerror")
                    || normalized.startsWith("-xx:errorfile")
                    || !DRIVER_JVM_OPTION.matcher(option).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Driver JVM 参数只允许 -D、-XX、--add-opens 或 --add-exports；不能覆盖内存、Agent、classpath 或错误转储配置");
            }
        }
    }

    private List<UpdateSparkJarTaskDefinitionRequest.ResourceBinding> bindingRequests(UUID taskId) {
        return bindingRepository.findAllByTaskIdOrderByCreatedAtAsc(taskId).stream().map(binding ->
                new UpdateSparkJarTaskDefinitionRequest.ResourceBinding(binding.getBindingName(),
                        binding.getResourceType(), binding.getResourceId(), binding.getTopicName(),
                        binding.getAccessMode())).toList();
    }

    private static boolean sameBindings(List<SparkJarTaskResourceBinding> existing,
                                        List<UpdateSparkJarTaskDefinitionRequest.ResourceBinding> requested) {
        if (existing.size() != requested.size()) return false;
        for (int index = 0; index < existing.size(); index++) {
            var left = existing.get(index); var right = requested.get(index);
            if (!left.getBindingName().equals(right.bindingName()) || left.getResourceType() != right.resourceType()
                    || !left.getResourceId().equals(right.resourceId()) || left.getAccessMode() != right.accessMode()) return false;
            if (!Objects.equals(left.getTopicName(), right.topicName())) return false;
        }
        return true;
    }

    private DataTask requireJarTask(UUID taskId) {
        DataTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!task.getType().isJar()) throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不是 Spark JAR 任务");
        return task;
    }

    private DataTask requireJarTaskForUpdate(UUID taskId) {
        DataTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!task.getType().isJar()) throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不是 Spark JAR 任务");
        return task;
    }

    private static void requireEditable(DataTask task) {
        if (task.getStatus() != TaskStatus.DRAFT && task.getStatus() != TaskStatus.DISABLED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布任务不能修改 Spark JAR 定义，请先停用");
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private List<UpdateSparkJarTaskDefinitionRequest.Entry> parseEntries(String json) {
        return objectMapper.readValue(json, new TypeReference<List<UpdateSparkJarTaskDefinitionRequest.Entry>>() {});
    }

    private static void requireUnique(List<String> values, String message) {
        if (new HashSet<>(values).size() != values.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static boolean containsControlCharacter(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\0') >= 0;
    }

    private static byte[] readJar(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_JAR_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JAR 文件不能为空且不能超过 100 MiB");
        try { return file.getBytes(); }
        catch (IOException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取上传的 JAR", exception); }
    }

    private static JarMetadata inspectJar(byte[] bytes) {
        try (JarInputStream jar = new JarInputStream(new ByteArrayInputStream(bytes))) {
            if (jar.getManifest() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JAR 缺少 Manifest");
            Attributes attributes = jar.getManifest().getMainAttributes();
            String api = attributes.getValue("DataScalpel-Job-Api-Version");
            String jobClass = attributes.getValue("DataScalpel-Job-Class");
            String modeValue = attributes.getValue("DataScalpel-Job-Mode");
            SparkJarJobMode jobMode;
            try {
                jobMode = modeValue == null || modeValue.isBlank()
                        ? SparkJarJobMode.BATCH
                        : SparkJarJobMode.valueOf(modeValue.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DataScalpel-Job-Mode 无效");
            }
            int version;
            try { version = Integer.parseInt(api); }
            catch (RuntimeException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Job API Version 无效"); }
            if (version != JOB_API_VERSION) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的 Job API Version：" + version);
            if (jobClass == null || !jobClass.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DataScalpel-Job-Class 无效");
            String entryName = jobClass.replace('.', '/') + ".class";
            boolean found = false;
            for (var entry = jar.getNextJarEntry(); entry != null; entry = jar.getNextJarEntry()) {
                if (!entry.isDirectory() && entryName.equals(entry.getName())) { found = true; break; }
            }
            if (!found) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JAR 中不存在 Job Class 对应的 class 文件");
            return new JarMetadata(version, jobClass, jobMode);
        } catch (ResponseStatusException exception) { throw exception; }
        catch (IOException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件不是合法 JAR", exception); }
    }

    private static String safeFileName(String value) {
        String name = value == null ? "user-job.jar" : value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || name.length() > 255 || !name.toLowerCase(Locale.ROOT).endsWith(".jar"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件名必须以 .jar 结尾");
        return name;
    }

    private static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception exception) { throw new IllegalStateException("无法计算 JAR 摘要", exception); }
    }

    private static void add(ZipOutputStream zip, String path, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(path)); zip.write(value.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }

    static String templatePom(SparkJarJobMode mode) {
        String jobClass = mode == SparkJarJobMode.STREAMING
                ? "com.example.datascalpel.ExampleSparkStreamingJob"
                : "com.example.datascalpel.ExampleSparkJob";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.datascalpel</groupId><artifactId>spark-job</artifactId><version>1.0.0</version>
                  <properties><maven.compiler.release>21</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
                  <dependencies>
                    <dependency><groupId>cn.superhuang</groupId><artifactId>data-scalpel-task-sdk</artifactId><version>0.1.0-SNAPSHOT</version><scope>provided</scope></dependency>
                    <dependency><groupId>org.apache.spark</groupId><artifactId>spark-sql_2.13</artifactId><version>4.1.1</version><scope>provided</scope></dependency>
                    <dependency><groupId>cn.superhuang</groupId><artifactId>data-scalpel-task-sdk-testkit</artifactId><version>0.1.0-SNAPSHOT</version><scope>test</scope></dependency>
                    <dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>5.0.0</version><scope>test</scope></dependency>
                    <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><version>6.0.3</version><scope>test</scope></dependency>
                  </dependencies>
                  <build><plugins>
                    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-jar-plugin</artifactId><version>3.4.2</version>
                      <configuration><archive><manifestEntries><DataScalpel-Job-Api-Version>1</DataScalpel-Job-Api-Version><DataScalpel-Job-Mode>%s</DataScalpel-Job-Mode><DataScalpel-Job-Class>%s</DataScalpel-Job-Class></manifestEntries></archive></configuration>
                    </plugin>
                    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-shade-plugin</artifactId><version>3.6.1</version>
                      <executions><execution><phase>package</phase><goals><goal>shade</goal></goals><configuration>
                        <createDependencyReducedPom>false</createDependencyReducedPom>
                        <artifactSet><excludes>
                          <exclude>cn.superhuang:data-scalpel-task-sdk</exclude>
                          <exclude>cn.superhuang:data-scalpel-task-sdk-testkit</exclude>
                          <exclude>org.apache.spark:*</exclude><exclude>org.scala-lang:*</exclude><exclude>org.apache.hadoop:*</exclude>
                        </excludes></artifactSet>
                      </configuration></execution></executions>
                    </plugin>
                    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>3.5.4</version>
                      <configuration><argLine>
                        -XX:+IgnoreUnrecognizedVMOptions --add-modules=jdk.incubator.vector
                        --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED
                        --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED
                        --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED
                        --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED
                        --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED
                        --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/sun.nio.cs=ALL-UNNAMED
                        --add-opens=java.base/sun.security.action=ALL-UNNAMED --add-opens=java.base/sun.util.calendar=ALL-UNNAMED
                        --add-opens=java.security.jgss/sun.security.krb5=ALL-UNNAMED
                        -Djdk.reflect.useDirectMethodHandle=false -Dio.netty.tryReflectionSetAccessible=true
                        --enable-native-access=ALL-UNNAMED
                      </argLine></configuration>
                    </plugin>
                  </plugins></build>
                </project>
                """.formatted(mode, jobClass);
    }

    static String templateReadme(SparkJarJobMode mode) {
        if (mode == SparkJarJobMode.STREAMING) return templateStreamingReadme();
        return """
                # DataScalpel Spark Job

                ## 构建与上传

                使用 Java 21 开发并实现 `SparkBatchJob`：

                ```bash
                mvn test
                mvn package
                ```

                将 `target/spark-job-1.0.0.jar` 上传到 Spark JAR任务定义页。Manifest中的
                `DataScalpel-Job-Api-Version` 和 `DataScalpel-Job-Class` 必须与实现保持一致。

                ## 依赖

                `data-scalpel-task-sdk`、Spark、Scala、Hadoop都是 `provided`，不得打入用户 JAR。
                Task Engine Uber JAR不是用户工程依赖。其他业务依赖由 Shade Plugin打入用户 JAR；
                平台不承诺 Task Engine其他内部依赖的版本或兼容性。
                `data-scalpel-task-sdk-testkit`只用于本地测试，不会进入最终用户 JAR。

                ## 资源与字段

                先在任务定义中声明模型或 JDBC资源绑定，再通过大小写敏感的绑定名访问。
                `READ/WRITE/READ_WRITE` 会在每次 SDK调用时校验。字段映射顺序固定为
                `map(目标字段, 来源字段)`；模型 UPSERT自动使用完整模型主键，JDBC UPSERT在代码中指定 Key。

                ## JDBC 读取与分片

                任务定义中的 `Spark Conf` 用于全局 Spark 参数。单次模型或 JDBC 表读取的分片、Fetch Size、
                超时和下推参数由用户 JAR 中的 `JdbcReadOptions` 指定：

                ```java
                var options = JdbcReadOptions.builder()
                        .partitionBy("id", "1", "10000000", 16)
                        .fetchSize(10_000)
                        .option("pushDownPredicate", "true")
                        .build();
                Dataset<Row> input = context.models().read("source_model", options);

                Dataset<Row> summary = context.jdbc().readQuery(
                        "erp_source",
                        "SELECT customer_id, SUM(amount) AS total_amount "
                                + "FROM sales.orders GROUP BY customer_id",
                        options);
                ```

                `lowerBound/upperBound` 仅用于计算 JDBC 分片步长，不作为数据过滤条件；
                `Dataset.repartition()` 只会在数据读入 Spark 后重新分区，不能替代 JDBC 源端分片。
                `readQuery` 仍只允许单条 `SELECT/WITH`，SQL 在绑定的数据源中执行；启用分片时，分片列必须出现在
                查询结果中。平台使用带别名的子查询实现 SQL 上推，因此可以与 `partitionBy` 同时使用。

                ## 运行限制

                平台负责 SparkSession生命周期；作业不得调用 `spark.stop()`、创建新的根 SparkSession或
                调用 `System.exit()`。SDK写操作立即执行，多次写入之间没有跨目标事务；原生 Spark Writer
                的写入不计入平台影响行数。批任务不提供Checkpoint或 Kafka SDK；S3和 HTTP SDK暂不提供。

                ## 可观测能力

                通过 `context.observability()`记录结构化事件、当前阶段、Counter、Gauge和Operation Timer。
                最新阶段与指标显示在运行详情中，事件写入 `console.log`。指标只属于当前Attempt；不要在消息或
                属性中记录SQL、数据内容、密码、Token或其他敏感信息。观测对象只供Driver端代码使用，不要捕获到
                Spark Executor闭包。
                """;
    }

    private static String templateBatchJob() {
        return """
                package com.example.datascalpel;

                import cn.superhuang.datascalpel.sdk.SparkBatchJob;
                import cn.superhuang.datascalpel.sdk.SparkJobContext;
                import cn.superhuang.datascalpel.sdk.JdbcReadOptions;
                import org.apache.spark.sql.Dataset;
                import org.apache.spark.sql.Row;

                public final class ExampleSparkJob implements SparkBatchJob {
                    public ExampleSparkJob() {}

                    @Override
                    public void execute(SparkJobContext context) {
                        context.observability().status("READ_INPUT", "正在读取订单模型");
                        context.observability().info("orders.started", "开始处理订单");
                        // 如需 JDBC 源端分片，可改用：
                        // var options = JdbcReadOptions.builder()
                        //         .partitionBy("order_id", "1", "10000000", 16)
                        //         .fetchSize(10_000)
                        //         .build();
                        // Dataset<Row> input = context.models().read("source_model", options);
                        // SQL 上推也支持同一组读取参数；分片列必须出现在查询结果中：
                        // Dataset<Row> summary = context.jdbc().readQuery(
                        //         "erp_source", "SELECT customer_id, SUM(amount) total FROM sales.orders GROUP BY customer_id", options);
                        Dataset<Row> input = context.models().read("source_model");
                        context.observability().status("WRITE_OUTPUT", "正在写入目标模型");
                        try (var operation = context.observability().operation("orders.model_write")) {
                            Long rows = context.models().write("target_model", input)
                                    .map("order_id", "order_id")
                                    .execute()
                                    .affectedRows();
                            if (rows != null) {
                                context.observability().addCounter("orders.written_rows", rows);
                            }
                        }
                    }
                }
                """;
    }

    private static String templateBatchJobTest() {
        return """
                package com.example.datascalpel;

                import cn.superhuang.datascalpel.sdk.testkit.CapturedModelWrite;
                import cn.superhuang.datascalpel.sdk.testkit.SparkJobTestContext;
                import cn.superhuang.datascalpel.sdk.testkit.TestModelTarget;
                import org.apache.spark.sql.Row;
                import org.apache.spark.sql.RowFactory;
                import org.apache.spark.sql.types.DataTypes;
                import org.apache.spark.sql.types.StructType;
                import org.junit.jupiter.api.Test;

                import java.util.List;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                class ExampleSparkJobTest {
                    @Test
                    void writesMappedRowsToTheBoundModel() throws Exception {
                        StructType schema = new StructType()
                                .add("order_id", DataTypes.StringType, false);
                        List<Row> rows = List.of(RowFactory.create("order-1"));

                        try (SparkJobTestContext context = SparkJobTestContext.builder()
                                .modelInput("source_model", schema, rows)
                                .modelOutput("target_model", TestModelTarget.builder(schema).build())
                                .build()) {
                            new ExampleSparkJob().execute(context);

                            CapturedModelWrite write = context.modelWrites("target_model").getFirst();
                            assertEquals(1, write.affectedRows());
                            assertEquals("order-1", write.rows().getFirst().getString(0));
                            context.assertCounter("orders.written_rows", 1);
                            context.assertCounter("datascalpel.model.write.successes", 1);
                            context.assertTimerRecorded("orders.model_write");
                        }
                    }
                }
                """;
    }

    private static String templateStreamingReadme() {
        return """
                # DataScalpel Spark Streaming Job

                使用 Java 21 实现 `SparkStreamingJob`。先执行 `mvn test`运行无需Docker的本地TestKit测试，
                再执行 `mvn package`并上传生成的 JAR。
                Manifest 必须声明 `DataScalpel-Job-Mode: STREAMING`。SDK、Spark、Scala和 Hadoop
                都是 `provided`；其他业务依赖由 Shade Plugin 打入用户 JAR。

                先在任务定义中配置 Kafka Topic、模型或 JDBC 资源绑定，再通过大小写敏感的绑定名访问。
                Kafka 输入返回 Spark Kafka Connector 原始列。所有 StreamingQuery 必须通过
                `context.queries().start(...)` 注册，必须使用平台提供的 Query Name 和 Checkpoint；
                `start()` 完成注册后应立即返回，不得调用 `awaitTermination()`。

                用户代码自行设置 Trigger、Output Mode 和处理逻辑。模型/JDBC 实时写入应放在 foreachBatch
                中，实时模式只允许 APPEND/UPSERT。平台负责 SparkSession、查询停止和 Checkpoint 生命周期，
                作业不得调用 `spark.stop()`、创建新的根 SparkSession或调用 `System.exit()`。

                TestKit使用本地文件流模拟Kafka、捕获模型/JDBC写入并校验全部StreamingQuery注册；
                它不模拟真实Kafka认证、数据库方言、约束、事务或网络故障。

                `context.observability()`可记录结构化事件、阶段、Counter、Gauge和Operation Timer。实时指标只属于
                当前Application/Attempt，不写入Checkpoint；CONTINUE恢复后重新从0累计。事件进入 `console.log`，
                不要记录SQL、Kafka消息内容、凭据或其他敏感信息。观测对象只供Driver端代码使用，不要捕获到
                Spark Executor闭包。
                """;
    }

    private static String templateStreamingJob() {
        return """
                package com.example.datascalpel;

                import cn.superhuang.datascalpel.sdk.KafkaStartingOffsets;
                import cn.superhuang.datascalpel.sdk.ModelWriteMode;
                import cn.superhuang.datascalpel.sdk.SparkStreamingJob;
                import cn.superhuang.datascalpel.sdk.SparkStreamingJobContext;
                import cn.superhuang.datascalpel.sdk.StreamingSinkType;
                import org.apache.spark.sql.Dataset;
                import org.apache.spark.sql.Row;
                import org.apache.spark.sql.streaming.Trigger;

                public final class ExampleSparkStreamingJob implements SparkStreamingJob {
                    public ExampleSparkStreamingJob() {}

                    @Override
                    public void start(SparkStreamingJobContext context) throws Exception {
                        context.observability().status("REGISTER_QUERIES", "正在注册实时查询");
                        context.observability().info("orders.streaming_started", "开始注册订单实时作业");
                        Dataset<Row> input = context.kafka().readStream(
                                "source_topic", KafkaStartingOffsets.LATEST);
                        Dataset<Row> output = input.selectExpr("key", "value");
                        String triggerInterval = context.parameters().find("trigger_interval")
                                .orElse("10 seconds");
                        context.queries().start("kafka-output", StreamingSinkType.KAFKA, spec ->
                                context.kafka().writeStream("target_topic", output)
                                        .queryName(spec.queryName())
                                        .option("checkpointLocation", spec.checkpointLocation())
                                        .trigger(Trigger.ProcessingTime(triggerInterval))
                                        .start());

                        Dataset<Row> modelRows = input.selectExpr(
                                "CAST(value AS STRING) AS order_id");
                        context.queries().start("model-upsert", StreamingSinkType.JDBC, spec ->
                                modelRows.writeStream()
                                        .queryName(spec.queryName())
                                        .option("checkpointLocation", spec.checkpointLocation())
                                        .foreachBatch((Dataset<Row> batch, Long batchId) -> {
                                            context.observability().status("PROCESS_BATCH", "正在处理订单微批");
                                            try (var operation = context.observability().operation("orders.model_upsert")) {
                                                Long rows = context.models().write("target_model", batch)
                                                        .mode(ModelWriteMode.UPSERT)
                                                        .map("order_id", "order_id")
                                                        .execute()
                                                        .affectedRows();
                                                if (rows != null) {
                                                    context.observability().addCounter("orders.written_rows", rows);
                                                }
                                            }
                                        })
                                        .trigger(Trigger.ProcessingTime(triggerInterval))
                                        .start());
                    }
                }
                """;
    }

    private static String templateStreamingJobTest() {
        return """
                package com.example.datascalpel;

                import cn.superhuang.datascalpel.sdk.testkit.SparkStreamingJobTestKit;
                import cn.superhuang.datascalpel.sdk.testkit.SparkStreamingJobTestRun;
                import cn.superhuang.datascalpel.sdk.testkit.TestKafkaTopic;
                import cn.superhuang.datascalpel.sdk.testkit.TestModelTarget;
                import org.apache.spark.sql.types.DataTypes;
                import org.apache.spark.sql.types.StructType;
                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertEquals;
                import static org.junit.jupiter.api.Assertions.assertFalse;
                import static org.junit.jupiter.api.Assertions.assertTrue;

                class ExampleSparkStreamingJobTest {
                    @Test
                    void registersAndProcessesAllQueries() {
                        StructType modelSchema = new StructType()
                                .add("order_id", DataTypes.StringType, false);
                        TestModelTarget modelTarget = TestModelTarget.builder(modelSchema)
                                .primaryKeyColumns("order_id")
                                .build();

                        try (TestKafkaTopic source = TestKafkaTopic.create("orders");
                             TestKafkaTopic target = TestKafkaTopic.create("results");
                             SparkStreamingJobTestRun run = SparkStreamingJobTestKit.builder()
                                     .parameter("trigger_interval", "100 milliseconds")
                                     .kafkaInput("source_topic", source)
                                     .kafkaOutput("target_topic", target)
                                     .modelOutput("target_model", modelTarget)
                                     .start(new ExampleSparkStreamingJob())) {
                            assertEquals(2, run.queryNames().size());
                            source.publishUtf8("order-1", "order-1");
                            run.processAllAvailable();
                            run.assertHealthy();

                            assertFalse(run.kafkaOutputRecords("target_topic").isEmpty());
                            assertFalse(run.context().modelWrites("target_model").isEmpty());
                            run.assertCounter("orders.written_rows", 1);
                            run.assertCounter("datascalpel.model.write.successes", 1);
                            run.assertTimerRecorded("orders.model_upsert");
                            run.assertGauge("datascalpel.streaming.registered_queries", 2);
                            run.stop();
                            assertTrue(run.isStopped());
                        }
                    }
                }
                """;
    }

    private static SparkJarJobMode expectedMode(DataTask task) {
        return task.getType() == TaskType.SPARK_STREAMING_JAR
                ? SparkJarJobMode.STREAMING : SparkJarJobMode.BATCH;
    }

    private static String normalizeTopicName(SparkJarResourceType type, String value) {
        if (type != SparkJarResourceType.KAFKA_TOPIC) return null;
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kafka Topic 名称不能为空");
        }
        String topic = value.trim();
        if (topic.length() > 249 || containsControlCharacter(topic)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kafka Topic 名称无效");
        }
        return topic;
    }

    private static boolean JOB_API_VERSION_EQUALS(Integer value) { return value != null && value == JOB_API_VERSION; }
    private record JarMetadata(int apiVersion, String jobClass, SparkJarJobMode jobMode) {}
    private record UploadResult(String previousKey, SparkJarTaskDefinitionResponse response) {}
    private record ArtifactValidation(String objectKey, String sha256, long sizeBytes) {}
    private record LegacyResources(
            List<UpdateSparkJarTaskDefinitionRequest.Entry> sparkConf,
            SparkExecutionResourceSpec resources
    ) {}
}

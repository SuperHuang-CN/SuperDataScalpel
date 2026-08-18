package cn.superhuang.datascalpel.sdk.testkit;

import cn.superhuang.datascalpel.sdk.KafkaStartingOffsets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class TestKafkaTopic implements AutoCloseable {
    private final String name;
    private final Path root;
    private final Path archive;
    private final Path subscriptionsRoot;
    private final Path outputsRoot;
    private final Map<Integer, AtomicLong> partitionOffsets = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final LinkedHashMap<String, Subscription> subscriptions = new LinkedHashMap<>();
    private final List<Path> outputPaths = new ArrayList<>();
    private boolean closed;

    private TestKafkaTopic(String name) {
        this.name = required(name);
        try {
            root = Files.createTempDirectory("datascalpel-test-topic-");
            archive = Files.createDirectories(root.resolve("archive"));
            subscriptionsRoot = Files.createDirectories(root.resolve("subscriptions"));
            outputsRoot = Files.createDirectories(root.resolve("outputs"));
        } catch (IOException exception) {
            throw new SparkJobTestException("TESTKIT_KAFKA_TOPIC_CREATE_FAILED",
                    "Unable to create test Kafka topic", exception);
        }
    }

    public static TestKafkaTopic create(String name) {
        return new TestKafkaTopic(name);
    }

    public String name() { return name; }

    public TestKafkaRecord publishUtf8(String key, String value) {
        return publish(0,
                key == null ? null : key.getBytes(StandardCharsets.UTF_8),
                value == null ? null : value.getBytes(StandardCharsets.UTF_8),
                Instant.now());
    }

    public TestKafkaRecord publish(byte[] key, byte[] value) {
        return publish(0, key, value, Instant.now());
    }

    public synchronized TestKafkaRecord publish(
            int partition,
            byte[] key,
            byte[] value,
            Instant timestamp
    ) {
        requireOpen();
        if (partition < 0 || timestamp == null) throw new IllegalArgumentException("Kafka record metadata is invalid");
        long offset = partitionOffsets.computeIfAbsent(partition, ignored -> new AtomicLong()).getAndIncrement();
        TestKafkaRecord record = new TestKafkaRecord(
                key, value, name, partition, offset, timestamp, 0);
        String fileName = "%020d-%05d-%020d.json".formatted(
                sequence.getAndIncrement(), partition, offset);
        Path archived = archive.resolve(fileName);
        writeRecord(archived, record);
        for (Subscription subscription : subscriptions.values()) {
            copyAtomically(archived, subscription.path().resolve(fileName));
        }
        return record;
    }

    synchronized Path subscribe(String subscriptionId, KafkaStartingOffsets startingOffsets) {
        requireOpen();
        String id = required(subscriptionId);
        Objects.requireNonNull(startingOffsets, "startingOffsets");
        Subscription existing = subscriptions.get(id);
        if (existing != null) {
            if (existing.startingOffsets() != startingOffsets) {
                throw new SparkJobTestException("TESTKIT_KAFKA_STARTING_OFFSETS_CHANGED",
                        "The same test Kafka subscription cannot change starting offsets");
            }
            return existing.path();
        }
        try {
            Path path = Files.createDirectories(subscriptionsRoot.resolve(stableDirectory(id)));
            if (startingOffsets == KafkaStartingOffsets.EARLIEST) {
                try (var files = Files.list(archive)) {
                    for (Path source : files.sorted(Comparator.comparing(Path::getFileName)).toList()) {
                        copyAtomically(source, path.resolve(source.getFileName()));
                    }
                }
            }
            subscriptions.put(id, new Subscription(path, startingOffsets));
            return path;
        } catch (IOException exception) {
            throw new SparkJobTestException("TESTKIT_KAFKA_SUBSCRIPTION_CREATE_FAILED",
                    "Unable to create test Kafka subscription", exception);
        }
    }

    synchronized Path registerOutput() {
        requireOpen();
        try {
            Path path = Files.createDirectories(outputsRoot.resolve(UUID.randomUUID().toString()));
            outputPaths.add(path);
            return path;
        } catch (IOException exception) {
            throw new SparkJobTestException("TESTKIT_KAFKA_OUTPUT_CREATE_FAILED",
                    "Unable to create test Kafka output", exception);
        }
    }

    synchronized List<Path> outputPaths() {
        return List.copyOf(outputPaths);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        SparkJobTestContext.deleteRecursively(root);
    }

    private void requireOpen() {
        if (closed) throw new SparkJobTestException("TESTKIT_KAFKA_TOPIC_CLOSED", "Test Kafka topic is closed");
    }

    private static void writeRecord(Path target, TestKafkaRecord record) {
        String json = "{" +
                "\"key\":" + binary(record.key()) + "," +
                "\"value\":" + binary(record.value()) + "," +
                "\"topic\":\"" + escape(record.topic()) + "\"," +
                "\"partition\":" + record.partition() + "," +
                "\"offset\":" + record.offset() + "," +
                "\"timestamp\":\"" + record.timestamp() + "\"," +
                "\"timestampType\":" + record.timestampType() +
                "}";
        try {
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            moveAtomically(temporary, target);
        } catch (IOException exception) {
            throw new SparkJobTestException("TESTKIT_KAFKA_PUBLISH_FAILED",
                    "Unable to publish test Kafka record", exception);
        }
    }

    private static void copyAtomically(Path source, Path target) {
        try {
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            moveAtomically(temporary, target);
        } catch (IOException exception) {
            throw new SparkJobTestException("TESTKIT_KAFKA_PUBLISH_FAILED",
                    "Unable to deliver test Kafka record", exception);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String binary(byte[] value) {
        return value == null ? "null" : "\"" + Base64.getEncoder().encodeToString(value) + "\"";
    }

    private static String escape(String value) {
        StringBuilder result = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) result.append("\\u%04x".formatted((int) character));
                    else result.append(character);
                }
            }
        }
        return result.toString();
    }

    private static String stableDirectory(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Kafka topic value must not be blank");
        return value.trim();
    }

    private record Subscription(Path path, KafkaStartingOffsets startingOffsets) { }
}

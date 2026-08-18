package cn.superhuang.datascalpel.sdk.testkit;

import java.time.Instant;

public record TestJobStatus(String phase, String message, Instant updatedAt) {
}

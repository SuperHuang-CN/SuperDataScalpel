package cn.superhuang.data.scalpel.dispatcher.backend.yarn;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendExecutionState;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendStatus;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YarnStatusParser {
    public static final Pattern APPLICATION_ID = Pattern.compile("application_[0-9]+_[0-9]+");

    public String uniqueApplicationId(String output) throws BackendException {
        Matcher matcher = APPLICATION_ID.matcher(output == null ? "" : output);
        Set<String> ids = new LinkedHashSet<>();
        while (matcher.find()) ids.add(matcher.group());
        if (ids.size() != 1) throw new BackendException(
                ids.isEmpty() ? "YARN_APPLICATION_ID_MISSING" : "YARN_APPLICATION_ID_AMBIGUOUS",
                ids.isEmpty() ? "YARN提交未返回 Application ID" : "YARN返回了多个 Application ID");
        return ids.iterator().next();
    }

    public Set<String> applicationIds(String output) {
        Matcher matcher = APPLICATION_ID.matcher(output == null ? "" : output);
        Set<String> ids = new LinkedHashSet<>();
        while (matcher.find()) ids.add(matcher.group());
        return Set.copyOf(ids);
    }

    public ParsedStatus parseStatus(String output) throws BackendException {
        String state = field(output, "State");
        String finalState = optionalField(output, "Final-State");
        String tracking = optionalField(output, "Tracking-URL");
        Instant started = millis(optionalField(output, "Start-Time"));
        Instant ended = millis(optionalField(output, "Finish-Time"));
        BackendStatus status = switch (state) {
            case "NEW", "NEW_SAVING", "SUBMITTED", "ACCEPTED" -> value(BackendExecutionState.PENDING, started, null, null, null);
            case "RUNNING" -> value(BackendExecutionState.RUNNING, started, null, null, null);
            case "FINISHED" -> switch (finalState == null ? "UNDEFINED" : finalState) {
                case "SUCCEEDED" -> value(BackendExecutionState.SUCCEEDED, started, ended, null, null);
                case "KILLED" -> value(BackendExecutionState.CANCELLED, started, ended, null, null);
                default -> value(BackendExecutionState.FAILED, started, ended,
                        "YARN_APPLICATION_FAILED", "YARN Application执行失败");
            };
            case "FAILED" -> value(BackendExecutionState.FAILED, started, ended,
                    "YARN_APPLICATION_FAILED", "YARN Application执行失败");
            case "KILLED" -> value(BackendExecutionState.CANCELLED, started, ended, null, null);
            default -> value(BackendExecutionState.UNKNOWN, started, ended, null, null);
        };
        String trackingUrl = safeTracking(tracking);
        return new ParsedStatus(new BackendStatus(
                status.state(), status.startedAt(), status.endedAt(), status.safeErrorCode(),
                status.safeErrorMessage(), trackingUrl), trackingUrl);
    }

    private static BackendStatus value(
            BackendExecutionState state, Instant started, Instant ended, String code, String message) {
        return new BackendStatus(state, started, ended, code, message);
    }

    private static String field(String output, String name) throws BackendException {
        String value = optionalField(output, name);
        if (value == null) throw new BackendException("INVALID_YARN_STATUS", "YARN状态缺少" + name);
        return value.toUpperCase();
    }

    private static String optionalField(String output, String name) {
        Matcher matcher = Pattern.compile("(?mi)^\\s*" + Pattern.quote(name) + "\\s*:\\s*(.+?)\\s*$")
                .matcher(output == null ? "" : output);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static Instant millis(String value) {
        if (value == null || !value.matches("[0-9]+")) return null;
        try {
            long millis = Long.parseLong(value);
            return millis <= 0 ? null : Instant.ofEpochMilli(millis);
        } catch (RuntimeException ignored) { return null; }
    }

    private static String safeTracking(String value) {
        if (value == null || value.length() > 1000 || (!value.startsWith("http://") && !value.startsWith("https://"))) {
            return null;
        }
        return value;
    }

    public record ParsedStatus(BackendStatus status, String trackingUrl) { }
}

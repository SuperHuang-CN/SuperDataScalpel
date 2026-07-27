package cn.superhuang.datascalpel.taskengine.contract;



import java.time.Instant;

public record ProblemResponse(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        Instant timestamp
) {
}

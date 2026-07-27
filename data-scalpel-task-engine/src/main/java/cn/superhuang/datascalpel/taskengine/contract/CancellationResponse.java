package cn.superhuang.datascalpel.taskengine.contract;



import java.util.UUID;

public record CancellationResponse(UUID requestId, String state) {
}

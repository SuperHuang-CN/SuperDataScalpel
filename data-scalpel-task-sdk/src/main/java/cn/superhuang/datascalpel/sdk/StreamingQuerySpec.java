package cn.superhuang.datascalpel.sdk;

public record StreamingQuerySpec(String queryName, String checkpointLocation) {
    public StreamingQuerySpec {
        if (queryName == null || queryName.isBlank()) {
            throw new IllegalArgumentException("queryName must not be blank");
        }
        if (checkpointLocation == null || checkpointLocation.isBlank()) {
            throw new IllegalArgumentException("checkpointLocation must not be blank");
        }
    }
}

package cn.superhuang.datascalpel.taskengine.httpapi;

public record HttpApiPullSummary(int pages, int batches, long rows, long responseBytes) {
    public HttpApiPullSummary {
        if (pages < 0) throw new IllegalArgumentException("pages must not be negative");
        if (batches < 0) throw new IllegalArgumentException("batches must not be negative");
        if (rows < 0) throw new IllegalArgumentException("rows must not be negative");
        if (responseBytes < 0) throw new IllegalArgumentException("responseBytes must not be negative");
    }
}

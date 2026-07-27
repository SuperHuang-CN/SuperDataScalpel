package cn.superhuang.datascalpel.taskengine.compiler;

public final class CompilationCancelledException extends RuntimeException {
    public CompilationCancelledException() {
        super("任务编译已取消");
    }
}

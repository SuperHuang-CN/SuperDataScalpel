package cn.superhuang.datascalpel.sdk;

/**
 * 批处理作业入口，实现 execute 编写一次处理流程。
 * @apiGroup 开始编写
 * @apiMode BATCH
 * @apiExample public void execute(SparkJobContext context) throws Exception {
 *       var rows = context.models().read("source_model");
 *       rows.show(20, false);
 *   }
 * @apiNote 主类需公开并有公开无参构造器；不要提供 main 方法。
 */
public interface SparkBatchJob {
    /**
     * 平台启动批任务时调用，方法返回表示本次处理结束。
     * @param context 平台传入的任务上下文。
     */
    void execute(SparkJobContext context) throws Exception;
}

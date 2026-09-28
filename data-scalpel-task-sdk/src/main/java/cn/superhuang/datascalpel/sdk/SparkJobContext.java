package cn.superhuang.datascalpel.sdk;

import org.apache.spark.sql.SparkSession;

/**
 * 任务提供的入口上下文：获取数据、参数与运行信息。
 * @apiGroup 开始编写
 * @apiOrder 0
 * @apiExample var rows = context.models().read("source_model");
 *   rows.show(20, false);
 * @apiNote context 是批处理 execute(SparkJobContext context) 或实时 start(SparkStreamingJobContext context) 的方法入参；示例写在该方法内部。使用平台提供的 SparkSession，不要调用 spark.stop() 或 System.exit()。
 */
public interface SparkJobContext {
    /**
     * 取得当前任务的 SparkSession。
     * @apiExample var spark = context.spark();
     *   spark.range(5).show();
     * @apiNote 先通过 context.spark() 取得对象，再用 spark. 调用其方法；spark 是这里声明的局部变量，不是预置变量，也不能直接写 spark()。
     * @return 平台创建的 SparkSession，可继续调用 range、sql 等 Spark 方法；生命周期由平台管理。
     */
    SparkSession spark();

    /**
     * 查看任务与本次执行的身份信息。
     * @apiExample var identity = context.identity();
     *   System.out.println(identity.runId());
     */
    SparkJobIdentity identity();

    /**
     * 读取任务配置中的自定义参数。
     * @apiExample var parameters = context.parameters();
     *   String mode = parameters.find("mode").orElse("full");
     */
    SparkJobParameters parameters();

    /**
     * 读取模型或配置模型写入。
     * @apiExample var models = context.models();
     *   var rows = models.read("source_model");
     *   rows.show(20, false);
     */
    ModelResources models();

    /**
     * 读取 JDBC 表、SQL 查询或配置写入。
     * @apiExample var jdbc = context.jdbc();
     *   var rows = jdbc.readTable("source_db", "orders");
     *   rows.show(20, false);
     */
    JdbcResources jdbc();

    /**
     * 记录阶段、事件、计数与耗时。
     * @apiExample var log = context.observability();
     *   log.info("处理开始", "开始处理数据");
     */
    JobObservability observability();
}

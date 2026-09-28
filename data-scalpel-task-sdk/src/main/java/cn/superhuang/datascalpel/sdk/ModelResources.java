package cn.superhuang.datascalpel.sdk;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

/**
 * 通过资源绑定名读写已发布的数据模型。
 * @apiGroup 读写模型
 * @apiOrder 10
 * @apiExample var rows = context.models().read("source_model");
 *   context.models().write("target_model", rows)
 *       .mode(ModelWriteMode.APPEND).mapSameName().execute();
 * @apiNote 绑定名是左侧资源的“代码引用名”，不是模型名称；读取需输入权限，写入需输出权限。
 */
public interface ModelResources {
    /**
     * 读取模型数据，返回可继续筛选、转换的 Dataset。
     * @param bindingName 任务资源的代码引用名，区分大小写；不是资源显示名称。
     * @param options 本次读取选项，可使用 JdbcReadOptions.defaults()。
     * @apiExample var options = JdbcReadOptions.builder().fetchSize(1000).build();
     *   var rows = context.models().read("source_model", options);
     *   rows.show(20, false);
     */
    Dataset<Row> read(String bindingName, JdbcReadOptions options);

    /**
     * 读取模型数据，返回可继续筛选、转换的 Dataset。
     * @param bindingName 任务资源的代码引用名，区分大小写；不是资源显示名称。
     * @apiExample var rows = context.models().read("source_model");
     *   rows.show(20, false);
     */
    default Dataset<Row> read(String bindingName) {
        return read(bindingName, JdbcReadOptions.defaults());
    }

    /**
     * 创建写入配置；调用 execute() 才真正执行写入。
     * @param bindingName 任务资源的代码引用名，区分大小写；不是资源显示名称。
     * @param source 待写入的 Dataset，字段需要满足目标映射。
     * @apiExample var rows = context.models().read("source_model");
     *   var result = context.models().write("target_model", rows)
     *       .mode(ModelWriteMode.APPEND).mapSameName().execute();
     * @apiNote execute() 会执行写入；请先确认目标模型和写入方式。
     */
    ModelWriteOperation write(String bindingName, Dataset<Row> source);
}

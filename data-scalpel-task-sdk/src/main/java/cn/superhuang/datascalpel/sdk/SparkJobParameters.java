package cn.superhuang.datascalpel.sdk;

import java.util.Map;
import java.util.Optional;

/**
 * 读取任务配置中的字符串参数。
 * @apiGroup 任务信息
 * @apiExample String date = context.parameters().require("businessDate");
 *   String mode = context.parameters().find("mode").orElse("full");
 */
public interface SparkJobParameters {
    /**
     * 读取可选参数，参数不存在时返回空 Optional。
     * @param name 任务运行配置中的参数名，区分大小写。
     * @apiExample String mode = context.parameters().find("mode").orElse("full");
     */
    Optional<String> find(String name);

    /**
     * 读取必填参数，参数不存在时报错。
     * @param name 任务运行配置中的参数名，区分大小写。
     * @apiExample String date = context.parameters().require("businessDate");
     */
    String require(String name);

    /**
     * 取得全部参数，返回不可修改且保持插入顺序的映射。
     * @apiExample var parameters = context.parameters().asMap();
     */
    Map<String, String> asMap();
}

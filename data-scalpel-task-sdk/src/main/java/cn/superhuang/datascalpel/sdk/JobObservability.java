package cn.superhuang.datascalpel.sdk;

import java.util.Map;

/**
 * 让运行页面看到处理阶段、业务事件、数量与耗时。
 * @apiGroup 日志与指标
 * @apiExample context.observability().status("读取", "开始读取数据");
 *   context.observability().addCounter("processed", 100);
 * @apiNote 仅在 Driver 端使用，不要放入 Executor 闭包；不要记录凭据或敏感数据。
 */
public interface JobObservability {
    /**
     * 记录普通业务事件。
     * @param eventName 业务事件名称。
     * @param message 简短说明，不要包含凭据或敏感数据。
     */
    void info(String eventName, String message);

    /**
     * 记录普通业务事件。
     * @param eventName 业务事件名称。
     * @param message 简短说明，不要包含凭据或敏感数据。
     * @param attributes 事件的附加属性，不要包含敏感值。
     */
    void info(String eventName, String message, Map<String, String> attributes);

    /**
     * 记录需要关注但不阻止处理的事件。
     * @param eventName 业务事件名称。
     * @param message 简短说明，不要包含凭据或敏感数据。
     */
    void warn(String eventName, String message);

    /**
     * 记录需要关注但不阻止处理的事件。
     * @param eventName 业务事件名称。
     * @param message 简短说明，不要包含凭据或敏感数据。
     * @param attributes 事件的附加属性，不要包含敏感值。
     */
    void warn(String eventName, String message, Map<String, String> attributes);

    /**
     * 记录错误事件；记录本身不代替抛出异常或终止作业。
     * @param eventName 业务事件名称。
     * @param message 简短说明，不要包含凭据或敏感数据。
     */
    void error(String eventName, String message);

    /**
     * 记录错误事件；记录本身不代替抛出异常或终止作业。
     * @param eventName 业务事件名称。
     * @param message 简短说明，不要包含凭据或敏感数据。
     * @param attributes 事件的附加属性，不要包含敏感值。
     */
    void error(String eventName, String message, Map<String, String> attributes);

    /**
     * 更新当前处理阶段及说明。
     * @param phase 当前处理阶段名称。
     * @param message 简短说明，不要包含凭据或敏感数据。
     */
    void status(String phase, String message);

    /**
     * 累计增加一个计数指标。
     * @param name 自定义计数名称，如 processed；字母开头，仅含字母、数字、点、下划线和短横线，最长 100 字符。
     * @param delta 累计增加的数量，不能为负。
     */
    void addCounter(String name, long delta);

    /**
     * 更新一个当前值指标。
     * @param name 自定义指标名称，如 progress；与计数名称规则相同，不可与计数重名。
     * @param value 当前指标值，必须为有限数值，不能是 NaN 或无穷大。
     */
    void setGauge(String name, double value);

    /**
     * 开始计时，建议通过 try-with-resources 自动结束。
     * @param name 本次计时操作的名称，如 transform；与指标名称规则相同。
     */
    JobOperation operation(String name);
}

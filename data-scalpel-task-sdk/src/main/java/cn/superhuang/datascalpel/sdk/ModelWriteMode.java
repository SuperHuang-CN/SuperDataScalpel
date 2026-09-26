package cn.superhuang.datascalpel.sdk;

/**
 * 模型写入方式。
 * @apiGroup 读写模型
 */
public enum ModelWriteMode {
    /**
     * 追加新数据，不清空目标。
     */
    APPEND,
    /**
     * 覆盖目标数据，使用前确认可清空原数据。
     */
    OVERWRITE,
    /**
     * 按模型完整主键更新已有记录、插入新记录。
     */
    UPSERT
}

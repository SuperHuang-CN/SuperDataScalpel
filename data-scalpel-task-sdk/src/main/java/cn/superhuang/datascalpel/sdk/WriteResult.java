package cn.superhuang.datascalpel.sdk;

/**
 * 查看 SDK 成功处理的输入行数，未知不等于零；不是 INSERT/UPDATE/DELETE 物理变更数之和。
 * @apiGroup 写入结果
 * @param affectedRows 成功处理的输入行数；UPSERT 包含值未变化的匹配行，覆盖不计删除行，无法可靠统计时为 null。
 */
public record WriteResult(Long affectedRows) {
    /**
     * 创建已知影响行数的结果。
     * @param affectedRows 非负的影响行数；未知行数请使用 unknown()。
     */
    public static WriteResult known(long affectedRows) {
        if (affectedRows < 0) {
            throw new IllegalArgumentException("affectedRows must not be negative");
        }
        return new WriteResult(affectedRows);
    }

    /**
     * 创建影响行数未知的结果。
     */
    public static WriteResult unknown() {
        return new WriteResult(null);
    }
}

package cn.superhuang.data.scalpel.dispatcher.backend;

import cn.superhuang.data.scalpel.contract.execution.SparkConfigurationEntry;

import java.util.List;
import java.util.Locale;

/** Extracts the one user-controlled Driver JVM option from the persisted Spark configuration. */
public final class DriverJavaOptions {
    private static final String KEY = "spark.driver.extrajavaoptions";

    private DriverJavaOptions() {
    }

    public static String extract(List<SparkConfigurationEntry> sparkConf) {
        if (sparkConf == null) return null;
        return sparkConf.stream()
                .filter(entry -> entry != null && entry.name() != null
                        && KEY.equals(entry.name().trim().toLowerCase(Locale.ROOT)))
                .map(SparkConfigurationEntry::value)
                .findFirst()
                .orElse(null);
    }

    public static List<SparkConfigurationEntry> withoutDriverJavaOptions(List<SparkConfigurationEntry> sparkConf) {
        if (sparkConf == null || sparkConf.isEmpty()) return List.of();
        return sparkConf.stream().filter(entry -> entry != null && (entry.name() == null
                || !KEY.equals(entry.name().trim().toLowerCase(Locale.ROOT)))).toList();
    }
}

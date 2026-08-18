package cn.superhuang.datascalpel.sdk;

import java.util.Map;
import java.util.Optional;

public interface SparkJobParameters {
    Optional<String> find(String name);

    String require(String name);

    /** Returns an immutable insertion-ordered view. */
    Map<String, String> asMap();
}

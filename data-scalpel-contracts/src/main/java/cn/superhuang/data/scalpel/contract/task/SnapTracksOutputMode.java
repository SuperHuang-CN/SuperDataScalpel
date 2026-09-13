package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("轨迹路网吸附结果范围：返回全部原始观测，或只返回成功匹配到网络线的观测。")
public enum SnapTracksOutputMode {
    ALL_FEATURES,
    MATCHED_FEATURES
}

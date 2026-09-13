package cn.superhuang.data.scalpel.business.asset.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "可登记为资产的来源类型：DATA_MODEL 已发布数据模型；FILE_DATASET 至少有一张可用逻辑表的文件数据集；PANORAMA 有可用当前成品的全景影像；DICTIONARY 已启用码表；DATA_SERVICE 已启用数据服务。")
public enum AssetType {
    DATA_MODEL,
    FILE_DATASET,
    PANORAMA,
    DICTIONARY,
    DATA_SERVICE
}

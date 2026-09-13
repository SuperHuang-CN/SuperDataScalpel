package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("文件数据集内单个文件的准备状态：PREPARING 尚在上传或处理，READY 已可供解析；它不表示整张表已经解析成功。")
public enum FileDatasetFileStatus {
    PREPARING,
    READY
}

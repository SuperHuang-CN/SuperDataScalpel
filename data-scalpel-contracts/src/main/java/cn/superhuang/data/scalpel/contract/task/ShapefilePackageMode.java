package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Shapefile 制品形态：ZIP 生成 {baseName}.zip，归档根目录含五个组件；COMPONENT_DIRECTORY 直接生成 .shp/.shx/.dbf/.prj/.cpg。两者均在完整提交后最后写 _SUCCESS。")
public enum ShapefilePackageMode {
    ZIP,
    COMPONENT_DIRECTORY
}

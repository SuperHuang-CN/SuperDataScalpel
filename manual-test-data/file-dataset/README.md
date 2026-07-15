# 文件数据集手工测试样例

目录覆盖当前可以真实解析的全部文件数据集格式：

- `roads.csv`：CSV，UTF-8、逗号分隔、首行表头。
- `roads.tsv`：TSV，UTF-8、制表符分隔、首行表头。
- `notes.txt`：TXT，UTF-8 行文本。
- `roads.json`：JSON，顶层数组，不需要 JSON Pointer。
- `roads.jsonl`：JSONL，每行一个 JSON 对象。
- `roads.xls`：Excel 97-2003，工作表名为 `Roads`。
- `roads.xlsx`：Excel OOXML，工作表名为 `Roads`。
- `roads.parquet`：Parquet，包含日期、时间戳、decimal、List 和 Map 字段。
- `roads.avro`：Avro Object Container File，使用 Snappy 内部 codec。

GZIP 支持组合也各保留一份：

- `roads.csv.gz`
- `roads.tsv.gz`
- `notes.txt.gz`
- `roads.jsonl.gz`

生成器只创建缺失的样例文件，不覆盖目录中已经存在的数据文件。

这些文件由 `ManualFileDatasetSampleDataGeneratorTest` 生成；需要重新生成时执行：

```bash
./mvnw -pl data-scalpel-admin -am test \
  -Dtest=ManualFileDatasetSampleDataGeneratorTest \
  -DgenerateManualFileDatasetSamples=true \
  -Dsurefire.failIfNoSpecifiedTests=false
```

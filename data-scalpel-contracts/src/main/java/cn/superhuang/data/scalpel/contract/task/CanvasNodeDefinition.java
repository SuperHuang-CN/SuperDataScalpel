package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ModelInputNodeDefinition.class, name = "MODEL_INPUT"),
        @JsonSubTypes.Type(value = JdbcInputNodeDefinition.class, name = "JDBC_INPUT"),
        @JsonSubTypes.Type(value = FileDatasetInputNodeDefinition.class, name = "FILE_DATASET_INPUT"),
        @JsonSubTypes.Type(value = HttpApiInputNodeDefinition.class, name = "HTTP_API_INPUT"),
        @JsonSubTypes.Type(value = KafkaInputNodeDefinition.class, name = "KAFKA_INPUT"),
        @JsonSubTypes.Type(value = JoinNodeDefinition.class, name = "JOIN"),
        @JsonSubTypes.Type(value = StreamJoinNodeDefinition.class, name = "STREAM_JOIN"),
        @JsonSubTypes.Type(value = RenameNodeDefinition.class, name = "RENAME"),
        @JsonSubTypes.Type(value = ModelOutputNodeDefinition.class, name = "MODEL_OUTPUT"),
        @JsonSubTypes.Type(value = JdbcOutputNodeDefinition.class, name = "JDBC_OUTPUT"),
        @JsonSubTypes.Type(value = KafkaOutputNodeDefinition.class, name = "KAFKA_OUTPUT"),
        @JsonSubTypes.Type(value = FileOutputNodeDefinition.class, name = "FILE_OUTPUT")
})
public sealed interface CanvasNodeDefinition
        permits ModelInputNodeDefinition, JdbcInputNodeDefinition, FileDatasetInputNodeDefinition,
                HttpApiInputNodeDefinition,
                KafkaInputNodeDefinition, JoinNodeDefinition, StreamJoinNodeDefinition, RenameNodeDefinition,
                ModelOutputNodeDefinition, JdbcOutputNodeDefinition, KafkaOutputNodeDefinition,
                FileOutputNodeDefinition {
    String id();

    String name();

    CanvasNodeLayout layout();

    @JsonIgnore
    CanvasNodeType nodeType();
}

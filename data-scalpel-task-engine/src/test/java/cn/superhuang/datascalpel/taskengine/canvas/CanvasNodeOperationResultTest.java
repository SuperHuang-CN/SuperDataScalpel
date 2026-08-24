package cn.superhuang.datascalpel.taskengine.canvas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasNodeOperationResultTest {

    @Test
    void schemaOnlyOutputFactoriesAcceptAbsentPreparedArtifacts() {
        CanvasNodeOperationResult jdbcOrModel = CanvasNodeOperationResult.output(null, null);
        CanvasNodeOperationResult kafka = CanvasNodeOperationResult.kafkaOutput(null, null);
        CanvasNodeOperationResult file = CanvasNodeOperationResult.fileOutput(null, null);

        assertTrue(jdbcOrModel.preparedOutputs().isEmpty());
        assertTrue(jdbcOrModel.lineageOutputCandidates().isEmpty());
        assertTrue(kafka.preparedKafkaOutputs().isEmpty());
        assertTrue(kafka.lineageOutputCandidates().isEmpty());
        assertTrue(file.preparedFileOutputs().isEmpty());
        assertTrue(file.lineageOutputCandidates().isEmpty());
    }
}

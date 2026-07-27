package cn.superhuang.datascalpel.taskengine.compiler.canvas;

import cn.superhuang.data.scalpel.contract.task.NodeCompilationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutableNodeCompilationTest {

    @Test
    void collectsWarningsWithoutInvalidatingTheNode() {
        MutableNodeCompilation compilation = new MutableNodeCompilation("node-1");

        compilation.warning("RISK", "存在风险", "configuration.value");

        assertFalse(compilation.hasErrors());
        assertEquals(NodeCompilationState.WARNING, compilation.result().state());
        assertEquals("RISK", compilation.result().issues().getFirst().code());
    }

    @Test
    void collectsErrorsAndMarksTheNodeInvalid() {
        MutableNodeCompilation compilation = new MutableNodeCompilation("node-1");

        compilation.error("INVALID", "配置无效", "configuration.value");

        assertTrue(compilation.hasErrors());
        assertEquals(NodeCompilationState.ERROR, compilation.result().state());
        assertEquals("INVALID", compilation.result().issues().getFirst().code());
    }
}

package cn.superhuang.data.scalpel.engine.query;

import cn.superhuang.data.scalpel.contract.service.SqlServiceDefinition;
import cn.superhuang.data.scalpel.contract.service.SqlServiceParameterDefinition;
import cn.superhuang.data.scalpel.contract.service.SqlServiceQueryRequest;
import cn.superhuang.data.scalpel.contract.service.SqlServiceResultFieldDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlServiceRequestCompilerTest {

    private final SqlServiceRequestCompiler compiler = new SqlServiceRequestCompiler(
            new EngineQueryProperties(20, 100, 50, 1000, 100_000, 30)
    );

    @Test
    void compilesRepeatedParametersAndPreservesExplicitNull() {
        LinkedHashMap<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("keyword", null);
        CompiledSqlServiceRequest request = compiler.compile(definition(false), new SqlServiceQueryRequest(
                null, null, arguments, null
        ));

        assertEquals(1, request.pageNo());
        assertEquals(20, request.pageSize());
        assertEquals(0, request.offset());
        assertEquals(2, request.parameters().size());
        assertNull(request.parameters().get(0).value());
        assertNull(request.parameters().get(1).value());
    }

    @Test
    void rejectsUnknownMissingAndOutOfRangeRequests() {
        assertThrows(EngineQueryValidationException.class, () -> compiler.compile(
                definition(false), new SqlServiceQueryRequest(1, 20, Map.of("unknown", "x"), false)
        ));
        assertThrows(EngineQueryValidationException.class, () -> compiler.compile(
                definition(true), new SqlServiceQueryRequest(1, 20, Map.of(), false)
        ));
        assertThrows(EngineQueryValidationException.class, () -> compiler.compile(
                definition(false), new SqlServiceQueryRequest(5002, 20, Map.of(), false)
        ));
        assertThrows(EngineQueryValidationException.class, () -> compiler.compile(
                definition(false), new SqlServiceQueryRequest(1, 101, Map.of(), false)
        ));
        SqlServiceRequestCompiler integerOffsetCompiler = new SqlServiceRequestCompiler(
                new EngineQueryProperties(20, 100, 50, 1000, Integer.MAX_VALUE, 30)
        );
        assertThrows(EngineQueryValidationException.class, () -> integerOffsetCompiler.compile(
                definition(false), new SqlServiceQueryRequest(Integer.MAX_VALUE, 100, Map.of(), false)
        ));
    }

    private static SqlServiceDefinition definition(boolean required) {
        PlatformTypeDefinition string = PlatformTypeDefinition.string(100);
        return new SqlServiceDefinition(
                1,
                "SELECT id FROM customer WHERE ? IS NULL OR name ILIKE ?",
                List.of("keyword", "keyword"),
                List.of(new SqlServiceParameterDefinition("keyword", string, required, null)),
                List.of(new SqlServiceResultFieldDefinition(
                        "id", PlatformTypeDefinition.of(PlatformDataType.LONG), false
                ))
        );
    }
}

package cn.superhuang.data.scalpel.engine.query;

import cn.superhuang.data.scalpel.contract.service.ConditionType;
import cn.superhuang.data.scalpel.contract.service.ServiceFieldDefinition;
import cn.superhuang.data.scalpel.contract.service.SortDirection;
import cn.superhuang.data.scalpel.contract.service.StandardFilter;
import cn.superhuang.data.scalpel.contract.service.StandardOrder;
import cn.superhuang.data.scalpel.contract.service.StandardServiceDefinition;
import cn.superhuang.data.scalpel.contract.service.StandardServiceQueryRequest;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StandardServiceRequestCompilerTest {

    private final StandardServiceRequestCompiler compiler = new StandardServiceRequestCompiler(
            new EngineQueryProperties(20, 100, 50, 1000, 100_000, 30)
    );

    @Test
    void excludesGeometryFromDefaultProjectionAndRejectsExplicitUse() {
        CompiledServiceRequest defaultRequest = compiler.compile(
                definition(),
                request(List.of(), List.of(), List.of())
        );

        assertEquals(
                List.of("id"),
                defaultRequest.query().projections().stream().map(projection -> projection.alias()).toList()
        );
        assertThrows(
                EngineQueryValidationException.class,
                () -> compiler.compile(definition(), request(List.of("shape"), List.of(), List.of()))
        );
        assertThrows(
                EngineQueryValidationException.class,
                () -> compiler.compile(
                        definition(),
                        request(
                                List.of("id"),
                                List.of(new StandardFilter("shape", "=", "POINT (0 0)", null, List.of())),
                                List.of()
                        )
                )
        );
        assertThrows(
                EngineQueryValidationException.class,
                () -> compiler.compile(
                        definition(),
                        request(
                                List.of("id"),
                                List.of(),
                                List.of(new StandardOrder("shape", SortDirection.ASC))
                        )
                )
        );
    }

    private static StandardServiceDefinition definition() {
        return new StandardServiceDefinition(
                1,
                "warehouse",
                "public",
                "spatial_asset",
                List.of(
                        new ServiceFieldDefinition("id", "id", PlatformDataType.LONG, false, true),
                        new ServiceFieldDefinition("shape", "shape", PlatformDataType.GEOMETRY, true, false)
                )
        );
    }

    private static StandardServiceQueryRequest request(
            List<String> columns,
            List<StandardFilter> filters,
            List<StandardOrder> orders
    ) {
        return new StandardServiceQueryRequest(
                1,
                20,
                ConditionType.AND,
                columns,
                filters,
                orders,
                List.of(),
                List.of(),
                false
        );
    }
}

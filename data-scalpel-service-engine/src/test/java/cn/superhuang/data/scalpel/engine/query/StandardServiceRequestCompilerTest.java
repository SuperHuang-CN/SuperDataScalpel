package cn.superhuang.data.scalpel.engine.query;

import cn.superhuang.data.scalpel.contract.service.ServiceFieldDefinition;
import cn.superhuang.data.scalpel.contract.service.SortDirection;
import cn.superhuang.data.scalpel.contract.service.StandardFilterNode;
import cn.superhuang.data.scalpel.contract.service.StandardFilterOperator;
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
            new EngineQueryProperties(20, 100, 50, 5, 1000, 100_000, 30)
    );

    @Test
    void excludesGeometryFromDefaultProjectionAndRejectsExplicitUse() {
        CompiledServiceRequest defaultRequest = compiler.compile(
                definition(),
                request(List.of(), null, List.of())
        );

        assertEquals(
                List.of("id"),
                defaultRequest.query().projections().stream().map(projection -> projection.alias()).toList()
        );
        assertThrows(
                EngineQueryValidationException.class,
                () -> compiler.compile(definition(), request(List.of("shape"), null, List.of()))
        );
        assertThrows(
                EngineQueryValidationException.class,
                () -> compiler.compile(
                        definition(),
                        request(
                                List.of("id"),
                                new StandardFilterNode(
                                        null,
                                        StandardFilterOperator.AND,
                                        null,
                                        List.of(new StandardFilterNode(
                                                "shape", StandardFilterOperator.EQ, "POINT (0 0)", List.of()
                                        ))
                                ),
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
                                null,
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
            List<String> fields,
            StandardFilterNode filter,
            List<StandardOrder> sort
    ) {
        return new StandardServiceQueryRequest(
                1,
                20,
                fields,
                filter,
                sort,
                List.of(),
                List.of(),
                false
        );
    }
}

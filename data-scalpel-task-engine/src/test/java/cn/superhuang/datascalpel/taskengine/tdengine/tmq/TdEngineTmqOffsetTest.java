package cn.superhuang.datascalpel.taskengine.tdengine.tmq;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TdEngineTmqOffsetTest {

    @Test
    void roundTripsVersionedCheckpointJsonInVGroupOrder() {
        TdEngineTmqOffset offset = new TdEngineTmqOffset("meters_topic", Map.of(1, 9834L, 0, 12031L));

        assertEquals(
                "{\"version\":1,\"topic\":\"meters_topic\",\"vGroups\":{\"0\":12031,\"1\":9834}}",
                offset.json()
        );
        assertEquals(offset.topic(), TdEngineTmqOffset.parse(offset.json()).topic());
        assertEquals(offset.vGroups(), TdEngineTmqOffset.parse(offset.json()).vGroups());
        assertEquals(offset, TdEngineTmqOffset.parse(offset.json()));
        assertEquals(offset.hashCode(), TdEngineTmqOffset.parse(offset.json()).hashCode());
        assertEquals(offset.json(), offset.toString());
        assertNotEquals(offset, new TdEngineTmqOffset("meters_topic", Map.of(0, 12032L, 1, 9834L)));
    }

    @Test
    void rejectsUnknownVersionsAndInvalidVGroups() {
        assertThrows(
                TdEngineTmqException.class,
                () -> TdEngineTmqOffset.parse(
                        "{\"version\":2,\"topic\":\"meters_topic\",\"vGroups\":{\"0\":1}}"
                )
        );
        assertThrows(
                TdEngineTmqException.class,
                () -> TdEngineTmqOffset.parse(
                        "{\"version\":1,\"topic\":\"meters_topic\",\"vGroups\":{\"bad\":1}}"
                )
        );
        assertThrows(
                TdEngineTmqException.class,
                () -> TdEngineTmqOffset.parse(
                        "{\"version\":1,\"topic\":\"meters_topic\",\"vGroups\":{}}"
                )
        );
    }
}

package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TrackFieldStatisticContractTest {
    @Test void newKindsRoundTripAndOldConstructorCopiesTheList() {
        var mapper = new ObjectMapper();
        for (var kind : List.of(TrackSummaryStatisticKind.COUNT, TrackSummaryStatisticKind.COUNT_FIELD, TrackSummaryStatisticKind.ANY)) {
            var statistic = new TrackSummaryStatistic("", kind, kind == TrackSummaryStatisticKind.COUNT ? null : "label", "result");
            assertEquals(statistic, mapper.readValue(mapper.writeValueAsString(statistic), TrackSummaryStatistic.class));
            var list = new ArrayList<>(List.of(statistic));
            var c = new TrackReconstructConfiguration("points", "shape", List.of("id"), "time", null, null, list, "tracks", "shape", "start", "end", "count");
            list.clear(); assertEquals(1, c.summaryStatistics().size()); assertNull(c.reconstruction());
            assertEquals(c, mapper.readValue(mapper.writeValueAsString(c), TrackReconstructConfiguration.class));
            var dwell = mapper.readValue("{\"distanceThreshold\":0,\"minimumDuration\":0,\"summaryStatistics\":[" + mapper.writeValueAsString(statistic) + "]}", TrackFindDwellConfiguration.class);
            assertEquals(dwell, mapper.readValue(mapper.writeValueAsString(dwell), TrackFindDwellConfiguration.class));
        }
        assertThrows(Exception.class, () -> mapper.readValue("{\"kind\":\"SAMPLE_RANDOM\"}", TrackSummaryStatistic.class));
    }
}

package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "algorithm")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SpatialPointClusterParameters.Dbscan.class, name = "DBSCAN"),
        @JsonSubTypes.Type(value = SpatialPointClusterParameters.Hdbscan.class, name = "HDBSCAN"),
        @JsonSubTypes.Type(value = SpatialPointClusterParameters.MultiScale.class, name = "MULTI_SCALE")
})
public sealed interface SpatialPointClusterParameters permits
        SpatialPointClusterParameters.Dbscan,
        SpatialPointClusterParameters.Hdbscan,
        SpatialPointClusterParameters.MultiScale {

    int minimumFeatures();

    record Dbscan(
            double searchDistance,
            SpatialDistanceUnit searchDistanceUnit,
            int minimumFeatures
    ) implements SpatialPointClusterParameters {
    }

    record Hdbscan(int minimumFeatures) implements SpatialPointClusterParameters {
    }

    record MultiScale(int minimumFeatures, double sensitivity) implements SpatialPointClusterParameters {
    }
}

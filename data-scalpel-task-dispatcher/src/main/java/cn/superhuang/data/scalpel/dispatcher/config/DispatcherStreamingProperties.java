package cn.superhuang.data.scalpel.dispatcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.UUID;

@ConfigurationProperties(prefix = "data-scalpel.dispatcher.streaming")
public record DispatcherStreamingProperties(
        String checkpointBaseUri
) {
    public DispatcherStreamingProperties {
        checkpointBaseUri = checkpointBaseUri == null || checkpointBaseUri.isBlank()
                ? "file:///checkpoints"
                : normalize(checkpointBaseUri);
    }

    public String deploymentCheckpointUri(UUID deploymentId) {
        if (deploymentId == null) {
            throw new IllegalArgumentException("Streaming deployment ID is required");
        }
        return checkpointBaseUri + "/deployments/" + deploymentId;
    }

    public boolean configured() {
        try {
            URI uri = URI.create(checkpointBaseUri);
            return uri.getScheme() != null
                    && switch (uri.getScheme().toLowerCase()) {
                        case "file", "hdfs", "viewfs", "s3a" -> true;
                        default -> false;
                    };
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String normalize(String value) {
        String normalized = value.trim().replaceFirst("/+$", "");
        URI uri = URI.create(normalized);
        if (uri.getScheme() == null || uri.getUserInfo() != null
                || !switch (uri.getScheme().toLowerCase()) {
                    case "file", "hdfs", "viewfs", "s3a" -> true;
                    default -> false;
                }) {
            throw new IllegalArgumentException(
                    "Streaming Checkpoint 只支持 file/HDFS/ViewFS/S3A URI，且 URI 不能包含凭证");
        }
        return normalized;
    }
}

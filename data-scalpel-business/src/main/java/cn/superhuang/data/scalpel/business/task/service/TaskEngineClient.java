package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.contract.task.TaskCompilationRequest;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskCompilationCancellationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.UUID;

@Component
public class TaskEngineClient {

    private final TaskEngineProperties properties;
    private final ObjectMapper objectMapper;

    public TaskEngineClient(TaskEngineProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public TaskCompilationResponse compile(String baseUrl, TaskCompilationRequest request) {
        try {
            return client(baseUrl).post()
                    .uri("/api/v1/task-compilations")
                    .body(request)
                    .retrieve()
                    .body(TaskCompilationResponse.class);
        } catch (RestClientResponseException exception) {
            throw remoteFailure(exception);
        } catch (ResourceAccessException exception) {
            throw accessFailure(exception);
        } catch (RestClientException exception) {
            throw clientFailure(exception, "Task Engine 调用失败");
        }
    }

    public TaskCompilationCancellationResponse cancel(String baseUrl, UUID requestId) {
        try {
            return client(baseUrl).post()
                    .uri("/api/v1/task-compilations/{requestId}/actions/cancel", requestId)
                    .retrieve()
                    .body(TaskCompilationCancellationResponse.class);
        } catch (RestClientResponseException exception) {
            throw remoteFailure(exception);
        } catch (ResourceAccessException exception) {
            throw accessFailure(exception);
        } catch (RestClientException exception) {
            throw clientFailure(exception, "Task Engine 取消请求失败");
        }
    }

    private RestClient client(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
        requestFactory.setReadTimeout(Math.toIntExact(properties.requestTimeout().toMillis()));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + properties.token())
                .build();
    }

    private ResponseStatusException remoteFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 400 || status == 404 || status == 409 || status == 429 || status == 504) {
            return new ResponseStatusException(
                    HttpStatusCode.valueOf(status), safeDetail(exception, "Task Engine 拒绝了请求"), exception);
        }
        if (status == 401 || status == 403) {
            return unavailable("Task Engine 认证失败", exception);
        }
        return unavailable("Task Engine 返回异常响应", exception);
    }

    private ResponseStatusException accessFailure(ResourceAccessException exception) {
        if (causedByConnectionFailure(exception)) {
            return unavailable("无法连接 Task Engine", exception);
        }
        if (causedByTimeout(exception)) {
            return new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Task Engine 响应超时", exception);
        }
        return unavailable("无法连接 Task Engine", exception);
    }

    private ResponseStatusException clientFailure(RestClientException exception, String fallback) {
        if (causedByConnectionFailure(exception)) {
            return unavailable("无法连接 Task Engine", exception);
        }
        if (causedByTimeout(exception)) {
            return new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Task Engine 响应超时", exception);
        }
        if (causedBy(exception, IOException.class)) {
            return unavailable("无法连接 Task Engine", exception);
        }
        return unavailable(fallback, exception);
    }

    private ResponseStatusException unavailable(String detail, Exception exception) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, detail, exception);
    }

    private String safeDetail(RestClientResponseException exception, String fallback) {
        try {
            JsonNode root = objectMapper.readTree(exception.getResponseBodyAsByteArray());
            JsonNode detail = root.get("detail");
            return detail != null && detail.isTextual() && !detail.textValue().isBlank()
                    ? detail.textValue()
                    : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean causedByTimeout(Throwable throwable) {
        return causedBy(throwable, SocketTimeoutException.class)
                || causedBy(throwable, HttpTimeoutException.class);
    }

    private static boolean causedByConnectionFailure(Throwable throwable) {
        if (causedBy(throwable, ConnectException.class)
                || causedBy(throwable, HttpConnectTimeoutException.class)) {
            return true;
        }
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    && current.getMessage() != null
                    && current.getMessage().toLowerCase(Locale.ROOT).contains("connect")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean causedBy(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }
}

package cn.superhuang.data.scalpel.business.operations.service;

import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Component
public class AlertWebhookSender {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final AlertCredentialCipher cipher;
    private final AlertDeliveryService deliveries;
    public AlertWebhookSender(AlertCredentialCipher cipher, AlertDeliveryService deliveries) { this.cipher = cipher; this.deliveries = deliveries; }

    public void send(AlertDeliveryService.DeliveryAttempt attempt) {
        long start = System.nanoTime();
        CompletableFuture<HttpResponse<Void>> pending = null;
        Integer status = null; boolean retryable = true; Long retryAfter = null; String error = "Webhook 网络请求失败";
        try {
            AlertChannelService.validateUrl(attempt.url());
            var request = HttpRequest.newBuilder(URI.create(attempt.url())).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("X-DataScalpel-Delivery-Id", attempt.id().toString());
            if (attempt.bearerCiphertext() != null) request.header("Authorization", "Bearer " + cipher.decrypt(attempt.bearerCiphertext()));
            if (attempt.hmacCiphertext() != null) {
                String timestamp = Long.toString(Instant.now().getEpochSecond());
                request.header("X-DataScalpel-Timestamp", timestamp);
                request.header("X-DataScalpel-Signature", "sha256=" + sign(cipher.decrypt(attempt.hmacCiphertext()), timestamp, attempt.payload()));
            }
            pending = client.sendAsync(request.POST(HttpRequest.BodyPublishers.ofString(attempt.payload(), StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.discarding());
            var response = pending.get(5, TimeUnit.SECONDS);
            status = response.statusCode(); retryable = status == 408 || status == 429 || status >= 500;
            retryAfter = status == 429 ? retryAfter(response.headers().firstValue("Retry-After").orElse(null)) : null;
            error = "Webhook 返回 HTTP " + status;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); error = "Webhook 投递被中断";
        } catch (TimeoutException e) {
            error = "Webhook 请求超时";
        } catch (ExecutionException e) {
            error = "Webhook 网络请求失败";
        } catch (Exception e) {
            // Configuration/authentication errors do not expose URLs, response bodies or secrets.
            retryable = false; error = "Webhook 配置或凭据不可用";
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
        deliveries.complete(attempt.id(), attempt.token(), status, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), retryable, retryAfter, error);
    }
    static String sign(String key, String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }
    static Long retryAfter(String value) {
        if (value == null) return null;
        try { return Math.max(0, Long.parseLong(value)); } catch (NumberFormatException ignored) {
            try { return Math.max(0, Duration.between(Instant.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds()); }
            catch (RuntimeException invalid) { return null; }
        }
    }
}

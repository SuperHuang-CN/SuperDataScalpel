package cn.superhuang.data.scalpel.business.operations.service;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import cn.superhuang.data.scalpel.business.operations.repository.*;
import cn.superhuang.data.scalpel.business.operations.web.request.SaveAlertChannelRequest;
import cn.superhuang.data.scalpel.business.operations.web.response.*;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@Service
public class AlertChannelService {
    private final AlertChannelRepository channels;
    private final AlertDeliveryRepository deliveries;
    private final AlertCredentialCipher cipher;
    private final OperationsAccess access;
    private final SearchEngine search;
    private final ObjectMapper json;
    public AlertChannelService(AlertChannelRepository channels, AlertDeliveryRepository deliveries, AlertCredentialCipher cipher,
                               OperationsAccess access, SearchEngine search, ObjectMapper json) {
        this.channels = channels; this.deliveries = deliveries; this.cipher = cipher; this.access = access; this.search = search; this.json = json;
    }
    @Transactional(readOnly = true)
    public PageResponse<AlertChannelResponse> search(SearchRequest request) {
        access.actor().require("alert.manage");
        var p = search.search(request, AlertChannel.class, channels);
        return new PageResponse<>(p.map(AlertChannelResponse::from).getContent(), p.getTotalElements(), p.getTotalPages(), p.getNumber(), p.getSize());
    }
    @Transactional
    public AlertChannelResponse save(UUID id, SaveAlertChannelRequest r) {
        access.actor().require("alert.manage"); validateUrl(r.url());
        if (r.bearerToken() != null && (r.bearerToken().contains("\r") || r.bearerToken().contains("\n"))) throw bad("令牌不能包含换行");
        if (r.clearBearerToken() && r.bearerToken() != null && !r.bearerToken().isBlank()
                || r.clearHmacSecret() && r.hmacSecret() != null && !r.hmacSecret().isBlank()) throw bad("不能同时替换和清除同一凭据");
        var c = id == null ? new AlertChannel() : channels.lockById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "通知渠道不存在"));
        boolean changed = id == null || !r.url().trim().equals(c.getUrl()) || r.enabled() != c.getEnabled()
                || r.clearBearerToken() || r.clearHmacSecret()
                || r.bearerToken() != null && !r.bearerToken().isBlank() || r.hmacSecret() != null && !r.hmacSecret().isBlank();
        c.setName(r.name().trim()); c.setUrl(r.url().trim()); c.setEnabled(r.enabled());
        if (changed) c.setConfigurationVersion(c.getConfigurationVersion() + 1);
        if (r.clearBearerToken()) c.setBearerCiphertext(null);
        else if (r.bearerToken() != null && !r.bearerToken().isBlank()) c.setBearerCiphertext(cipher.encrypt(r.bearerToken()));
        if (r.clearHmacSecret()) c.setHmacCiphertext(null);
        else if (r.hmacSecret() != null && !r.hmacSecret().isBlank()) c.setHmacCiphertext(cipher.encrypt(r.hmacSecret()));
        return AlertChannelResponse.from(channels.save(c));
    }
    @Transactional
    public UUID test(UUID id) {
        var actor = access.actor(); actor.require("alert.manage");
        var c = channels.lockById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "通知渠道不存在"));
        if (!c.getEnabled()) throw bad("请先启用通知渠道");
        var d = new AlertDelivery(); d.setChannelId(id); d.setChannelVersion(c.getConfigurationVersion()); d.setEventType(AlertEventType.TEST);
        d.setSequence(0); d.setNextAttemptAt(Instant.now()); d.setPayloadJson("{}"); d.setRequestedBy(actor.id()); deliveries.save(d);
        d.setPayloadJson(json.writeValueAsString(new AlertWebhookPayload(1, d.getId(), null, AlertEventType.TEST, 0, null, null,
                "CHANNEL", id, null, null, null, c.getName(), Instant.now(), Instant.now(), "DataScalpel Webhook 测试通知", null, null, null)));
        return d.getId();
    }
    @Transactional
    public AlertChannelResponse setEnabled(UUID id, boolean enabled) {
        access.actor().require("alert.manage");
        var c = channels.lockById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "通知渠道不存在"));
        if (c.getEnabled() != enabled) {
            c.setEnabled(enabled); c.setConfigurationVersion(c.getConfigurationVersion() + 1);
        }
        return AlertChannelResponse.from(c);
    }
    static void validateUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) throw bad("Webhook 需使用没有用户凭据或片段的 HTTP/HTTPS 地址");
        } catch (IllegalArgumentException e) { throw bad("Webhook 地址无效"); }
    }
    private static ResponseStatusException bad(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
}

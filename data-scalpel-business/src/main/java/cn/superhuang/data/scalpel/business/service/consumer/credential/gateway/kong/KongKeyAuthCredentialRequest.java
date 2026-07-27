package cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.kong;

import java.util.List;

record KongKeyAuthCredentialRequest(String key, List<String> tags) {
}

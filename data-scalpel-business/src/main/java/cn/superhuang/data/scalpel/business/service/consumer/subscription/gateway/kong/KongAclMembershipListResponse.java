package cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.kong;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record KongAclMembershipListResponse(List<KongAclMembershipResponse> data, String offset) {
}

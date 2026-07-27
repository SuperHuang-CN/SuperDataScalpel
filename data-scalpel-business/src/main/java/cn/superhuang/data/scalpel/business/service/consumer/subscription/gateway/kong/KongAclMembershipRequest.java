package cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.kong;

import java.util.List;

record KongAclMembershipRequest(String group, List<String> tags) {
}

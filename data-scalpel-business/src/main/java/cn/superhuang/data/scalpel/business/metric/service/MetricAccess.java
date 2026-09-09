package cn.superhuang.data.scalpel.business.metric.service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
final class MetricAccess {
 private MetricAccess() {}
 static boolean has(String permission) { var auth=SecurityContextHolder.getContext().getAuthentication();return auth!=null && auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals(permission)); }
 static void require(String permission) { if(!has(permission))throw new AccessDeniedException("缺少权限："+permission); }
}

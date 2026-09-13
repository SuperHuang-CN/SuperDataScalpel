package cn.superhuang.data.scalpel.admin.security.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "当前 JWT 中携带的登录身份、角色和权限快照。")
public record CurrentUserResponse(
        @Schema(description = "当前登录用户名，对应 JWT subject。")
        String username,
        @Schema(description = "签发 JWT 时写入的角色编码；当前用户只绑定一个角色，通常返回单项列表。")
        List<String> roles,
        @Schema(description = "签发 JWT 时该角色拥有的有效权限编码；角色授权变化后需重新登录才会更新此快照。")
        List<String> permissions,
        @Schema(description = "系统用户 UUID；旧 JWT 可能为空，DSH 使用前需重新登录。")
        String userId
) {
}

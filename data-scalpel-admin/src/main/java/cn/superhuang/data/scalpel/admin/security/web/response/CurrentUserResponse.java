package cn.superhuang.data.scalpel.admin.security.web.response;

import java.util.List;

public record CurrentUserResponse(String username, List<String> roles, List<String> permissions) {
}

package cn.superhuang.data.scalpel.admin.security.web.resource;

import cn.superhuang.data.scalpel.admin.security.JwtTokenService;
import cn.superhuang.data.scalpel.admin.security.web.request.LoginRequest;
import cn.superhuang.data.scalpel.admin.security.web.response.CurrentUserResponse;
import cn.superhuang.data.scalpel.admin.security.web.response.LoginResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationResource {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    public AuthenticationResource(AuthenticationManager authenticationManager, JwtTokenService jwtTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    @Operation(summary = "登录后台系统", description = "校验启用用户的用户名和密码，成功后签发包含当前角色与权限快照的 JWT；不会创建服务端登录会话。")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        JwtTokenService.IssuedToken issuedToken = jwtTokenService.issue(authentication);
        return new LoginResponse(issuedToken.value(), "Bearer", issuedToken.expiresAt());
    }

    @GetMapping("/me")
    @Operation(summary = "查询当前登录身份", description = "读取当前 JWT 中的用户名、角色和权限快照，用于恢复前端登录态；不会重新查询数据库中的最新授权。")
    CurrentUserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        return new CurrentUserResponse(jwt.getSubject(), jwt.getClaimAsStringList("roles"), jwt.getClaimAsStringList("permissions"), jwt.getClaimAsString("userId"));
    }
}

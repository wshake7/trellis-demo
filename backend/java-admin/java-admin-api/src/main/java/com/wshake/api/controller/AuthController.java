package com.wshake.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.wshake.api.dto.LoginRequest;
import com.wshake.api.vo.LoginResponse;
import com.wshake.api.vo.UserInfoVO;
import com.wshake.common.exception.AuthException;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.Result;
import com.wshake.common.result.ResultCode;
import com.wshake.service.entity.SysUser;
import com.wshake.service.user.AuthService;
import com.wshake.service.user.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴权 Controller。
 *
 * @author wshake
 */
@Slf4j
@Tag(name = "鉴权", description = "登录、登出、当前用户信息")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SysUserService sysUserService;

    /**
     * 登录。
     */
    @PostMapping("/login")
    @Operation(
            summary = "账号密码登录",
            description = "Sa-Token 签发;前端把 token 写入后续请求 satoken header 或 Authorization: Bearer <token>")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "登录成功"),
                @ApiResponse(
                        responseCode = "400",
                        description = "参数错误(code=1001)",
                        content = @Content(schema = @Schema(implementation = Result.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "凭证错误(code=2002)",
                        content = @Content(schema = @Schema(implementation = Result.class)))
            })
    public Result<LoginResponse> login(
            @Parameter(description = "登录请求体", required = true) @Valid @RequestBody LoginRequest req) {
        SysUser user = authService.login(req.getUsername(), req.getPassword());
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();
        return Result.ok(new LoginResponse(token, user.getId(), user.getUsername(), user.getNickname()));
    }

    /**
     * 登出。
     */
    @PostMapping("/logout")
    @Operation(summary = "登出", description = "注销当前 Sa-Token")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "登出成功"),
                @ApiResponse(
                        responseCode = "401",
                        description = "未登录(code=2001)",
                        content = @Content(schema = @Schema(implementation = Result.class)))
            })
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.ok();
    }

    /**
     * 当前用户信息。
     */
    @GetMapping("/info")
    @Operation(summary = "当前登录用户信息", description = "读取当前 Sa-Token 对应用户")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "成功"),
                @ApiResponse(
                        responseCode = "401",
                        description = "未登录(code=2001)",
                        content = @Content(schema = @Schema(implementation = Result.class)))
            })
    public Result<UserInfoVO> info() {
        if (!StpUtil.isLogin()) {
            throw AuthException.notLogin();
        }
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserService.findById(userId);
        if (user == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "用户不存在");
        }
        return Result.ok(new UserInfoVO(
                user.getId(), user.getUsername(), user.getNickname(), user.getStatus(), user.getCreateTime()));
    }
}

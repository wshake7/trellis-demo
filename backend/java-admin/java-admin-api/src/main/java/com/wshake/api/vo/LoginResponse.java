package com.wshake.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应 VO。
 *
 * @author wshake
 */
@Data
@AllArgsConstructor
@Schema(description = "登录成功响应")
public class LoginResponse {

    /** Sa-Token token 值（前端写入 {@code satoken} header） */
    @Schema(
            description = "Sa-Token token 值;前端写入请求头 satoken 或 Authorization: Bearer <token>",
            example = "9c8a7b6e-1234-5678-90ab-cdef12345678")
    private String token;

    @Schema(description = "用户 ID", example = "1")
    private Long userId;

    @Schema(description = "用户名", example = "admin")
    private String username;

    @Schema(description = "昵称", example = "管理员")
    private String nickname;
}

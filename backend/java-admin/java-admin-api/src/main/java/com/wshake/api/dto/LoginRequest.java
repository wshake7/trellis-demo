package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求 DTO。
 *
 * @author wshake
 */
@Data
@Schema(description = "账号密码登录请求")
public class LoginRequest {

    @Schema(
            description = "用户名",
            example = "admin",
            minLength = 3,
            maxLength = 64,
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "不能为空")
    @Size(min = 3, max = 64, message = "长度 3-64")
    private String username;

    @Schema(
            description = "密码(明文,仅登录时使用)",
            example = "admin123",
            minLength = 6,
            maxLength = 64,
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "不能为空")
    @Size(min = 6, max = 64, message = "长度 6-64")
    private String password;
}

package com.wshake.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用户信息 VO（{@code /api/v1/auth/info} 响应）。
 *
 * @author wshake
 */
@Data
@AllArgsConstructor
@Schema(description = "当前登录用户信息")
public class UserInfoVO {

    @Schema(description = "用户 ID", example = "1")
    private Long id;

    @Schema(description = "用户名", example = "admin")
    private String username;

    @Schema(description = "昵称", example = "管理员")
    private String nickname;

    @Schema(
            description = "状态:1=启用,0=禁用",
            example = "1",
            allowableValues = {"0", "1"})
    private Integer status;

    @Schema(description = "创建时间", example = "2026-06-14 12:00:00")
    private LocalDateTime createTime;
}

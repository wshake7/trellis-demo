package com.wshake.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Knife4j 文档元数据配置。
 *
 * <p>说明:
 * <ul>
 *   <li>title / version / contact 通过 Java bean 注入,yml 不重复(避免 knife4j 4.x yml vs bean 谁覆盖谁的歧义)</li>
 *   <li>{@code bearerAuth} securityScheme 与 Sa-Token 默认 header {@code satoken} 对齐,Knife4j 调试页面输入框自动落到此 header</li>
 *   <li>{@code @RestControllerAdvice} 的方法要显式加 {@code @Operation} + {@code @ApiResponse},否则不会收录到 OpenAPI</li>
 * </ul>
 *
 * @author wshake
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("java-admin API 文档")
                        .description("Treasure 后台管理系统接口")
                        .version("1.0.0")
                        .contact(new Contact().name("wshake")))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .in(SecurityScheme.In.HEADER)
                                        .name("satoken")))
                .externalDocs(
                        new ExternalDocumentation().description("Knife4j 文档页").url("/doc.html"));
    }
}

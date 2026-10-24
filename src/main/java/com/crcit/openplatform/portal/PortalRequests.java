package com.crcit.openplatform.portal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 门户 API 的请求体 DTO。
 */
public final class PortalRequests {

    private PortalRequests() {
    }

    public record LoginRequest(
            @NotBlank(message = "请输入邮箱") String email,
            @NotBlank(message = "请输入密码") String password
    ) {
    }

    public record CreateAppRequest(
            @NotBlank(message = "请输入应用名称")
            @Size(max = 60, message = "应用名称不能超过 60 个字符")
            String name,
            @Size(max = 200, message = "应用描述不能超过 200 个字符")
            String description,
            @NotBlank(message = "请选择认证方式") String authMode
    ) {
    }

    public record CreateAccessRequest(
            @NotBlank(message = "请选择应用") String appId,
            @NotBlank(message = "请选择 API 产品") String productCode,
            @NotBlank(message = "请填写接入场景")
            @Size(max = 500, message = "接入场景不能超过 500 个字符")
            String useCase
    ) {
    }

    public record DecisionRequest(
            @NotBlank(message = "请选择审批结果") String decision,
            @Size(max = 500, message = "审批意见不能超过 500 个字符") String comment
    ) {
    }
}

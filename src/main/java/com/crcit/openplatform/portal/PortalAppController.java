package com.crcit.openplatform.portal;

import com.crcit.openplatform.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 应用管理接口。 */
@RestController
@RequestMapping("/portal/api/apps")
public class PortalAppController {

    private final PortalAppService appService;

    public PortalAppController(PortalAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    public ApiResponse<List<PortalViews.AppView>> list(HttpServletRequest request) {
        return ApiResponse.success(appService.list(
                PortalControllerSupport.principal(request)));
    }

    @PostMapping
    public ApiResponse<PortalViews.AppCreatedView> create(
            HttpServletRequest request,
            @Valid @RequestBody PortalRequests.CreateAppRequest body) {
        return ApiResponse.success(appService.create(
                PortalControllerSupport.principal(request),
                body.name(), body.description(), body.authMode()));
    }

    @GetMapping("/{appId}/credentials")
    public ApiResponse<PortalViews.CredentialsView> credentials(
            HttpServletRequest request, @PathVariable String appId) {
        return ApiResponse.success(appService.credentials(
                PortalControllerSupport.principal(request), appId));
    }

    @PostMapping("/{appId}/rotate-secret")
    public ApiResponse<PortalViews.CredentialsView> rotateSecret(
            HttpServletRequest request, @PathVariable String appId) {
        return ApiResponse.success(appService.rotateSecret(
                PortalControllerSupport.principal(request), appId));
    }
}

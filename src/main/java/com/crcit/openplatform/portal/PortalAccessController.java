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

/**
 * 接入申请接口。
 *
 * <p>开发者可以查看/提交本企业申请；管理员可以查看全部并审批。</p>
 */
@RestController
@RequestMapping("/portal/api/access-requests")
public class PortalAccessController {

    private final PortalAccessService accessService;

    public PortalAccessController(PortalAccessService accessService) {
        this.accessService = accessService;
    }

    @GetMapping
    public ApiResponse<List<PortalViews.AccessRequestView>> list(HttpServletRequest request) {
        return ApiResponse.success(accessService.list(
                PortalControllerSupport.principal(request)));
    }

    @PostMapping
    public ApiResponse<PortalViews.AccessRequestView> create(
            HttpServletRequest request,
            @Valid @RequestBody PortalRequests.CreateAccessRequest body) {
        return ApiResponse.success(accessService.create(
                PortalControllerSupport.principal(request),
                body.appId(), body.productCode(), body.useCase()));
    }

    @PostMapping("/{requestId}/decision")
    public ApiResponse<PortalViews.AccessRequestView> decide(
            HttpServletRequest request,
            @PathVariable String requestId,
            @Valid @RequestBody PortalRequests.DecisionRequest body) {
        return ApiResponse.success(accessService.decide(
                PortalControllerSupport.principal(request),
                requestId, body.decision(), body.comment()));
    }
}

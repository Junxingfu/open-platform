package com.crcit.openplatform.portal;

import com.crcit.openplatform.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 调用统计接口。 */
@RestController
@RequestMapping("/portal/api")
public class PortalUsageController {

    private final PortalUsageService usageService;

    public PortalUsageController(PortalUsageService usageService) {
        this.usageService = usageService;
    }

    @GetMapping("/usage")
    public ApiResponse<PortalViews.UsageView> usage(
            HttpServletRequest request,
            @RequestParam(defaultValue = "7") int days) {
        PortalPrincipal principal = PortalControllerSupport.principal(request);
        return ApiResponse.success(usageService.summary(
                principal.enterpriseId(), days, principal.isAdmin()));
    }
}

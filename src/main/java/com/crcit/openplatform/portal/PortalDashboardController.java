package com.crcit.openplatform.portal;

import com.crcit.openplatform.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 门户首页数据。 */
@RestController
@RequestMapping("/portal/api")
public class PortalDashboardController {

    private final PortalDashboardService dashboardService;

    public PortalDashboardController(PortalDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<PortalViews.DashboardView> dashboard(HttpServletRequest request) {
        return ApiResponse.success(dashboardService.dashboard(
                PortalControllerSupport.principal(request)));
    }
}

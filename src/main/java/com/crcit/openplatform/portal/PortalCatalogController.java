package com.crcit.openplatform.portal;

import com.crcit.openplatform.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API 目录与产品接口。
 *
 * <p>目录数据对登录用户公开（不区分企业）；产品开通状态按当前企业计算。</p>
 */
@RestController
@RequestMapping("/portal/api")
public class PortalCatalogController {

    private final PortalCatalogService catalogService;

    public PortalCatalogController(PortalCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/apis")
    public ApiResponse<List<PortalViews.ApiSummary>> apis(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String method) {
        return ApiResponse.success(catalogService.search(keyword, product, method));
    }

    @GetMapping("/apis/{apiId}")
    public ApiResponse<PortalViews.ApiDetail> detail(@PathVariable String apiId) {
        return ApiResponse.success(catalogService.detail(apiId));
    }

    @GetMapping("/products")
    public ApiResponse<List<PortalViews.ProductView>> products(HttpServletRequest request) {
        PortalPrincipal principal = PortalControllerSupport.principal(request);
        return ApiResponse.success(catalogService.products(principal.enterpriseId()));
    }
}

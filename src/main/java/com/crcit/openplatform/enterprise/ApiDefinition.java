package com.crcit.openplatform.enterprise;

/**
 * 平台登记的一个可调用 API。
 *
 * @param apiId          API 唯一标识，例如 enterprise.verify
 * @param method         HTTP 方法，必须大写
 * @param path           不含 context-path 的路径，例如 /open/v1/enterprise/verify
 * @param productCode    所属 API 产品编码
 * @param requiredScope  调用该 API 必须具备的权限点
 */
public record ApiDefinition(
        String apiId,
        String method,
        String path,
        String productCode,
        String requiredScope
) {
}

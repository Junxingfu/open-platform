package com.crcit.openplatform.enterprise;

import java.util.Set;

/**
 * API 产品（一组 API + 一组权限点），企业按产品订阅。
 *
 * <p>授权时先确认 API 属于哪个产品，再检查企业是否订阅了该产品，最后检查 scope。
 * 这样“按产品授权”比“按单个 API 授权”更易维护。</p>
 *
 * @param code   产品编码，例如 enterprise-basic
 * @param name   产品名称，仅用于展示
 * @param scopes 产品包含的权限点
 * @param apiIds 产品包含的 API ID
 * @param active 产品是否上线；下线后其下所有 API 都不可调用
 */
public record ApiProduct(
        String code,
        String name,
        Set<String> scopes,
        Set<String> apiIds,
        boolean active
) {
}

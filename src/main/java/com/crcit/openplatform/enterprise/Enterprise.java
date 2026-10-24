package com.crcit.openplatform.enterprise;

/**
 * 接入开放平台的企业。
 *
 * @param id     企业 ID
 * @param name   企业名称
 * @param active 是否启用；停用后该企业下所有应用都无法调用 API
 */
public record Enterprise(String id, String name, boolean active) {
}

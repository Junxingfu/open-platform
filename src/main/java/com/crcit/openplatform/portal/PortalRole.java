package com.crcit.openplatform.portal;

/**
 * 门户用户角色。
 *
 * <p>MVP 只区分两类角色：</p>
 * <ul>
 *   <li>{@link #DEVELOPER}：企业开发者，管理本企业应用、申请 API、查看调用量；</li>
 *   <li>{@link #ADMIN}：平台运营，审批接入申请、查看全部企业申请。</li>
 * </ul>
 */
public enum PortalRole {
    DEVELOPER,
    ADMIN
}

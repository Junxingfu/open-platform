package com.crcit.openplatform.portal;

import java.time.Instant;

/**
 * API 产品接入申请。
 *
 * <p>企业开发者在门户选择“应用 + API 产品”提交申请，平台运营审批通过后，
 * {@code MutableAppRegistry.grantProduct} 会为应用创建订阅，应用才真正获得调用资格。</p>
 */
public record AccessRequest(
        String id,
        String enterpriseId,
        String appId,
        String productCode,
        String useCase,
        RequestStatus status,
        Instant createdAt,
        Instant decidedAt,
        String reviewer,
        String reviewComment
) {
    public AccessRequest decide(RequestStatus nextStatus, String reviewer, String comment) {
        return new AccessRequest(id, enterpriseId, appId, productCode, useCase,
                nextStatus, createdAt, Instant.now(), reviewer, comment);
    }
}

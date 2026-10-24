package com.crcit.openplatform.portal;

import java.time.Instant;
import java.util.List;

/**
 * 门户 API 的响应 DTO 集合。
 *
 * <p>统一放在一个文件里，便于前端对照字段；这些对象只用于 HTTP 响应，不参与领域逻辑。
 * 所有响应外层仍然包一层 {@code ApiResponse}（code/message/requestId/data）。</p>
 */
public final class PortalViews {

    private PortalViews() {
    }

    /** 当前登录用户。 */
    public record UserView(
            String id,
            String name,
            String email,
            String role,
            String enterpriseId,
            String enterpriseName
    ) {
    }

    /** 登录响应。 */
    public record LoginResult(String token, UserView user) {
    }

    /** 应用列表项；完整密钥不在列表中返回。 */
    public record AppView(
            String appId,
            String name,
            String description,
            String enterpriseId,
            String enterpriseName,
            String authMode,
            boolean active,
            Instant createdAt,
            String secretMasked,
            List<String> productCodes,
            List<String> productNames
    ) {
    }

    /** 创建应用后的一次性完整密钥返回。 */
    public record AppCreatedView(AppView app, String appSecret) {
    }

    /** 查看/轮换密钥的返回。 */
    public record CredentialsView(String appId, String appSecret, String authMode) {
    }

    /** API 产品（按企业视角附带已开通应用数、待审批数）。 */
    public record ProductView(
            String code,
            String name,
            String description,
            List<String> scopes,
            int apiCount,
            boolean active,
            int subscribedAppCount,
            int pendingRequestCount
    ) {
    }

    /** API 目录列表项。 */
    public record ApiSummary(
            String apiId,
            String name,
            String method,
            String path,
            String summary,
            String productCode,
            String productName,
            String requiredScope,
            String category,
            String version,
            List<String> authModes,
            String status
    ) {
    }

    public record HeaderDoc(String name, boolean required, String description, String example) {
    }

    public record ParamDoc(
            String name,
            String location,
            String type,
            boolean required,
            String description,
            String example
    ) {
    }

    public record ErrorDoc(String code, String message, String description) {
    }

    /** API 详情（目录摘要 + 文档）。 */
    public record ApiDetail(
            ApiSummary summary,
            String description,
            List<String> authModes,
            String rateLimit,
            List<HeaderDoc> requestHeaders,
            List<ParamDoc> requestParams,
            String requestExample,
            String responseExample,
            List<ErrorDoc> errorCodes,
            List<ApiSummary> relatedApis
    ) {
    }

    /** 接入申请。 */
    public record AccessRequestView(
            String id,
            String enterpriseId,
            String enterpriseName,
            String appId,
            String appName,
            String productCode,
            String productName,
            String useCase,
            String status,
            Instant createdAt,
            Instant decidedAt,
            String reviewer,
            String reviewComment
    ) {
    }

    /** 一次开放 API 调用的展示信息。 */
    public record ActivityView(
            String id,
            String appId,
            String appName,
            String apiId,
            String apiName,
            int status,
            long costMs,
            Instant at
    ) {
    }

    public record UsagePoint(String date, long calls, long errors) {
    }

    public record ApiUsage(
            String apiId,
            String name,
            long calls,
            long errors,
            long avgLatencyMs
    ) {
    }

    /** 调用统计。 */
    public record UsageView(
            long totalCalls,
            double successRate,
            long avgLatencyMs,
            List<UsagePoint> series,
            List<ApiUsage> byApi,
            List<ActivityView> recent
    ) {
    }

    public record OnboardingStep(
            String key,
            String title,
            String description,
            boolean done,
            String action
    ) {
    }

    public record Announcement(String id, String title, String content, String date, String tag) {
    }

    public record Stats(
            int appCount,
            int approvedProductCount,
            int pendingRequestCount,
            long calls7d,
            double successRate,
            long avgLatencyMs,
            int apiCount,
            int pendingApprovalCount
    ) {
    }

    /** 门户首页。 */
    public record DashboardView(
            UserView user,
            Stats stats,
            List<OnboardingStep> onboarding,
            List<ActivityView> recentActivity,
            List<Announcement> announcements
    ) {
    }
}

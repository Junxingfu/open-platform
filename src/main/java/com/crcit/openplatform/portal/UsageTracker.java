package com.crcit.openplatform.portal;

import com.crcit.openplatform.auth.AuthContext;
import com.crcit.openplatform.enterprise.ApiDefinition;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 开放 API 调用量的内存采集器。
 *
 * <p>{@code AuditFilter} 在每次开放 API 请求结束后调用 {@link #record}，门户的“调用统计”
 * 直接读取这里的数据。MVP 只保留最近 5000 条，重启清零；生产环境应写入时序库/日志平台。</p>
 *
 * <p>注意：只记录已经通过认证的开放 API 调用（{@code AuthContext} 非空），不记录门户自身请求。</p>
 */
@Component
public class UsageTracker {

    private static final int MAX_RECORDS = 5000;

    private final Deque<UsageRecord> records = new ConcurrentLinkedDeque<>();

    public void record(AuthContext context, ApiDefinition api, int status, long costMs) {
        if (context == null || api == null) {
            return;
        }
        records.addFirst(new UsageRecord(
                "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                context.appId(),
                context.enterpriseId(),
                api.apiId(),
                status,
                costMs,
                Instant.now()));
        while (records.size() > MAX_RECORDS) {
            records.pollLast();
        }
    }

    public List<UsageRecord> snapshot() {
        return List.copyOf(records);
    }

    public record UsageRecord(
            String id,
            String appId,
            String enterpriseId,
            String apiId,
            int status,
            long costMs,
            Instant at
    ) {
        public boolean success() {
            return status >= 200 && status < 400;
        }
    }
}

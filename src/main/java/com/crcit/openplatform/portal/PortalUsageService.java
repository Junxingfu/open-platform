package com.crcit.openplatform.portal;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门户调用统计服务。
 *
 * <p>数据来自 {@link UsageTracker}（内存，最多 5000 条）。按企业维度过滤，生成：
 * 总量、成功率、平均耗时、按天趋势、按 API 汇总、最近调用。</p>
 *
 * <p>管理员可以看全平台数据（{@code all=true}）；企业开发者只能看本企业。</p>
 */
@Service
public class PortalUsageService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final UsageTracker usageTracker;
    private final PortalCatalogService catalogService;
    private final PortalStore portalStore;

    public PortalUsageService(UsageTracker usageTracker,
                              PortalCatalogService catalogService,
                              PortalStore portalStore) {
        this.usageTracker = usageTracker;
        this.catalogService = catalogService;
        this.portalStore = portalStore;
    }

    public PortalViews.UsageView summary(String enterpriseId, int days, boolean all) {
        int safeDays = Math.max(1, Math.min(days, 30));
        Instant cutoff = Instant.now().minus(safeDays, ChronoUnit.DAYS);

        List<UsageTracker.UsageRecord> scoped = usageTracker.snapshot().stream()
                .filter(record -> record.at().isAfter(cutoff))
                .filter(record -> all || record.enterpriseId().equals(enterpriseId))
                .toList();

        long total = scoped.size();
        long errors = scoped.stream().filter(record -> !record.success()).count();
        long avgLatency = total == 0 ? 0
                : Math.round(scoped.stream().mapToLong(UsageTracker.UsageRecord::costMs).average().orElse(0));
        double successRate = total == 0 ? 0.0 : (total - errors) * 100.0 / total;

        return new PortalViews.UsageView(
                total,
                round(successRate),
                avgLatency,
                buildSeries(scoped, safeDays),
                buildByApi(scoped),
                recent(scoped, 10));
    }

    private List<PortalViews.UsagePoint> buildSeries(List<UsageTracker.UsageRecord> records, int days) {
        ZoneId zone = ZoneId.systemDefault();
        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(zone);
        for (int i = days - 1; i >= 0; i--) {
            byDay.put(today.minusDays(i), new long[]{0, 0});
        }
        for (UsageTracker.UsageRecord record : records) {
            LocalDate day = record.at().atZone(zone).toLocalDate();
            long[] values = byDay.get(day);
            if (values != null) {
                values[0]++;
                if (!record.success()) {
                    values[1]++;
                }
            }
        }
        List<PortalViews.UsagePoint> result = new ArrayList<>();
        byDay.forEach((day, values) ->
                result.add(new PortalViews.UsagePoint(day.format(DAY_FORMAT), values[0], values[1])));
        return result;
    }

    private List<PortalViews.ApiUsage> buildByApi(List<UsageTracker.UsageRecord> records) {
        Map<String, long[]> aggregate = new LinkedHashMap<>();
        for (UsageTracker.UsageRecord record : records) {
            long[] values = aggregate.computeIfAbsent(record.apiId(), key -> new long[]{0, 0, 0});
            values[0]++;
            if (!record.success()) {
                values[1]++;
            }
            values[2] += record.costMs();
        }
        return aggregate.entrySet().stream()
                .map(entry -> new PortalViews.ApiUsage(
                        entry.getKey(),
                        catalogService.apiName(entry.getKey()),
                        entry.getValue()[0],
                        entry.getValue()[1],
                        entry.getValue()[0] == 0 ? 0 : entry.getValue()[2] / entry.getValue()[0]))
                .sorted(Comparator.comparingLong(PortalViews.ApiUsage::calls).reversed())
                .toList();
    }

    private List<PortalViews.ActivityView> recent(List<UsageTracker.UsageRecord> records, int limit) {
        return records.stream()
                .sorted(Comparator.comparing(UsageTracker.UsageRecord::at).reversed())
                .limit(limit)
                .map(this::toActivity)
                .toList();
    }

    public PortalViews.ActivityView toActivity(UsageTracker.UsageRecord record) {
        String appName = portalStore.findApp(record.appId())
                .map(PortalApp::name)
                .orElse(record.appId());
        return new PortalViews.ActivityView(
                record.id(),
                record.appId(),
                appName,
                record.apiId(),
                catalogService.apiName(record.apiId()),
                record.status(),
                record.costMs(),
                record.at());
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}

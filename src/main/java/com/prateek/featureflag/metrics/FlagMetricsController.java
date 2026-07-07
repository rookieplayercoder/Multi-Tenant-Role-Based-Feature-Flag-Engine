package com.prateek.featureflag.metrics;

import com.prateek.featureflag.flag.FeatureFlag;
import com.prateek.featureflag.flag.FeatureFlagService;
import com.prateek.featureflag.organization.MemberRole;
import com.prateek.featureflag.organization.OrganizationAuthorizationService;
import com.prateek.featureflag.security.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read access to a {@link FeatureFlag}'s evaluation metrics — Module 11.
 * Nests under {@code /api/flags/{flagId}/metrics}, matching how
 * {@code FlagVersionController} scopes its own flag-level endpoints;
 * kept as its own controller for the same reason that one is: a distinct
 * concern (aggregated read-only analytics) from
 * {@code FeatureFlagController}'s current-state CRUD.
 * <p>
 * Same role gate as every other flag-scoped controller in this project:
 * {@code OWNER}/{@code ADMIN}/{@code EDITOR}, no {@code VIEWER} access.
 * <p>
 * {@code from}/{@code to} default to the trailing 30 days when omitted —
 * a reasonable default for a metrics dashboard rather than requiring both
 * on every call.
 */
@RestController
public class FlagMetricsController {

    private static final int DEFAULT_RANGE_DAYS = 30;

    private final FeatureFlagService featureFlagService;
    private final FlagEvaluationMetricService flagEvaluationMetricService;
    private final OrganizationAuthorizationService organizationAuthorizationService;

    public FlagMetricsController(FeatureFlagService featureFlagService,
                                  FlagEvaluationMetricService flagEvaluationMetricService,
                                  OrganizationAuthorizationService organizationAuthorizationService) {
        this.featureFlagService = featureFlagService;
        this.flagEvaluationMetricService = flagEvaluationMetricService;
        this.organizationAuthorizationService = organizationAuthorizationService;
    }

    @GetMapping("/api/flags/{flagId}/metrics")
    public ResponseEntity<MetricsResponse> metrics(
            @PathVariable UUID flagId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            FeatureFlag flag = featureFlagService.getActiveById(flagId);
            authorize(flag.getEnvironment().getProject().getOrganization().getId(), principal);

            LocalDate rangeTo = to != null ? to : LocalDate.now();
            LocalDate rangeFrom = from != null ? from : rangeTo.minusDays(DEFAULT_RANGE_DAYS - 1L);

            List<FlagEvaluationMetric> daily = flagEvaluationMetricService.getDailyBreakdown(flagId, rangeFrom, rangeTo);
            MetricsSummary summary = flagEvaluationMetricService.summarize(daily);

            return ResponseEntity.ok(MetricsResponse.from(rangeFrom, rangeTo, daily, summary));
        } catch (EntityNotFoundException flagNotFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /** Shared role gate — see class Javadoc. */
    private void authorize(UUID organizationId, CustomUserDetails principal) {
        organizationAuthorizationService.requireRole(
                organizationId, principal.getUser().getId(),
                MemberRole.OWNER, MemberRole.ADMIN, MemberRole.EDITOR);
    }

    public record DailyCount(LocalDate date, boolean result, String reason, long count) {
        static DailyCount from(FlagEvaluationMetric metric) {
            return new DailyCount(metric.getEvaluationDate(), metric.isResult(), metric.getReason(), metric.getCount());
        }
    }

    public record MetricsResponse(LocalDate from, LocalDate to, long total, long trueCount, long falseCount,
                                   Map<String, Long> byReason, List<DailyCount> daily) {
        static MetricsResponse from(LocalDate from, LocalDate to, List<FlagEvaluationMetric> daily, MetricsSummary summary) {
            return new MetricsResponse(
                    from, to, summary.total(), summary.trueCount(), summary.falseCount(), summary.byReason(),
                    daily.stream().map(DailyCount::from).toList());
        }
    }
}

package com.techeazyhq.cm.bil.app;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

import com.techeazyhq.cm.bil.dom.UsgAlert;
import com.techeazyhq.cm.bil.dom.UsgAlertRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;

/** Reads usage alerts (ADR-060): a fiduciary its own, platform staff everyone's for a month. */
public class UsgAlertQryHandler {

    private static final int OWN_LIMIT = 24;
    private static final Pattern PERIOD = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private final UsgAlertRepoPort alerts;
    private final Clock clock;

    public UsgAlertQryHandler(UsgAlertRepoPort alerts, Clock clock) {
        this.alerts = alerts;
        this.clock = clock;
    }

    /** The fiduciary's own alerts, newest first. */
    public Flux<UsgAlert> mine(BilCaller caller) {
        if (!caller.isDf()) {
            return Flux.error(new DomRuleViolation(403, "not-a-fiduciary", "Only a fiduciary has usage alerts"));
        }
        return alerts.findByDf(caller.dfId(), OWN_LIMIT);
    }

    /** Every fiduciary's alerts for {@code period} ({@code yyyy-MM}, this month when null), for platform staff. */
    public Flux<UsgAlert> forPeriod(BilCaller caller, String period) {
        if (!caller.staff()) {
            return Flux.error(new DomRuleViolation(403, "not-platform-staff", "Only platform staff see every alert"));
        }
        if (period != null && !PERIOD.matcher(period).matches()) {
            return Flux.error(new DomRuleViolation(400, "invalid-period", "period is a month like 2026-11"));
        }
        return alerts.findByPeriod(period != null ? period : YearMonth.now(clock.withZone(ZoneOffset.UTC)).toString());
    }
}

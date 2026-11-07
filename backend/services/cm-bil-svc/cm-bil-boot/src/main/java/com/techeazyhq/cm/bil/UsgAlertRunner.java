package com.techeazyhq.cm.bil;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.techeazyhq.cm.bil.app.UsgAlertSweep;

/**
 * Runs the usage alert sweep (ADR-060) every minute. One pass at a time per replica, as the outbox relays (V-03); two
 * replicas sweeping together are safe, since the alert's primary key lets only one raise it.
 */
@Component
public class UsgAlertRunner {

    private static final Logger LOG = LoggerFactory.getLogger(UsgAlertRunner.class);

    private final UsgAlertSweep sweep;
    private final AtomicBoolean passRunning = new AtomicBoolean();

    public UsgAlertRunner(UsgAlertSweep sweep) {
        this.sweep = sweep;
    }

    @Scheduled(initialDelayString = "${cm.bil.usage-alerts.initial-delay-ms:30000}",
            fixedDelayString = "${cm.bil.usage-alerts.fixed-delay-ms:60000}")
    public void run() {
        if (!passRunning.compareAndSet(false, true)) {
            return;
        }
        sweep.run().doFinally(signal -> passRunning.set(false))
                .subscribe(raised -> {
                    if (raised > 0) {
                        LOG.info("Raised {} usage alert(s)", raised);
                    }
                }, e -> LOG.warn("Usage alert sweep failed", e));
    }
}

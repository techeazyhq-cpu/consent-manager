package com.techeazyhq.cm.cnsqry.dom;

import java.time.Instant;

/** One tile per DF a DP has ever interacted with (plan §8, §17 — the user's tile-dashboard request). */
public record DpDashboardRow(String dfId, long activeCnt, long pendingCnt, Instant lastUpdatedAt) {
}

package com.techeazyhq.cm.webkit;

/**
 * Thrown by a Cmd/Qry handler when a domain-level {@code Spec} (Specification pattern)
 * rejects a request — e.g. {@code DfSubEligiblePolicy}, {@code CnsReqDecidablePolicy}.
 * {@link DomProblemDetailHandler} maps this to the {@code problem+json} response the
 * plan specifies for each rule (§7b: {@code subscription-inactive} -> 402,
 * {@code quota-exceeded} -> 429, etc.).
 */
public class DomRuleViolation extends RuntimeException {

    private final int httpStatus;
    private final String problemType;

    public DomRuleViolation(int httpStatus, String problemType, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.problemType = problemType;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String problemType() {
        return problemType;
    }
}

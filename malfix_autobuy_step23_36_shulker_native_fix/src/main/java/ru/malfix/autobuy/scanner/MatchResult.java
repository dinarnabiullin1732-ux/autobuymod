package ru.malfix.autobuy.scanner;

public final class MatchResult {

    private final boolean matched;
    private final TargetItem target;
    private final String reason;

    private MatchResult(boolean matched, TargetItem target, String reason) {
        this.matched = matched;
        this.target = target;
        this.reason = reason == null ? "" : reason;
    }

    public static MatchResult no(String reason) {
        return new MatchResult(false, null, reason);
    }

    public static MatchResult yes(TargetItem target, String reason) {
        return new MatchResult(true, target, reason);
    }

    public boolean isMatched() {
        return matched;
    }

    public TargetItem getTarget() {
        return target;
    }

    public String getReason() {
        return reason;
    }
}

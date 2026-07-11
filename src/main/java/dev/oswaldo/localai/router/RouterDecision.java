package dev.oswaldo.localai.router;

public record RouterDecision(Decision decision, String reason) {
    public enum Decision {
        WEB,
        LOCAL
    }
}

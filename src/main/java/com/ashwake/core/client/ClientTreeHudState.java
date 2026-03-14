package com.ashwake.core.client;

public final class ClientTreeHudState {
    private static TreeHudData current;

    private ClientTreeHudState() {
    }

    public static void show(String descriptionKey, int currentHits, int maxHits, int displayTicks) {
        long now = System.nanoTime();
        if (current != null && current.matches(descriptionKey, maxHits)) {
            long lastHitAt = current.currentHits() != currentHits ? now : current.lastHitAtNanos();
            current = new TreeHudData(
                    descriptionKey,
                    currentHits,
                    maxHits,
                    displayTicks,
                    displayTicks,
                    current.shownAtNanos(),
                    lastHitAt
            );
            return;
        }

        current = new TreeHudData(descriptionKey, currentHits, maxHits, displayTicks, displayTicks, now, now);
    }

    public static void clear() {
        current = null;
    }

    public static void tick() {
        if (current == null) {
            return;
        }

        int nextTicks = current.ticksRemaining() - 1;
        current = nextTicks > 0
                ? new TreeHudData(
                current.descriptionKey(),
                current.currentHits(),
                current.maxHits(),
                nextTicks,
                current.totalTicks(),
                current.shownAtNanos(),
                current.lastHitAtNanos()
        )
                : null;
    }

    public static TreeHudData get() {
        return current;
    }

    public record TreeHudData(
            String descriptionKey,
            int currentHits,
            int maxHits,
            int ticksRemaining,
            int totalTicks,
            long shownAtNanos,
            long lastHitAtNanos
    ) {
        private static final float INTRO_DURATION_NANOS = 180_000_000.0F;
        private static final float HIT_PULSE_NANOS = 280_000_000.0F;

        public boolean matches(String descriptionKey, int maxHits) {
            return this.maxHits == maxHits && this.descriptionKey.equals(descriptionKey);
        }

        public float progress() {
            return Math.max(0.0F, Math.min(1.0F, currentHits / (float) Math.max(1, maxHits)));
        }

        public float alpha() {
            int fadeTicks = Math.min(8, totalTicks);
            return ticksRemaining <= fadeTicks
                    ? Math.max(0.0F, ticksRemaining / (float) Math.max(1, fadeTicks))
                    : 1.0F;
        }

        public float introProgress() {
            return clamp01((System.nanoTime() - shownAtNanos) / INTRO_DURATION_NANOS);
        }

        public float hitPulse() {
            return 1.0F - clamp01((System.nanoTime() - lastHitAtNanos) / HIT_PULSE_NANOS);
        }

        public float ageSeconds() {
            return Math.max(0.0F, (System.nanoTime() - shownAtNanos) / 1_000_000_000.0F);
        }

        public float shimmerPhase() {
            return (ageSeconds() * 0.55F) % 1.0F;
        }

        private static float clamp01(float value) {
            return Math.max(0.0F, Math.min(1.0F, value));
        }
    }
}

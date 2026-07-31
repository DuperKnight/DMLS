package com.duperknight.client.reimbursement;

/** Approximate duration derived from the fully expanded command and routing plan. */
public record ReimbursementEstimate(int totalSeconds, int commandCount, int stackCount) {
    public static ReimbursementEstimate calculate(
            ReimbursementPlan plan,
            boolean admin,
            int pingMilliseconds,
            int spamWaitTicks
    ) {
        return calculate(plan, admin, pingMilliseconds, spamWaitTicks,
                ReimbursementCommandPlanner.build(plan));
    }

    static ReimbursementEstimate calculate(
            ReimbursementPlan plan,
            boolean admin,
            int pingMilliseconds,
            int spamWaitTicks,
            ReimbursementCommandPlanner.BuildResult commands
    ) {
        int ping = Math.clamp(pingMilliseconds, 50, 1500);
        double commandSeconds = commands.commandCount() * (admin ? Math.max(0.25, ping / 1000.0) : 1.0);
        double responseSeconds = Math.max(1.0, ping / 500.0);
        double screenSeconds = plan.playerTargets().size() * responseSeconds
                + plan.containerTargets().size() * responseSeconds * 2.0;
        double stackSeconds = plan.requiredStacks() * Math.max(0.35, ping / 1500.0);
        int seconds = Math.max(1, (int) Math.ceil(
                spamWaitTicks / 20.0 + commandSeconds + screenSeconds + stackSeconds + 1.0));
        return new ReimbursementEstimate(seconds, commands.commandCount(), plan.requiredStacks());
    }

    public String formatted() {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return "~%d:%02d".formatted(minutes, seconds);
    }
}

package jp.example.recipients;

import java.util.List;

/**
 * 宛先の件数ではなく、返却リストの変更可能性が原因かを比較観測する診断用コードです。
 */
public final class RecipientPlannerDiagnostic {

    public static void main(String[] args) {
        RecipientPlanner planner = new RecipientPlanner();
        diagnose("active=0", planner, List.of(
                new RecipientPlanner.Recipient("inactive@example.test", false)
        ));
        diagnose("active=1", planner, List.of(
                new RecipientPlanner.Recipient("alice@example.test", true)
        ));
    }

    private static void diagnose(
            String label,
            RecipientPlanner planner,
            List<RecipientPlanner.Recipient> candidates
    ) {
        try {
            planner.planRecipients(candidates);
            System.out.println(label + ": completed");
        } catch (RuntimeException exception) {
            System.out.println(label + ": " + exception.getClass().getName());
        }
    }
}

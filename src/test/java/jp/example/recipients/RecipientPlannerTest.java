package jp.example.recipients;

import java.util.List;

/**
 * 外部テストライブラリを使わない、実行可能な最小の振る舞いテストです。
 */
public final class RecipientPlannerTest {

    public static void main(String[] args) {
        new RecipientPlannerTest().activeRecipientsCanBeExtendedWithAuditRecipient();
        System.out.println("PASS: activeRecipientsCanBeExtendedWithAuditRecipient");
    }

    void activeRecipientsCanBeExtendedWithAuditRecipient() {
        RecipientPlanner planner = new RecipientPlanner();

        List<String> actual = planner.planRecipients(List.of(
                new RecipientPlanner.Recipient("alice@example.test", true),
                new RecipientPlanner.Recipient("inactive@example.test", false)
        ));

        assertEquals(
                List.of("alice@example.test", "audit@example.test"),
                actual,
                "有効な宛先に監査宛先を追加できるべき"
        );
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}

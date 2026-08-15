package jp.example.recipients;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 有効な通知先に必須の監査宛先を加える、最小の業務ロジックです。
 */
public final class RecipientPlanner {

    private static final String AUDIT_RECIPIENT = "audit@example.test";

    public List<String> planRecipients(List<Recipient> candidates) {
        List<String> recipients = candidates.stream()
                .filter(Recipient::active)
                .map(Recipient::email)
                .collect(Collectors.toCollection(ArrayList::new));

        System.out.println("before add: " + recipients.getClass().getName());
        recipients.add(AUDIT_RECIPIENT);
        return recipients;
    }

    public record Recipient(String email, boolean active) {
    }
}

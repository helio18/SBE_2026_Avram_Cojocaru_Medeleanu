package homework;

import java.util.ArrayList;
import java.util.List;

public class Subscription {

    private final String subscriberId;
    private final String subscriptionId;
    private final List<SubscriptionCondition> conditions;

    public Subscription(List<SubscriptionCondition> conditions) {
        this(null, null, conditions);
    }

    public Subscription(String subscriberId, String subscriptionId, List<SubscriptionCondition> conditions) {
        this.subscriberId = subscriberId;
        this.subscriptionId = subscriptionId;
        this.conditions = List.copyOf(new ArrayList<>(conditions));
    }

    public String getSubscriberId() {
        return subscriberId;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public List<SubscriptionCondition> getConditions() {
        return conditions;
    }

    public boolean matches(Publication publication) {
        for (SubscriptionCondition condition : conditions) {
            if (!condition.matches(publication)) {
                return false;
            }
        }

        return true;
    }

    public String toFileLine(int index) {
        StringBuilder builder = new StringBuilder();
        builder.append("Subscription ").append(index).append(": {");

        for (int conditionIndex = 0; conditionIndex < conditions.size(); conditionIndex++) {
            if (conditionIndex > 0) {
                builder.append(';');
            }
            builder.append(conditions.get(conditionIndex).toFileToken());
        }

        builder.append('}');
        return builder.toString();
    }
}

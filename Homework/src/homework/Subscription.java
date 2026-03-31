package homework;

import java.util.ArrayList;
import java.util.List;

public class Subscription {

    private final List<SubscriptionCondition> conditions;

    public Subscription(List<SubscriptionCondition> conditions) {
        this.conditions = List.copyOf(new ArrayList<>(conditions));
    }

    public List<SubscriptionCondition> getConditions() {
        return conditions;
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

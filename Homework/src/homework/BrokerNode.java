package homework;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BrokerNode {

    private final String brokerId;
    private final Map<String, String> subscriptionToSubscriber;
    private final Map<String, List<SubscriptionCondition>> subscriptionFragments;

    public BrokerNode(String brokerId) {
        this.brokerId = brokerId;
        this.subscriptionToSubscriber = new LinkedHashMap<>();
        this.subscriptionFragments = new LinkedHashMap<>();
    }

    public String getBrokerId() {
        return brokerId;
    }

    public void registerFragment(String subscriptionId, String subscriberId, List<SubscriptionCondition> fragment) {
        if (fragment.isEmpty()) {
            return;
        }

        subscriptionToSubscriber.put(subscriptionId, subscriberId);
        subscriptionFragments.computeIfAbsent(subscriptionId, key -> new ArrayList<>()).addAll(fragment);
    }

    public Set<String> filterCandidates(Publication publication, Set<String> candidates) {
        Set<String> nextCandidates = new LinkedHashSet<>();

        for (String subscriptionId : candidates) {
            List<SubscriptionCondition> conditions = subscriptionFragments.get(subscriptionId);
            if (conditions == null || conditions.isEmpty()) {
                nextCandidates.add(subscriptionId);
                continue;
            }

            boolean matches = true;
            for (SubscriptionCondition condition : conditions) {
                if (!condition.matches(publication)) {
                    matches = false;
                    break;
                }
            }

            if (matches) {
                nextCandidates.add(subscriptionId);
            }
        }

        return nextCandidates;
    }

    public int getLocalSubscriptionCount() {
        return subscriptionFragments.size();
    }

    public int getLocalConditionCount() {
        int count = 0;
        for (List<SubscriptionCondition> conditions : subscriptionFragments.values()) {
            count += conditions.size();
        }
        return count;
    }
}
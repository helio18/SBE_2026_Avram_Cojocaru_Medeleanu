package homework;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BrokerOverlay {

    public static final class PublicationRouteResult {
        private final Set<String> matchedSubscriptionIds;
        private final List<String> visitedBrokers;
        private final long deliveryLatencyMillis;

        PublicationRouteResult(Set<String> matchedSubscriptionIds, List<String> visitedBrokers, long deliveryLatencyMillis) {
            this.matchedSubscriptionIds = new LinkedHashSet<>(matchedSubscriptionIds);
            this.visitedBrokers = new ArrayList<>(visitedBrokers);
            this.deliveryLatencyMillis = deliveryLatencyMillis;
        }

        public Set<String> getMatchedSubscriptionIds() {
            return new LinkedHashSet<>(matchedSubscriptionIds);
        }

        public List<String> getVisitedBrokers() {
            return new ArrayList<>(visitedBrokers);
        }

        public long getDeliveryLatencyMillis() {
            return deliveryLatencyMillis;
        }
    }

    private final List<BrokerNode> brokers;
    private final Map<String, Subscription> subscriptionsById;

    public BrokerOverlay(int brokerCount) {
        int safeBrokerCount = Math.max(2, brokerCount);
        this.brokers = new ArrayList<>(safeBrokerCount);
        this.subscriptionsById = new LinkedHashMap<>();

        for (int index = 0; index < safeBrokerCount; index++) {
            brokers.add(new BrokerNode("broker-" + (index + 1)));
        }
    }

    public void registerSubscription(Subscription subscription) {
        String subscriptionId = ensureSubscriptionId(subscription);
        subscriptionsById.put(subscriptionId, subscription);

        Map<Integer, List<SubscriptionCondition>> fragmentsByBroker = new LinkedHashMap<>();
        List<SubscriptionCondition> conditions = subscription.getConditions();

        for (int conditionIndex = 0; conditionIndex < conditions.size(); conditionIndex++) {
            SubscriptionCondition condition = conditions.get(conditionIndex);
            int brokerIndex = chooseBrokerIndex(subscriptionId, condition, conditionIndex);
            fragmentsByBroker.computeIfAbsent(Integer.valueOf(brokerIndex), key -> new ArrayList<>()).add(condition);
        }

        for (Map.Entry<Integer, List<SubscriptionCondition>> entry : fragmentsByBroker.entrySet()) {
            brokers.get(entry.getKey().intValue()).registerFragment(
                    subscriptionId,
                    subscription.getSubscriberId(),
                    entry.getValue());
        }
    }

    public PublicationRouteResult routePublication(Publication publication) {
        Set<String> candidates = new LinkedHashSet<>(subscriptionsById.keySet());
        List<String> visitedBrokers = new ArrayList<>();

        for (BrokerNode broker : brokers) {
            visitedBrokers.add(broker.getBrokerId());
            candidates = broker.filterCandidates(publication, candidates);
            if (candidates.isEmpty()) {
                break;
            }
        }

        long deliveryLatencyMillis = Math.max(1L, visitedBrokers.size()) + Math.max(0L, candidates.size());
        return new PublicationRouteResult(candidates, visitedBrokers, deliveryLatencyMillis);
    }

    public int getBrokerCount() {
        return brokers.size();
    }

    public List<BrokerNode> getBrokers() {
        return new ArrayList<>(brokers);
    }

    private int chooseBrokerIndex(String subscriptionId, SubscriptionCondition condition, int conditionIndex) {
        int hash = 17;
        hash = 31 * hash + subscriptionId.hashCode();
        hash = 31 * hash + condition.getFieldName().hashCode();
        hash = 31 * hash + condition.getOperator().hashCode();
        hash = 31 * hash + conditionIndex;
        return Math.floorMod(hash, brokers.size());
    }

    private String ensureSubscriptionId(Subscription subscription) {
        if (subscription.getSubscriptionId() != null) {
            return subscription.getSubscriptionId();
        }

        return "subscription-" + (subscriptionsById.size() + 1);
    }
}
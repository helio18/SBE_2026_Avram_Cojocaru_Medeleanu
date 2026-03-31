package homework;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SubscriptionRouter {

    private final ConcurrentHashMap<String, AtomicInteger> subscriberRoundRobin;

    public SubscriptionRouter() {
        this.subscriberRoundRobin = new ConcurrentHashMap<>();
    }

    public int routeSubscription(
            List<BrokerNode> brokers,
            int entryBrokerIndex,
            RegisteredSubscription registeredSubscription) {
        AtomicInteger counter = subscriberRoundRobin.computeIfAbsent(
                registeredSubscription.getSubscriberId(),
                ignored -> new AtomicInteger(0));

        int targetBrokerIndex = Math.floorMod(counter.getAndIncrement(), brokers.size());
        int hops = 0;
        int currentIndex = entryBrokerIndex;

        while (true) {
            hops++;

            if (currentIndex == targetBrokerIndex) {
                brokers.get(currentIndex).storeSubscription(registeredSubscription);
                return hops;
            }

            currentIndex = (currentIndex + 1) % brokers.size();
        }
    }
}

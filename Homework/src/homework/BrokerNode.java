package homework;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class BrokerNode {

    private final String brokerId;
    private final List<RegisteredSubscription> storedSubscriptions;

    public BrokerNode(String brokerId) {
        this.brokerId = brokerId;
        this.storedSubscriptions = new CopyOnWriteArrayList<>();
    }

    public String getBrokerId() {
        return brokerId;
    }

    public void storeSubscription(RegisteredSubscription registeredSubscription) {
        storedSubscriptions.add(registeredSubscription);
    }

    public int getStoredSubscriptionCount() {
        return storedSubscriptions.size();
    }

    public PublicationRouteResult matchPublication(
            Publication publication,
            long emitNanoTime,
            Set<String> alreadyNotifiedSubscribers) {
        int deliveries = 0;
        long totalLatencyNanos = 0L;

        for (RegisteredSubscription registeredSubscription : storedSubscriptions) {
            String subscriberId = registeredSubscription.getSubscriberId();

            if (alreadyNotifiedSubscribers.contains(subscriberId)) {
                continue;
            }

            if (!registeredSubscription.getSubscription().matches(publication)) {
                continue;
            }

            alreadyNotifiedSubscribers.add(subscriberId);
            totalLatencyNanos += registeredSubscription.getSubscriber().receiveNotification(
                    publication,
                    brokerId,
                    emitNanoTime);
            deliveries++;
        }

        return new PublicationRouteResult(deliveries, totalLatencyNanos, 1);
    }
}

package project.routing;

import homework.Publication;
import homework.Subscription;
import project.matching.MatchingEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RoutingTable {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Subscription>> perNeighbor =
            new ConcurrentHashMap<>();

    public void addAdvertisement(String neighborId, String subscriptionId, Subscription subscription) {
        perNeighbor.computeIfAbsent(neighborId, key -> new ConcurrentHashMap<>())
                .put(subscriptionId, subscription);
    }

    public List<String> neighborsInterestedIn(Publication publication) {
        List<String> interested = new ArrayList<>();
        for (Map.Entry<String, ConcurrentHashMap<String, Subscription>> entry : perNeighbor.entrySet()) {
            for (Subscription subscription : entry.getValue().values()) {
                if (MatchingEngine.matches(publication, subscription)) {
                    interested.add(entry.getKey());
                    break;
                }
            }
        }
        return interested;
    }

    public int totalAdvertisedSubscriptions() {
        int total = 0;
        for (ConcurrentHashMap<String, Subscription> entries : perNeighbor.values()) {
            total += entries.size();
        }
        return total;
    }
}

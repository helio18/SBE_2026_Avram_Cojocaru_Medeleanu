package project;

import homework.DatasetGenerator;
import homework.GeneratorConfig;
import homework.Publication;
import homework.Subscription;
import project.matching.MatchingEngine;

public class ProjectApp {

    public static void main(String[] args) {
        GeneratorConfig config = GeneratorConfig.defaultConfig()
                .withPublicationCount(1_000)
                .withSubscriptionCount(1_000);

        DatasetGenerator generator = new DatasetGenerator(config);
        Publication[] publications = generator.generatePublications(1);
        Subscription[] subscriptions = generator.generateSubscriptions(1);

        long matchCount = 0;
        long pairsChecked = 0;
        for (Subscription subscription : subscriptions) {
            for (Publication publication : publications) {
                pairsChecked++;
                if (MatchingEngine.matches(publication, subscription)) {
                    matchCount++;
                }
            }
        }

        double matchRate = (100.0d * matchCount) / pairsChecked;
        System.out.println("Project skeleton ready.");
        System.out.printf(
                "Matching smoke check: %d matches / %d pairs (%.4f%%)%n",
                matchCount,
                pairsChecked,
                matchRate);
    }
}

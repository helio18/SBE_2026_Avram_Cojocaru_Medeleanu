package project.transport;

import homework.Publication;
import homework.Subscription;
import homework.SubscriptionCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MessageCodec {

    public static final String TYPE_SUB = "SUB";
    public static final String TYPE_ADV = "ADV";
    public static final String TYPE_PUB = "PUB";
    public static final String TYPE_NOT = "NOT";

    private MessageCodec() {
    }

    public static String buildSubscribe(
            String subId,
            String subscriberHost,
            int subscriberPort,
            Subscription subscription) {
        return TYPE_SUB + "|" + subId + "|" + subscriberHost + "|" + subscriberPort
                + "|" + encodeConditions(subscription);
    }

    public static String buildAdvertisement(
            String subId,
            String originBrokerId,
            int hopCount,
            String encodedConditions) {
        return TYPE_ADV + "|" + subId + "|" + originBrokerId + "|" + hopCount
                + "|" + encodedConditions;
    }

    public static String buildPublication(
            String pubId,
            long emitTimestampMs,
            int hopCount,
            Publication publication) {
        return TYPE_PUB + "|" + pubId + "|" + emitTimestampMs + "|" + hopCount
                + "|" + publication.getCompany()
                + "|" + String.format(Locale.US, "%.6f", publication.getValue())
                + "|" + String.format(Locale.US, "%.6f", publication.getDrop())
                + "|" + String.format(Locale.US, "%.6f", publication.getVariation())
                + "|" + publication.getDate();
    }

    public static String buildNotification(
            String pubId,
            long emitTimestampMs,
            String subId,
            Publication publication) {
        return TYPE_NOT + "|" + pubId + "|" + emitTimestampMs + "|" + subId
                + "|" + publication.getCompany()
                + "|" + String.format(Locale.US, "%.6f", publication.getValue())
                + "|" + String.format(Locale.US, "%.6f", publication.getDrop())
                + "|" + String.format(Locale.US, "%.6f", publication.getVariation())
                + "|" + publication.getDate();
    }

    public static String encodeConditions(Subscription subscription) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (SubscriptionCondition condition : subscription.getConditions()) {
            if (!first) {
                builder.append(';');
            }
            first = false;
            builder.append(condition.toFileToken());
        }
        return builder.toString();
    }

    public static List<SubscriptionCondition> decodeConditions(String encoded) {
        List<SubscriptionCondition> conditions = new ArrayList<>();
        int index = 0;
        while (index < encoded.length()) {
            if (encoded.charAt(index) != '(') {
                throw new IllegalArgumentException("Bad encoded conditions: " + encoded);
            }
            int end = encoded.indexOf(')', index);
            if (end < 0) {
                throw new IllegalArgumentException("Unterminated condition: " + encoded);
            }
            String body = encoded.substring(index + 1, end);
            int firstComma = body.indexOf(',');
            int secondComma = body.indexOf(',', firstComma + 1);
            String field = body.substring(0, firstComma);
            String operator = body.substring(firstComma + 1, secondComma);
            String value = body.substring(secondComma + 1);

            if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
                conditions.add(SubscriptionCondition.text(
                        field,
                        operator,
                        value.substring(1, value.length() - 1)));
            } else {
                double parsed = Double.parseDouble(value);
                int decimals = value.contains(".") ? value.length() - value.indexOf('.') - 1 : 0;
                conditions.add(SubscriptionCondition.number(field, operator, parsed, decimals));
            }

            index = end + 1;
            if (index < encoded.length() && encoded.charAt(index) == ';') {
                index++;
            }
        }
        return conditions;
    }
}

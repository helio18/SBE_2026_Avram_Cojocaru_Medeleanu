#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HOMEWORK_BIN="$REPO_ROOT/Homework/bin"
PROJECT_BIN="$SCRIPT_DIR/bin"
PROTOBUF_JAR="$SCRIPT_DIR/lib/protobuf-java-4.28.3.jar"

DURATION_SECONDS="${DURATION_SECONDS:-180}"
TOTAL_SUBSCRIPTIONS="${TOTAL_SUBSCRIPTIONS:-10000}"
PUBLICATION_RATE="${PUBLICATION_RATE:-50}"
PUBLISHER_COUNT="${PUBLISHER_COUNT:-1}"
SUBSCRIBER_COUNT="${SUBSCRIBER_COUNT:-3}"
SUB_SEND_GRACE_SECONDS="${SUB_SEND_GRACE_SECONDS:-8}"
DRAIN_GRACE_SECONDS="${DRAIN_GRACE_SECONDS:-5}"

if [[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* || "$(uname -s)" == CYGWIN* ]]; then
    CP="$(cygpath -w "$HOMEWORK_BIN");$(cygpath -w "$PROJECT_BIN");$(cygpath -w "$PROTOBUF_JAR")"
else
    CP="$HOMEWORK_BIN:$PROJECT_BIN:$PROTOBUF_JAR"
fi

log_step() {
    printf '\n== %s ==\n' "$1"
}

log_step "Compiling sources"
bash "$SCRIPT_DIR/build.bash"

mkdir -p "$SCRIPT_DIR/output"
OUTPUT_ROOT="$(mktemp -d "$SCRIPT_DIR/output/eval.XXXXXX")"
echo "Output root: $OUTPUT_ROOT"

declare -a BACKGROUND_PIDS=()

stop_all() {
    if [[ ${#BACKGROUND_PIDS[@]} -gt 0 ]]; then
        for pid in "${BACKGROUND_PIDS[@]}"; do
            kill -KILL "$pid" 2>/dev/null || true
        done
        BACKGROUND_PIDS=()
    fi
}

trap stop_all EXIT

run_scenario() {
    local scenario_name="$1"
    local company_equals="$2"
    local out_dir="$OUTPUT_ROOT/$scenario_name"
    mkdir -p "$out_dir"

    local stop_file="$out_dir/STOP"
    rm -f "$stop_file"

    log_step "Scenario $scenario_name (company-equals=$company_equals%)"

    BACKGROUND_PIDS=()
    local broker_pids=()
    local subscriber_pids=()
    local publisher_pids=()

    # Subscriberii si peering-ul folosesc porturile text (5001-3).
    # Publisher-ul foloseste pub-port-urile binare (7001-3) pentru protobuf.
    local broker_list_text="B1@localhost:5001,B2@localhost:5002,B3@localhost:5003"
    local broker_list_pub="B1@localhost:7001,B2@localhost:7002,B3@localhost:7003"

    java -cp "$CP" project.broker.BrokerMain \
        --id=B1 --port=5001 --pub-port=7001 \
        --peers=B2@localhost:5002,B3@localhost:5003 \
        --stop-file="$stop_file" \
        --stats-file="$out_dir/B1.stats" \
        > "$out_dir/B1.log" 2>&1 &
    broker_pids+=($!)

    java -cp "$CP" project.broker.BrokerMain \
        --id=B2 --port=5002 --pub-port=7002 \
        --peers=B1@localhost:5001,B3@localhost:5003 \
        --stop-file="$stop_file" \
        --stats-file="$out_dir/B2.stats" \
        > "$out_dir/B2.log" 2>&1 &
    broker_pids+=($!)

    java -cp "$CP" project.broker.BrokerMain \
        --id=B3 --port=5003 --pub-port=7003 \
        --peers=B1@localhost:5001,B2@localhost:5002 \
        --stop-file="$stop_file" \
        --stats-file="$out_dir/B3.stats" \
        > "$out_dir/B3.log" 2>&1 &
    broker_pids+=($!)

    BACKGROUND_PIDS+=("${broker_pids[@]}")

    sleep 2

    local subscriptions_per_subscriber=$((TOTAL_SUBSCRIPTIONS / SUBSCRIBER_COUNT))
    local remainder=$((TOTAL_SUBSCRIPTIONS % SUBSCRIBER_COUNT))

    for ((index=1; index<=SUBSCRIBER_COUNT; index++)); do
        local sub_id="S$index"
        local listen_port=$((6000 + index))
        local seed=$((100000 + index))
        local subs_for_this=$subscriptions_per_subscriber
        if [[ "$index" -le "$remainder" ]]; then
            subs_for_this=$((subs_for_this + 1))
        fi

        java -cp "$CP" project.subscriber.SubscriberMain \
            --id="$sub_id" \
            --listen-port="$listen_port" \
            --brokers="$broker_list_text" \
            --subscriptions="$subs_for_this" \
            --company-frequency=100 \
            --value-frequency=0 \
            --drop-frequency=0 \
            --variation-frequency=0 \
            --date-frequency=0 \
            --company-equals="$company_equals" \
            --seed="$seed" \
            --threads=2 \
            --sub-id-prefix="$sub_id" \
            --stop-file="$stop_file" \
            --stats-file="$out_dir/$sub_id.stats" \
            > "$out_dir/$sub_id.log" 2>&1 &
        subscriber_pids+=($!)
    done
    BACKGROUND_PIDS+=("${subscriber_pids[@]}")

    sleep "$SUB_SEND_GRACE_SECONDS"

    for ((index=1; index<=PUBLISHER_COUNT; index++)); do
        local pub_id="P$index"
        local seed=$((200000 + index))
        local publications_total=$((PUBLICATION_RATE * DURATION_SECONDS + 1000))

        java -cp "$CP" project.publisher.PublisherMain \
            --id="$pub_id" \
            --brokers="$broker_list_pub" \
            --publications="$publications_total" \
            --rate="$PUBLICATION_RATE" \
            --duration-seconds="$DURATION_SECONDS" \
            --seed="$seed" \
            --threads=2 \
            --pub-id-prefix="$pub_id" \
            --stats-file="$out_dir/$pub_id.stats" \
            > "$out_dir/$pub_id.log" 2>&1 &
        publisher_pids+=($!)
    done
    BACKGROUND_PIDS+=("${publisher_pids[@]}")

    echo "[$scenario_name] publishers running for ${DURATION_SECONDS}s..."
    for pid in "${publisher_pids[@]}"; do
        wait "$pid" 2>/dev/null || true
    done
    echo "[$scenario_name] publishers finished, draining ${DRAIN_GRACE_SECONDS}s..."

    sleep "$DRAIN_GRACE_SECONDS"

    : > "$stop_file"

    for pid in "${subscriber_pids[@]}"; do
        wait "$pid" 2>/dev/null || true
    done
    for pid in "${broker_pids[@]}"; do
        wait "$pid" 2>/dev/null || true
    done
    BACKGROUND_PIDS=()

    aggregate_scenario "$out_dir" "$scenario_name" "$company_equals"
}

aggregate_scenario() {
    local out_dir="$1"
    local scenario_name="$2"
    local company_equals="$3"
    local report="$out_dir/report.md"

    local total_notifications=0
    local total_subscriptions=0
    local total_publications=0
    local weighted_latency=0
    local total_for_latency=0

    for stats in "$out_dir"/S*.stats; do
        [[ -f "$stats" ]] || continue
        local notifications subscriptions latency
        notifications=$(grep '^notificationsReceived=' "$stats" | cut -d= -f2)
        subscriptions=$(grep '^subscriptionsSent=' "$stats" | cut -d= -f2)
        latency=$(grep '^averageLatencyMs=' "$stats" | cut -d= -f2)
        total_notifications=$((total_notifications + notifications))
        total_subscriptions=$((total_subscriptions + subscriptions))
        if [[ "$notifications" -gt 0 ]]; then
            weighted_latency=$(awk -v wl="$weighted_latency" -v n="$notifications" -v l="$latency" \
                'BEGIN { printf "%.6f", wl + n * l }')
            total_for_latency=$((total_for_latency + notifications))
        fi
    done

    for stats in "$out_dir"/P*.stats; do
        [[ -f "$stats" ]] || continue
        local publications
        publications=$(grep '^publicationsSent=' "$stats" | cut -d= -f2)
        total_publications=$((total_publications + publications))
    done

    local average_latency="0.000"
    if [[ "$total_for_latency" -gt 0 ]]; then
        average_latency=$(awk -v wl="$weighted_latency" -v tn="$total_for_latency" \
            'BEGIN { printf "%.3f", wl / tn }')
    fi

    local match_rate_percent="0.0000"
    if [[ "$total_publications" -gt 0 && "$total_subscriptions" -gt 0 ]]; then
        match_rate_percent=$(awk -v notifications="$total_notifications" \
            -v publications="$total_publications" -v subscriptions="$total_subscriptions" \
            'BEGIN { printf "%.4f", 100.0 * notifications / (publications * subscriptions) }')
    fi

    {
        echo "## Scenariu: $scenario_name"
        echo
        echo "- Frecventa operatorului \`=\` pe \`company\`: ${company_equals}%"
        echo "- Subscriptii inregistrate (total pe toti subscriberii): ${total_subscriptions}"
        echo "- Publicatii emise (total pe toti publisherii): ${total_publications}"
        echo "- Notificari primite (total pe toti subscriberii): ${total_notifications}"
        echo "- Latenta medie de livrare: **${average_latency} ms**"
        echo "- Rata de matching (notificari / (publicatii x subscriptii)): **${match_rate_percent}%**"
    } > "$report"

    cat "$report"
}

run_scenario "scenario-A-eq-100" 100
run_scenario "scenario-B-eq-25" 25

log_step "Final report"

PROCESSOR_INFO=""
if command -v powershell.exe >/dev/null 2>&1; then
    PROCESSOR_INFO="$(powershell.exe -NoProfile -Command "(Get-CimInstance Win32_Processor).Name" 2>/dev/null | tr -d '\r\n' || true)"
fi
if [[ -z "$PROCESSOR_INFO" ]] && command -v sysctl >/dev/null 2>&1; then
    PROCESSOR_INFO="$(sysctl -n machdep.cpu.brand_string 2>/dev/null || true)"
fi
if [[ -z "$PROCESSOR_INFO" ]] && [[ -r /proc/cpuinfo ]]; then
    PROCESSOR_INFO="$(grep -m1 'model name' /proc/cpuinfo | cut -d: -f2- | sed 's/^ //' || true)"
fi
if [[ -z "$PROCESSOR_INFO" ]]; then
    PROCESSOR_INFO="unknown"
fi

{
    echo "# Raport evaluare proiect SBE 2026"
    echo
    echo "## Configuratie"
    echo
    echo "- Durata feed publicatii per scenariu: \`${DURATION_SECONDS}\` secunde"
    echo "- Subscriptii totale per scenariu: \`${TOTAL_SUBSCRIPTIONS}\`"
    echo "- Subscriberi: \`${SUBSCRIBER_COUNT}\` (balansare round-robin pe brokeri)"
    echo "- Publisheri: \`${PUBLISHER_COUNT}\` (rata \`${PUBLICATION_RATE}\` pub/s)"
    echo "- Brokeri: 3 in topologie triunghi B1-B2-B3-B1"
    echo "- Procesor: \`${PROCESSOR_INFO}\`"
    echo "- Java: \`$(java -version 2>&1 | head -1)\`"
    echo
    cat "$OUTPUT_ROOT/scenario-A-eq-100/report.md"
    echo
    cat "$OUTPUT_ROOT/scenario-B-eq-25/report.md"
} > "$OUTPUT_ROOT/final-report.md"

cat "$OUTPUT_ROOT/final-report.md"

echo
echo "Artifacts kept in: $OUTPUT_ROOT"

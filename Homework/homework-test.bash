#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

MODE="quick"

usage() {
    cat <<'EOF'
Usage:
  bash homework-test.bash
  bash homework-test.bash --full

Modes:
  quick  Runs the core regression and functionality suite.
  full   Runs the quick suite plus the isolated default run and a large benchmark.
EOF
}

case "${1:-}" in
    "")
        ;;
    --full)
        MODE="full"
        ;;
    --help|-h)
        usage
        exit 0
        ;;
    *)
        echo "Unknown option: $1" >&2
        usage >&2
        exit 1
        ;;
esac

mkdir -p "$SCRIPT_DIR/output"
OUTPUT_ROOT="$(mktemp -d "$SCRIPT_DIR/output/automated-tests.XXXXXX")"

log_step() {
    printf '\n== %s ==\n' "$1"
}

pass() {
    printf '[PASS] %s\n' "$1"
}

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    printf 'Artifacts kept in: %s\n' "$OUTPUT_ROOT" >&2
    exit 1
}

assert_file_exists() {
    local file_path="$1"
    [[ -f "$file_path" ]] || fail "Expected file not found: $file_path"
}

assert_dir_exists() {
    local dir_path="$1"
    [[ -d "$dir_path" ]] || fail "Expected directory not found: $dir_path"
}

assert_contains() {
    local file_path="$1"
    local expected="$2"
    grep -Fq -- "$expected" "$file_path" || fail "Expected text not found in $file_path: $expected"
}

assert_identical() {
    local first="$1"
    local second="$2"
    cmp -s "$first" "$second" || fail "Files differ: $first vs $second"
}

run_app() {
    local label="$1"
    shift

    local output_dir="$OUTPUT_ROOT/$label"
    local log_file="$OUTPUT_ROOT/$label.run.log"

    java -cp bin homework.HomeworkApp "$@" "--output=$output_dir" >"$log_file" 2>&1 \
        || fail "Application run failed for $label. See $log_file"

    printf '%s\n' "$output_dir"
}

compile_sources() {
    log_step "Compiling sources"
    find src -name '*.java' -print0 | xargs -0 javac -d bin
    assert_file_exists "$SCRIPT_DIR/bin/homework/HomeworkApp.class"
    pass "Sources compiled"
}

test_help() {
    log_step "Testing --help"
    local help_file="$OUTPUT_ROOT/help.txt"
    java -cp bin homework.HomeworkApp --help >"$help_file" 2>&1 || fail "Help command failed"
    assert_contains "$help_file" "Usage:"
    assert_contains "$help_file" "--company-equals=<0..100>"
    assert_contains "$help_file" "--threads=<lista separata prin virgula, ex. 1,4>"
    pass "Help output is correct"
}

test_small_functional() {
    log_step "Testing small functional scenario"
    local out_dir
    out_dir="$(run_app small-functional --publications=1000 --subscriptions=1000 --threads=1,4)"

    assert_file_exists "$out_dir/README.md"
    assert_file_exists "$out_dir/threads-1/summary.txt"
    assert_file_exists "$out_dir/threads-4/summary.txt"

    assert_contains "$out_dir/threads-1/summary.txt" "- company: requested 90%, planned 900 (90.00%), actual 900 (90.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "- value: requested 70%, planned 700 (70.00%), actual 700 (70.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "- drop: requested 55%, planned 550 (55.00%), actual 550 (55.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "- variation: requested 65%, planned 650 (65.00%), actual 650 (65.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "- date: requested 40%, planned 400 (40.00%), actual 400 (40.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "Company equality actual: 630 / 900 (70.00%)"
    assert_contains "$out_dir/README.md" "| Camp | Cerut | Tinta discreta | Obtinut |"
    assert_contains "$out_dir/README.md" "| drop | 55% | 550 (55.00%) | 550 (55.00%) |"
    pass "Small functional scenario passed"
}

test_edge_frequencies() {
    log_step "Testing edge frequencies"
    local out_dir
    out_dir="$(run_app edge-frequencies --publications=500 --subscriptions=500 --threads=1,2 --company-frequency=100 --value-frequency=100 --drop-frequency=0 --variation-frequency=0 --date-frequency=0 --company-equals=80)"

    assert_contains "$out_dir/threads-1/summary.txt" "- company: requested 100%, planned 500 (100.00%), actual 500 (100.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "- value: requested 100%, planned 500 (100.00%), actual 500 (100.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "- drop: requested 0%, planned 0 (0.00%), actual 0 (0.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "- variation: requested 0%, planned 0 (0.00%), actual 0 (0.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "- date: requested 0%, planned 0 (0.00%), actual 0 (0.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "Company equality actual: 400 / 500 (80.00%)"
    pass "Edge frequency scenario passed"
}

test_midrange_frequencies() {
    log_step "Testing mid-range frequency values"
    local out_dir
    out_dir="$(run_app midrange-frequencies --publications=200 --subscriptions=200 --threads=1 --company-frequency=30 --value-frequency=45 --drop-frequency=60 --variation-frequency=75 --date-frequency=90 --company-equals=35 --seed=12345)"

    assert_contains "$out_dir/threads-1/summary.txt" "- company: requested 30%, planned 60 (30.00%), actual 60 (30.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "- value: requested 45%, planned 90 (45.00%), actual 90 (45.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "- drop: requested 60%, planned 120 (60.00%), actual 120 (60.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "- variation: requested 75%, planned 150 (75.00%), actual 150 (75.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "- date: requested 90%, planned 180 (90.00%), actual 180 (90.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "Company equality actual: 21 / 60 (35.00%)"
    pass "Mid-range frequency scenario passed"
}

test_operator_coverage() {
    log_step "Testing operator coverage"
    local out_dir
    local subscriptions_file
    out_dir="$(run_app operator-coverage --publications=500 --subscriptions=500 --threads=1 --company-frequency=90 --value-frequency=70 --drop-frequency=55 --variation-frequency=65 --date-frequency=40 --company-equals=20 --seed=12345)"
    subscriptions_file="$out_dir/threads-1/subscriptions.txt"

    assert_contains "$out_dir/threads-1/summary.txt" "Company equality actual: 90 / 450 (20.00%)"
    assert_contains "$subscriptions_file" "(company,!=,"
    assert_contains "$subscriptions_file" "(value,<,"
    assert_contains "$subscriptions_file" "(value,>,"
    assert_contains "$subscriptions_file" "(value,<=,"
    assert_contains "$subscriptions_file" "(value,>=,"
    assert_contains "$subscriptions_file" "(value,=,"
    pass "Operator coverage scenario passed"
}

test_frequency_sum_above_100() {
    log_step "Testing frequency sums above 100%"
    local out_dir
    out_dir="$(run_app sum-above-100 --publications=500 --subscriptions=500 --threads=1 --company-frequency=50 --value-frequency=50 --drop-frequency=50 --variation-frequency=50 --date-frequency=50 --seed=12345)"

    assert_contains "$out_dir/threads-1/summary.txt" "- company: requested 50%, planned 250 (50.00%), actual 250 (50.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "- value: requested 50%, planned 250 (50.00%), actual 250 (50.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "- drop: requested 50%, planned 250 (50.00%), actual 250 (50.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "- variation: requested 50%, planned 250 (50.00%), actual 250 (50.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "- date: requested 50%, planned 250 (50.00%), actual 250 (50.00%)"
    pass "Frequency sum above 100% scenario passed"
}

test_single_thread_baseline() {
    log_step "Testing single-thread baseline"
    local out_dir
    out_dir="$(run_app baseline-single-thread --publications=1000 --subscriptions=1000 --threads=1 --seed=12345)"

    assert_dir_exists "$out_dir/threads-1"
    [[ ! -d "$out_dir/threads-4" ]] || fail "Unexpected threads-4 directory in single-thread baseline"
    assert_contains "$out_dir/threads-1/summary.txt" "Threads: 1"
    assert_contains "$out_dir/README.md" "| 1 |"
    pass "Single-thread baseline scenario passed"
}

test_small_workload_many_threads() {
    log_step "Testing small workload with many threads"
    local out_dir
    out_dir="$(run_app workload-threads-8 --publications=1000 --subscriptions=1000 --threads=8 --seed=12345)"

    assert_dir_exists "$out_dir/threads-8"
    assert_contains "$out_dir/threads-8/summary.txt" "Threads: 8"
    assert_contains "$out_dir/threads-8/summary.txt" "- company: requested 90%, planned 900 (90.00%), actual 900 (90.00%)"
    assert_contains "$out_dir/README.md" "| 8 |"
    pass "Small workload with many threads passed"
}

test_small_regressions() {
    log_step "Testing small regression scenarios"
    local out_one out_two
    out_one="$(run_app regression-small-1 --publications=1 --subscriptions=1 --threads=1 --company-frequency=20 --value-frequency=20 --drop-frequency=20 --variation-frequency=20 --date-frequency=20)"
    out_two="$(run_app regression-small-2 --publications=2 --subscriptions=2 --threads=1 --company-frequency=20 --value-frequency=20 --drop-frequency=20 --variation-frequency=20 --date-frequency=20)"

    assert_contains "$out_one/threads-1/summary.txt" "- company: requested 20%, planned 1 (100.00%), actual 1 (100.00%)"
    assert_contains "$out_one/threads-1/summary.txt" "Company equality actual: 1 / 1 (100.00%)"
    assert_contains "$out_two/threads-1/summary.txt" "- company: requested 20%, planned 1 (50.00%), actual 1 (50.00%)"
    assert_contains "$out_two/threads-1/summary.txt" "- value: requested 20%, planned 1 (50.00%), actual 1 (50.00%)"
    pass "Small regression scenarios passed"
}

test_discrete_reporting() {
    log_step "Testing discrete percentage reporting"
    local out_dir
    out_dir="$(run_app discrete-reporting --publications=10 --subscriptions=10 --threads=1)"

    assert_contains "$out_dir/threads-1/summary.txt" "- drop: requested 55%, planned 6 (60.00%), actual 6 (60.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "- variation: requested 65%, planned 7 (70.00%), actual 7 (70.00%)"
    assert_contains "$out_dir/threads-1/summary.txt" "Company equality planned minimum: 7 / 9 (77.78%)"
    assert_contains "$out_dir/README.md" "| drop | 55% | 6 (60.00%) | 6 (60.00%) |"
    assert_contains "$out_dir/README.md" '- `company` cu operator `=` tinta discreta minima: `7 / 9` = `77.78%`'
    pass "Discrete reporting scenario passed"
}

test_seed_reproducibility() {
    log_step "Testing reproducibility with a fixed seed"
    local out_a out_b
    out_a="$(run_app seed-a --publications=1000 --subscriptions=1000 --threads=1,4 --seed=12345)"
    out_b="$(run_app seed-b --publications=1000 --subscriptions=1000 --threads=1,4 --seed=12345)"

    assert_identical "$out_a/threads-1/publications.txt" "$out_b/threads-1/publications.txt"
    assert_identical "$out_a/threads-1/subscriptions.txt" "$out_b/threads-1/subscriptions.txt"
    assert_identical "$out_a/threads-4/publications.txt" "$out_b/threads-4/publications.txt"
    assert_identical "$out_a/threads-4/subscriptions.txt" "$out_b/threads-4/subscriptions.txt"
    pass "Fixed-seed reproducibility passed"
}

test_parallel_consistency() {
    log_step "Testing consistency across thread counts"
    local out_dir
    out_dir="$(run_app consistency --publications=1000 --subscriptions=1000 --threads=1,4 --seed=20260325)"

    assert_identical "$out_dir/threads-1/publications.txt" "$out_dir/threads-4/publications.txt"
    assert_identical "$out_dir/threads-1/subscriptions.txt" "$out_dir/threads-4/subscriptions.txt"
    pass "Thread-count consistency passed"
}

test_default_run_isolated() {
    log_step "Testing isolated default run"
    local sandbox="$OUTPUT_ROOT/default-run-workspace"
    local log_file="$OUTPUT_ROOT/default-run.run.log"

    mkdir -p "$sandbox/bin"
    cp -R "$SCRIPT_DIR/src" "$sandbox/src"
    cp "$SCRIPT_DIR/README.md" "$sandbox/README.md"

    (
        cd "$sandbox"
        find src -name '*.java' -print0 | xargs -0 javac -d bin
        java -cp bin homework.HomeworkApp >"$log_file" 2>&1
    ) || fail "Isolated default run failed. See $log_file"

    assert_file_exists "$sandbox/README.md"
    assert_file_exists "$sandbox/output/threads-1/summary.txt"
    assert_file_exists "$sandbox/output/threads-4/summary.txt"
    assert_contains "$sandbox/README.md" "| Camp | Cerut | Tinta discreta | Obtinut |"
    pass "Isolated default run passed"
}

test_large_benchmark() {
    log_step "Testing large benchmark"
    local out_dir
    out_dir="$(run_app benchmark-large --publications=100000 --subscriptions=100000 --threads=1,4,8)"

    assert_dir_exists "$out_dir/threads-1"
    assert_dir_exists "$out_dir/threads-4"
    assert_dir_exists "$out_dir/threads-8"
    assert_contains "$out_dir/README.md" "| 8 |"
    pass "Large benchmark passed"
}

run_quick_suite() {
    compile_sources
    test_help
    test_small_functional
    test_edge_frequencies
    test_midrange_frequencies
    test_operator_coverage
    test_frequency_sum_above_100
    test_single_thread_baseline
    test_small_workload_many_threads
    test_small_regressions
    test_discrete_reporting
    test_seed_reproducibility
    test_parallel_consistency
}

run_full_suite() {
    run_quick_suite
    test_default_run_isolated
    test_large_benchmark
}

if [[ "$MODE" == "full" ]]; then
    run_full_suite
else
    run_quick_suite
fi

printf '\nAll automated tests passed.\n'
printf 'Artifacts are available in: %s\n' "$OUTPUT_ROOT"

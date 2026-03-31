#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

MODE="${1:-full}"

if [[ "$MODE" != "full" && "$MODE" != "quick" ]]; then
    echo "Usage: ./run_homework_checks.sh [full|quick]"
    exit 1
fi

log_step() {
    printf '\n== %s ==\n' "$1"
}

ok() {
    printf '[OK] %s\n' "$1"
}

fail() {
    printf '[ERROR] %s\n' "$1" >&2
    exit 1
}

require_file() {
    local path="$1"
    [[ -f "$path" ]] || fail "Lipseste fisierul: $path"
}

require_dir() {
    local path="$1"
    [[ -d "$path" ]] || fail "Lipseste directorul: $path"
}

require_text() {
    local path="$1"
    local expected="$2"
    grep -Fq -- "$expected" "$path" || fail "Nu am gasit textul asteptat in $path: $expected"
}

require_line_count() {
    local path="$1"
    local expected="$2"
    local actual
    actual="$(wc -l < "$path")"
    [[ "$actual" -eq "$expected" ]] || fail "Numar invalid de linii in $path: expected $expected, actual $actual"
}

require_no_field() {
    local path="$1"
    local field_name="$2"
    if grep -Fq "(${field_name}," "$path"; then
        fail "Am gasit campul $field_name in $path, desi ar trebui sa lipseasca."
    fi
}

require_all_lines_contain() {
    local path="$1"
    local field_name="$2"
    if grep -Fvq "(${field_name}," "$path"; then
        fail "Exista linii in $path care nu contin campul obligatoriu $field_name."
    fi
}

clean_generated_files() {
    rm -rf bin
    rm -rf output/threads-1 output/threads-4 output/threads-8
    rm -rf output/test-small output/test-edge output/test-benchmark
    rm -rf output/repro-1 output/repro-4
    rm -rf output/generator-default output/project-demo
    rm -f output/README.md
}

compile_project() {
    log_step "Compilare"
    mkdir -p bin output
    find src -name '*.java' -print0 | xargs -0 javac -d bin
    require_file "bin/homework/HomeworkApp.class"
    ok "Compilarea a trecut."
}

check_help_output() {
    log_step "Verificare help"
    local help_output
    help_output="$(java -cp bin homework.HomeworkApp --help 2>&1)"

    [[ "$help_output" == *"--mode=project|generator"* ]] || fail "Help-ul nu contine optiunea --mode."
    [[ "$help_output" == *"--publications=<numar>"* ]] || fail "Help-ul nu contine optiunea --publications."
    [[ "$help_output" == *"--subscriptions=<numar>"* ]] || fail "Help-ul nu contine optiunea --subscriptions."
    [[ "$help_output" == *"--threads=<lista separata prin virgula, ex. 1,4>"* ]] || fail "Help-ul nu contine optiunea --threads."
    [[ "$help_output" == *"--company-equals=<0..100>"* ]] || fail "Help-ul nu contine optiunea --company-equals."
    [[ "$help_output" == *"--output=<director>"* ]] || fail "Help-ul nu contine optiunea --output."
    ok "Help-ul este afisat corect."
}

run_default_case() {
    log_step "Rulare standard"
    java -cp bin homework.HomeworkApp --mode=generator --output=output/generator-default

    require_file "output/generator-default/README.md"
    require_file "output/generator-default/threads-1/publications.txt"
    require_file "output/generator-default/threads-1/subscriptions.txt"
    require_file "output/generator-default/threads-1/summary.txt"
    require_file "output/generator-default/threads-4/publications.txt"
    require_file "output/generator-default/threads-4/subscriptions.txt"
    require_file "output/generator-default/threads-4/summary.txt"

    require_line_count "output/generator-default/threads-1/publications.txt" 40000
    require_line_count "output/generator-default/threads-1/subscriptions.txt" 40000
    require_line_count "output/generator-default/threads-4/publications.txt" 40000
    require_line_count "output/generator-default/threads-4/subscriptions.txt" 40000

    require_text "output/generator-default/threads-4/summary.txt" "company: target 90%, actual 36000 (90.00%)"
    require_text "output/generator-default/threads-4/summary.txt" "value: target 70%, actual 28000 (70.00%)"
    require_text "output/generator-default/threads-4/summary.txt" "drop: target 55%, actual 22000 (55.00%)"
    require_text "output/generator-default/threads-4/summary.txt" "variation: target 65%, actual 26000 (65.00%)"
    require_text "output/generator-default/threads-4/summary.txt" "date: target 40%, actual 16000 (40.00%)"
    require_text "output/generator-default/threads-4/summary.txt" "Company equality actual: 25200 / 36000 (70.00%)"
    require_text "output/generator-default/README.md" "| 1 |"
    require_text "output/generator-default/README.md" "| 4 |"

    ok "Rularea standard este corecta."
}

run_small_case() {
    log_step "Test rapid pe set mic"
    java -cp bin homework.HomeworkApp \
        --mode=generator \
        --publications=1000 \
        --subscriptions=1000 \
        --threads=1,4 \
        --output=output/test-small

    require_file "output/test-small/README.md"
    require_file "output/test-small/threads-4/summary.txt"
    require_line_count "output/test-small/threads-4/publications.txt" 1000
    require_line_count "output/test-small/threads-4/subscriptions.txt" 1000
    require_text "output/test-small/threads-4/summary.txt" "Publications: 1000"
    require_text "output/test-small/threads-4/summary.txt" "Subscriptions: 1000"
    ok "Testul mic este corect."
}

run_edge_case() {
    log_step "Test de margine pentru procente"
    java -cp bin homework.HomeworkApp \
        --mode=generator \
        --publications=500 \
        --subscriptions=500 \
        --threads=1,2 \
        --company-frequency=100 \
        --value-frequency=100 \
        --drop-frequency=0 \
        --variation-frequency=0 \
        --date-frequency=0 \
        --company-equals=80 \
        --output=output/test-edge

    require_file "output/test-edge/threads-2/summary.txt"
    require_line_count "output/test-edge/threads-2/publications.txt" 500
    require_line_count "output/test-edge/threads-2/subscriptions.txt" 500
    require_text "output/test-edge/threads-2/summary.txt" "company: target 100%, actual 500 (100.00%)"
    require_text "output/test-edge/threads-2/summary.txt" "value: target 100%, actual 500 (100.00%)"
    require_text "output/test-edge/threads-2/summary.txt" "drop: target 0%, actual 0 (0.00%)"
    require_text "output/test-edge/threads-2/summary.txt" "variation: target 0%, actual 0 (0.00%)"
    require_text "output/test-edge/threads-2/summary.txt" "date: target 0%, actual 0 (0.00%)"
    require_text "output/test-edge/threads-2/summary.txt" "Company equality actual: 400 / 500 (80.00%)"
    require_all_lines_contain "output/test-edge/threads-2/subscriptions.txt" "company"
    require_all_lines_contain "output/test-edge/threads-2/subscriptions.txt" "value"
    require_no_field "output/test-edge/threads-2/subscriptions.txt" "drop"
    require_no_field "output/test-edge/threads-2/subscriptions.txt" "variation"
    require_no_field "output/test-edge/threads-2/subscriptions.txt" "date"
    ok "Testul de margine este corect."
}

run_benchmark_case() {
    log_step "Test de paralelizare"

    if [[ "$MODE" == "full" ]]; then
        java -cp bin homework.HomeworkApp \
            --mode=generator \
            --publications=100000 \
            --subscriptions=100000 \
            --threads=1,4,8 \
            --output=output/test-benchmark
    else
        java -cp bin homework.HomeworkApp \
            --mode=generator \
            --publications=20000 \
            --subscriptions=20000 \
            --threads=1,4,8 \
            --output=output/test-benchmark
    fi

    require_file "output/test-benchmark/README.md"
    require_file "output/test-benchmark/threads-1/summary.txt"
    require_file "output/test-benchmark/threads-4/summary.txt"
    require_file "output/test-benchmark/threads-8/summary.txt"
    require_text "output/test-benchmark/README.md" "| 1 |"
    require_text "output/test-benchmark/README.md" "| 4 |"
    require_text "output/test-benchmark/README.md" "| 8 |"
    ok "Benchmark-ul este corect."
}

run_reproducibility_case() {
    log_step "Test de reproductibilitate"
    java -cp bin homework.HomeworkApp \
        --mode=generator \
        --publications=2000 \
        --subscriptions=2000 \
        --threads=1 \
        --seed=123 \
        --output=output/repro-1

    java -cp bin homework.HomeworkApp \
        --mode=generator \
        --publications=2000 \
        --subscriptions=2000 \
        --threads=4 \
        --seed=123 \
        --output=output/repro-4

    diff -q output/repro-1/threads-1/publications.txt output/repro-4/threads-4/publications.txt >/dev/null \
        || fail "Publicatiile nu sunt identice pentru acelasi seed."
    diff -q output/repro-1/threads-1/subscriptions.txt output/repro-4/threads-4/subscriptions.txt >/dev/null \
        || fail "Subscriptiile nu sunt identice pentru acelasi seed."
    ok "Reproductibilitatea este corecta."
}

run_project_case() {
    log_step "Test proiect pub/sub"
    java -cp bin homework.HomeworkApp \
        --mode=project \
        --publications=1000 \
        --subscriptions=2000 \
        --evaluation-seconds=3 \
        --publish-interval-ms=10 \
        --output=output/project-demo

    require_file "output/project-demo/README.md"
    require_file "output/project-demo/scenario-equals-100/scenario-report.txt"
    require_file "output/project-demo/scenario-equals-25/scenario-report.txt"
    require_text "output/project-demo/README.md" "publisheri"
    require_text "output/project-demo/README.md" "brokeri"
    require_text "output/project-demo/README.md" "subscriberi"
    require_text "output/project-demo/README.md" "| equals-100 |"
    require_text "output/project-demo/README.md" "| equals-25 |"
    require_text "output/project-demo/scenario-equals-100/scenario-report.txt" "Average publication hops: 3.0000"
    require_text "output/project-demo/scenario-equals-25/scenario-report.txt" "Average publication hops: 3.0000"
    ok "Testul de proiect pub/sub este corect."
}

main() {
    log_step "Curatare artefacte generate"
    clean_generated_files
    ok "Curatarea a fost facuta."

    compile_project
    check_help_output
    run_default_case
    run_small_case
    run_edge_case
    run_benchmark_case
    run_reproducibility_case
    run_project_case

    printf '\nToate testele au trecut cu succes.\n'
}

main

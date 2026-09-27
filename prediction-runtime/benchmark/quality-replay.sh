#!/usr/bin/env bash
set -euo pipefail
# Run from the repository root after assembleDebug generated the Java app classes.
if [[ $# -lt 3 ]]; then echo "Usage: $0 model.gguf en.dict de.dict [tune|diagnostic|regression|holdout]" >&2; exit 2; fi
if command -v devbox-mem >/dev/null; then devbox-mem; fi
bench_dir="${PREDICTION_BENCH_DIR:-/tmp/keyboard-quality-bench}"
java_home="${JAVA_HOME:?Set JAVA_HOME to a JDK installation}"
mkdir -p "$bench_dir/java"
cmake -S prediction-runtime/benchmark -B "$bench_dir" -DCMAKE_BUILD_TYPE=Release
cmake --build "$bench_dir" --target score-server score-test word-boundary-test -j2
cc -shared -fPIC -O2 -I"$java_home/include" -I"$java_home/include/linux" -Ivendor/cdict/libcdict \
  vendor/cdict/libcdict/libcdict.c vendor/cdict/java/jni/juloo_cdict_Cdict.c -o "$bench_dir/java/libcdict_java.so"
app_classes=build/intermediates/javac/debug/compileDebugJavaWithJavac/classes
"$java_home/bin/javac" -cp "$app_classes" -d "$bench_dir/java" prediction-runtime/benchmark/QualityReplay.java
"$java_home/bin/java" -Djava.library.path="$bench_dir/java" -cp "$bench_dir/java:$app_classes" QualityReplay \
  "$bench_dir/score-server" "$1" "$2" "$3" prediction-runtime/benchmark/quality.tsv "${4:-holdout}"

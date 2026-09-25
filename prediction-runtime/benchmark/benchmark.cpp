#include "predictor_core.h"
extern "C" {
#include "libcdict.h"
}
#include <fstream>
#include <iostream>
#include <iterator>
#include <thread>

static bool hit(const std::vector<std::string> & words, const std::string & expected) {
    return std::find(words.begin(), words.end(), expected) != words.end();
}
static std::vector<std::string> dictionary_words(const cdict_t * dict, const std::string & prefix) {
    if (!dict) return {};
    cdict_result_t r; cdict_find(dict, prefix.data(), prefix.size(), &r);
    std::vector<int> ids;
    if (r.found) ids.push_back(r.index);
    int suffixes[3], dist[3];
    int ns = cdict_suffixes(dict, &r, suffixes, 3);
    int nd = prefix.size() < 3 || ids.size() + 1 >= 3 ? 0 : cdict_distance(dict, prefix.data(), prefix.size(), 1, dist, 3);
    for (int j = 0; j < 3 && ids.size() < 3; ++j) {
        if (j < ns) ids.push_back(suffixes[j]);
        if (j < nd && ids.size() < 3) ids.push_back(dist[j]);
    }
    std::vector<std::string> words;
    for (int id : ids) { char buf[256]; int n = cdict_word(dict, id, buf, 256); words.emplace_back(buf, n); }
    return words;
}
int main(int argc, char ** argv) {
    if (argc < 3) { std::cerr << "Usage: prediction-bench MODEL.gguf corpus.tsv [en.dict]\n"; return 2; }
    keyboard::Predictor predictor;
    auto load = keyboard::Clock::now();
    if (!predictor.load(argv[1])) { std::cerr << "Model load failed\n"; return 1; }
    auto load_ms = std::chrono::duration<double, std::milli>(keyboard::Clock::now() - load).count();
    cdict_header_t header; cdict_t dict; const cdict_t * dp = nullptr;
    std::string dictionary_data;
    if (argc > 3) {
        std::ifstream f(argv[3], std::ios::binary);
        dictionary_data.assign(std::istreambuf_iterator<char>(f), {});
        if (cdict_of_string(dictionary_data.data(), dictionary_data.size(), &header) != CDICT_OK) return 3;
        for (int i = 0; i < header.n_dicts; ++i) {
            cdict_get_dict(&header, i, &dict);
            if (std::string(dict.name) == "main") { dp = &dict; break; }
        }
    }
    std::ifstream corpus(argv[2]);
    std::string line;
    int cases = 0, next_hits = 0, completion_hits = 0, dictionary_hits = 0;
    int saved = 0, possible = 0;
    std::vector<double> timings;
    while (std::getline(corpus, line)) {
        if (line.empty() || line[0] == '#') continue;
        size_t tab = line.find('\t');
        if (tab == std::string::npos) return 4;
        std::string context = line.substr(0, tab), expected = line.substr(tab + 1);
        if (expected.size() < 3) return 4;
        predictor.reset();
        predictor.predict(context, "", 5000); // Warm this context; model weights stay loaded.
        auto begin = keyboard::Clock::now();
        auto next = predictor.predict(context, "");
        timings.push_back(std::chrono::duration<double, std::milli>(keyboard::Clock::now() - begin).count() + 50);
        std::string prefix = expected.substr(0, 2);
        begin = keyboard::Clock::now();
        auto completion = predictor.predict(context, prefix);
        timings.push_back(std::chrono::duration<double, std::milli>(keyboard::Clock::now() - begin).count() + 50);
        bool nh = hit(next, expected), ch = hit(completion, expected);
        next_hits += nh; completion_hits += ch;
        dictionary_hits += hit(dictionary_words(dp, prefix), expected);
        // End-of-field acceptance includes a space and costs one tap.
        saved += nh ? expected.size() : ch ? expected.size() - 2 : 0;
        possible += expected.size() + 1;
        ++cases;
    }
    if (cases == 0) return 4;
    // A cancellation during a cold prefill must never expose a result.
    predictor.reset();
    std::string long_context;
    for (int i = 0; i < 100; ++i) long_context += "This is a public benchmark sentence. ";
    std::vector<std::string> cancelled;
    std::thread running([&] { cancelled = predictor.predict(long_context, "", 5000); });
    std::this_thread::sleep_for(std::chrono::milliseconds(2));
    ++predictor.cancellation;
    running.join();
    if (!cancelled.empty()) { std::cerr << "Cancellation test failed\n"; return 5; }
    std::sort(timings.begin(), timings.end());
    std::cout << "{\"cases\":" << cases << ",\"load_ms\":" << load_ms
              << ",\"warm_p95_ms_including_debounce\":" << timings[static_cast<size_t>(std::ceil(timings.size() * .95)) - 1]
              << ",\"next_word_top3_hits\":" << next_hits << ",\"completion_top3_hits\":" << completion_hits
              << ",\"dictionary_top3_hits\":" << (dp ? dictionary_hits : -1)
              << ",\"simulated_keystrokes_saved\":" << saved << ",\"typed_keystrokes\":" << possible
              << ",\"cancellation_passed\":true}\n";
}

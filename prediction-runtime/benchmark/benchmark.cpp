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
static size_t utf8_prefix_end(const std::string & text, size_t characters) {
    size_t end = 0;
    while (end < text.size() && characters--) {
        ++end;
        while (end < text.size() && (static_cast<unsigned char>(text[end]) & 0xc0) == 0x80) ++end;
    }
    return end;
}
static size_t utf8_length(const std::string & text) {
    return std::count_if(text.begin(), text.end(), [](unsigned char c) { return (c & 0xc0) != 0x80; });
}
int main(int argc, char ** argv) {
    if (argc < 3) { std::cerr << "Usage: prediction-bench MODEL.gguf corpus.tsv [dictionary|-] [en|de]\n"; return 2; }
    std::string language = argc > 4 ? argv[4] : "";
    int word_budget = language.empty() ? 250 : 500;
    int phrase_budget = language.empty() ? 500 : 1000;
    keyboard::Predictor predictor;
    auto load = keyboard::Clock::now();
    if (!predictor.load(argv[1])) { std::cerr << "Model load failed\n"; return 1; }
    auto load_ms = std::chrono::duration<double, std::milli>(keyboard::Clock::now() - load).count();
    cdict_header_t header; cdict_t dict; const cdict_t * dp = nullptr;
    std::string dictionary_data;
    if (argc > 3 && std::string(argv[3]) != "-") {
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
    std::vector<double> timings, phrase_timings;
    int phrase_results = 0, cold_results = 0;
    std::vector<double> cold_timings;
    while (std::getline(corpus, line)) {
        if (line.empty() || line[0] == '#') continue;
        size_t tab = line.find('\t');
        if (tab == std::string::npos) return 4;
        std::string context = line.substr(0, tab), expected = line.substr(tab + 1);
        if (utf8_length(expected) < 3) return 4;
        predictor.reset();
        auto cold_begin = keyboard::Clock::now();
        auto cold = predictor.predict(context, "", word_budget, false, language);
        cold_results += !cold.empty();
        cold_timings.push_back(std::chrono::duration<double, std::milli>(keyboard::Clock::now() - cold_begin).count());
        predictor.predict(context, "", 5000, false, language); // Also measure warm context.
        auto begin = keyboard::Clock::now();
        auto next = predictor.predict(context, "", word_budget, false, language);
        timings.push_back(std::chrono::duration<double, std::milli>(keyboard::Clock::now() - begin).count() + 50);
        std::string prefix = expected.substr(0, utf8_prefix_end(expected, 2));
        begin = keyboard::Clock::now();
        auto completion = predictor.predict(context, prefix, word_budget, false, language);
        timings.push_back(std::chrono::duration<double, std::milli>(keyboard::Clock::now() - begin).count() + 50);
        bool nh = hit(next, expected), ch = hit(completion, expected);
        next_hits += nh; completion_hits += ch;
        dictionary_hits += hit(dictionary_words(dp, prefix), expected);
        // End-of-field acceptance includes a space and costs one tap.
        saved += nh ? utf8_length(expected) : ch ? utf8_length(expected) - 2 : 0;
        possible += utf8_length(expected) + 1;
        begin = keyboard::Clock::now();
        auto phrase = predictor.predict(context, "", phrase_budget, true, language);
        phrase_timings.push_back(std::chrono::duration<double, std::milli>(keyboard::Clock::now() - begin).count());
        if (!phrase.empty()) {
            ++phrase_results;
            if (cases < 5) std::cerr << "Phrase sample: " << context << "[" << phrase[0] << "]\n";
        }
        ++cases;
    }
    if (cases == 0) return 4;
    // A cancellation during a cold prefill must never expose a result.
    predictor.reset();
    std::string long_context;
    for (int i = 0; i < 100; ++i) long_context += "This is a public benchmark sentence. ";
    std::vector<std::string> cancelled;
    std::thread running([&] { cancelled = predictor.predict(long_context, "", 5000, false, language); });
    std::this_thread::sleep_for(std::chrono::milliseconds(2));
    ++predictor.cancellation;
    running.join();
    if (!cancelled.empty()) { std::cerr << "Cancellation test failed\n"; return 5; }
    std::sort(cold_timings.begin(), cold_timings.end());
    std::sort(timings.begin(), timings.end());
    std::sort(phrase_timings.begin(), phrase_timings.end());
    std::cout << "{\"cases\":" << cases << ",\"load_ms\":" << load_ms
              << ",\"warm_p95_ms_including_debounce\":" << timings[static_cast<size_t>(std::ceil(timings.size() * .95)) - 1]
              << ",\"next_word_top3_hits\":" << next_hits << ",\"completion_top3_hits\":" << completion_hits
              << ",\"dictionary_top3_hits\":" << (dp ? dictionary_hits : -1)
              << ",\"simulated_keystrokes_saved\":" << saved << ",\"typed_keystrokes\":" << possible
              << ",\"cold_word_results\":" << cold_results
              << ",\"cold_inference_p95_ms\":" << cold_timings[static_cast<size_t>(std::ceil(cold_timings.size() * .95)) - 1]
              << ",\"phrase_results\":" << phrase_results
              << ",\"phrase_inference_p95_ms\":" << phrase_timings[static_cast<size_t>(std::ceil(phrase_timings.size() * .95)) - 1]
              << ",\"cancellation_passed\":true}\n";
}

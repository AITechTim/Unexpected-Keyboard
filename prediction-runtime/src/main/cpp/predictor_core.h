#pragma once
#include "llama.h"
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <map>
#include <mutex>
#include <string>
#include <vector>

namespace keyboard {
using Clock = std::chrono::steady_clock;

// English word decoding. Non-ASCII bytes are retained and validated as Unicode
// letters by the Java layer, after the whole UTF-8 word has been assembled.
inline bool word_byte(unsigned char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '\'' || c >= 128;
}

// -1: invalid, 0: incomplete, 1: complete. A token may contain both the
// end of this word and the beginning of another; never expose the latter.
inline int word_state(const std::string & text, const std::string & required,
                      size_t spaces, std::string & word) {
    if (text.compare(0, std::min(text.size(), required.size()),
                     required, 0, std::min(text.size(), required.size())) != 0) return -1;
    if (text.size() <= spaces) return 0;
    size_t end = spaces;
    while (end < text.size() && word_byte(static_cast<unsigned char>(text[end]))) ++end;
    if (end == spaces || end - spaces > 64) return -1;
    if (end < required.size() && end < text.size()) return -1;
    word = text.substr(spaces, end - spaces);
    return end < text.size() ? 1 : 0;
}

// Phrase parsing preserves only words with a decoded following boundary.
// The unfinished final word is never shown, including on timeout.
inline int phrase_state(const std::string & text, const std::string & required,
                        size_t spaces, std::string & phrase) {
    phrase.clear();
    size_t match = std::min(text.size(), required.size());
    if (text.compare(0, match, required, 0, match) != 0) return -1;
    size_t at = spaces;
    int words = 0;
    while (at < text.size()) {
        size_t begin = at;
        while (at < text.size() && word_byte(static_cast<unsigned char>(text[at]))) ++at;
        if (at - begin > 64 || at == begin) return -1;
        if (at == text.size()) return 0;
        if (at < required.size()) return -1;
        if (words++) phrase += " ";
        phrase += text.substr(begin, at - begin);
        if (words == 5 || text[at] != ' ') return 1;
        ++at;
    }
    return 0;
}

struct Predictor {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    std::atomic<unsigned> cancellation{0};
    unsigned running = 0;
    Clock::time_point deadline;
    std::vector<llama_token> cached;
    std::vector<std::string> pieces;

    ~Predictor() { if (ctx) llama_free(ctx); if (model) llama_model_free(model); }
    static bool abort(void * p) {
        auto * self = static_cast<Predictor *>(p);
        return self->cancellation.load() != self->running || Clock::now() >= self->deadline;
    }
    void reset() {
        std::fill(cached.begin(), cached.end(), 0);
        cached.clear();
        if (ctx) llama_memory_clear(llama_get_memory(ctx), true);
    }
    bool load(const char * path) {
        static std::once_flag init;
        std::call_once(init, [] {
            // Never send tokens, prompts or native diagnostics to Android logs.
            llama_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
            llama_backend_init();
        });
        auto mp = llama_model_default_params();
        mp.n_gpu_layers = 0;
        mp.load_mode = LLAMA_LOAD_MODE_MMAP;
        model = llama_model_load_from_file(path, mp);
        if (!model) return false;
        vocab = llama_model_get_vocab(model);
        auto cp = llama_context_default_params();
        cp.n_ctx = 512;
        cp.n_batch = 256;
        cp.n_ubatch = 256;
        cp.n_seq_max = 13; // Root plus two alternating banks of six beams.
        cp.kv_unified = true;
        cp.n_threads = 2;
        cp.n_threads_batch = 2;
        cp.offload_kqv = false;
        cp.op_offload = false;
        cp.abort_callback = abort;
        cp.abort_callback_data = this;
        ctx = llama_init_from_model(model, cp);
        if (!ctx) return false;
        int size = llama_vocab_n_tokens(vocab);
        pieces.resize(size);
        for (int i = 0; i < size; ++i) {
            char buf[256];
            int n = llama_token_to_piece(vocab, i, buf, sizeof(buf), 0, false);
            if (n > 0) pieces[i].assign(buf, n);
        }
        return true;
    }
    bool decode(llama_batch & b) {
        if (abort(this) || llama_decode(ctx, b) != 0) { reset(); return false; }
        return true;
    }
    static void add(llama_batch & b, llama_token token, int pos, int seq, bool logits) {
        int i = b.n_tokens++;
        b.token[i] = token; b.pos[i] = pos; b.n_seq_id[i] = 1;
        b.seq_id[i][0] = seq; b.logits[i] = logits;
    }
    struct Beam { int seq; int row; std::string text; double score; };
    struct Extension { int parent; llama_token token; std::string text; double score; };

    std::vector<std::string> predict(std::string context, const std::string & prefix,
                                     int budget_ms = 250, bool phrase_mode = false) {
        if (!ctx) return {};
        running = cancellation.load();
        deadline = Clock::now() + std::chrono::milliseconds(budget_ms);
        // Retokenize from the preceding word boundary, including whitespace in
        // the constrained continuation. This handles BPE's leading-space tokens.
        size_t trim = context.find_last_not_of(" \t\r\n");
        size_t start = trim == std::string::npos ? 0 : trim + 1;
        std::string whitespace = context.substr(start);
        context.resize(start);
        std::string required = whitespace + prefix;
        if (required.size() > 64) return {};
        std::vector<llama_token> tokens(context.size() + 8);
        int n = llama_tokenize(vocab, context.data(), context.size(), tokens.data(),
                               tokens.size(), true, false);
        if (n < 0) return {};
        tokens.resize(n);
        if (tokens.empty()) tokens.push_back(llama_vocab_bos(vocab));
        if (tokens[0] < 0) return {};
        if (tokens.size() > 256) tokens.erase(tokens.begin(), tokens.end() - 256);
        auto mem = llama_get_memory(ctx);
        for (int seq = 1; seq < 13; ++seq) llama_memory_seq_rm(mem, seq, -1, -1);
        size_t common = 0;
        while (common < cached.size() && common < tokens.size() && cached[common] == tokens[common]) ++common;
        // Re-evaluate the last token to obtain logits even for identical input.
        common = std::min(common, tokens.size() - 1);
        llama_memory_seq_rm(mem, 0, common, -1);
        llama_batch batch = llama_batch_init(256, 0, 1);
        batch.n_tokens = 0;
        for (size_t i = common; i < tokens.size(); ++i)
            add(batch, tokens[i], i, 0, i + 1 == tokens.size());
        if (!decode(batch)) { llama_batch_free(batch); return {}; }
        cached = tokens;
        std::vector<Beam> beams{{0, batch.n_tokens - 1, "", 0}};
        std::map<std::string, double> finished;
        const int vocab_size = llama_vocab_n_tokens(vocab);
        std::string partial_phrase;
        const int depth_limit = phrase_mode ? 32 : 8;
        const size_t beam_limit = phrase_mode ? 1 : 6;
        for (int depth = 0; depth < depth_limit && !beams.empty() && !abort(this); ++depth) {
            std::vector<Extension> extensions;
            for (const auto & b : beams) {
                const float * logits = llama_get_logits_ith(ctx, b.row);
                float max = *std::max_element(logits, logits + vocab_size);
                double sum = 0;
                for (int t = 0; t < vocab_size; ++t) sum += std::exp(logits[t] - max);
                double norm = max + std::log(sum);
                // Inspect all tokens before pruning: rare typed prefixes must
                // not disappear merely because they are outside a top-k list.
                for (int t = 0; t < vocab_size; ++t) {
                    if ((t & 1023) == 0 && abort(this)) break;
                    if (pieces[t].empty() || llama_vocab_is_eog(vocab, t)) continue;
                    std::string text = b.text + pieces[t], word;
                    int state = phrase_mode ? phrase_state(text, required, whitespace.size(), word)
                                            : word_state(text, required, whitespace.size(), word);
                    if (state < 0) continue;
                    double score = b.score + logits[t] - norm;
                    if (state == 1 && !phrase_mode) {
                        if (word.size() <= prefix.size()) continue;
                        auto old = finished.find(word);
                        if (old == finished.end() || score > old->second) finished[word] = score;
                    } else {
                        if (extensions.size() < beam_limit || score > extensions.back().score) {
                            extensions.push_back({b.seq, t, std::move(text), score});
                            std::sort(extensions.begin(), extensions.end(), [](const Extension & a, const Extension & b) {
                                return a.score > b.score;
                            });
                            if (extensions.size() > beam_limit) extensions.pop_back();
                        }
                    }
                }
            }
            if (extensions.empty()) break;
            if (phrase_mode) {
                std::string complete;
                int state = phrase_state(extensions[0].text, required, whitespace.size(), complete);
                if (complete.find(' ') != std::string::npos) partial_phrase = complete;
                if (state == 1) break;
            }
            if (depth + 1 == depth_limit || abort(this)) break;
            // Once three completed words outrank every unfinished path, further
            // decoding cannot improve the result (log probabilities only fall).
            if (finished.size() >= 3) {
                std::vector<double> scores;
                for (auto & f : finished) scores.push_back(f.second);
                std::sort(scores.begin(), scores.end(), std::greater<double>());
                if (scores[2] >= extensions[0].score) break;
            }
            int bank = (depth % 2 == 0) ? 1 : 7;
            for (int s = bank; s < bank + 6; ++s) llama_memory_seq_rm(mem, s, -1, -1);
            batch.n_tokens = 0;
            beams.clear();
            for (size_t i = 0; i < extensions.size(); ++i) {
                auto & e = extensions[i];
                int seq = bank + i;
                llama_memory_seq_cp(mem, e.parent, seq, -1, -1);
                add(batch, e.token, tokens.size() + depth, seq, true);
                beams.push_back({seq, static_cast<int>(i), std::move(e.text), e.score});
            }
            if (!decode(batch)) break;
        }
        llama_batch_free(batch);
        if (cancellation.load() != running) return {};
        for (int seq = 1; seq < 13; ++seq) llama_memory_seq_rm(mem, seq, -1, -1);
        if (phrase_mode) return partial_phrase.empty() ? std::vector<std::string>{} : std::vector<std::string>{partial_phrase};
        std::vector<std::pair<std::string, double>> ranked(finished.begin(), finished.end());
        std::sort(ranked.begin(), ranked.end(), [](const auto & a, const auto & b) { return a.second > b.second; });
        std::vector<std::string> result;
        for (size_t i = 0; i < ranked.size() && i < 3; ++i) result.push_back(ranked[i].first);
        return result;
    }
};
}

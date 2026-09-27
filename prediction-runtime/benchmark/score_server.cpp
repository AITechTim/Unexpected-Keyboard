#include "predictor_core.h"
#include <iostream>
#include <sstream>

// Tab-separated request: language, context, prefix, candidate words. One response per request.
int main(int argc, char **argv) {
    if (argc != 2) return 2;
    keyboard::Predictor predictor;
    if (!predictor.load(argv[1])) return 3;
    std::string line;
    while (std::getline(std::cin, line)) {
        std::vector<std::string> fields;
        std::stringstream input(line);
        std::string field;
        while (std::getline(input, field, '\t')) fields.push_back(field);
        if (fields.size() < 4) return 4;
        std::vector<std::string> words(fields.begin() + 3, fields.end());
        predictor.reset();
        auto start = keyboard::Clock::now();
        auto baseline = predictor.predict(fields[1], fields[2], 500, false, fields[0]);
        double cold_baseline_ms = std::chrono::duration<double, std::milli>(keyboard::Clock::now() - start).count();
        predictor.predict(fields[1], fields[2], 5000, false, fields[0]);
        start = keyboard::Clock::now();
        baseline = predictor.predict(fields[1], fields[2], 500, false, fields[0]);
        double baseline_ms = std::chrono::duration<double, std::milli>(keyboard::Clock::now() - start).count();
        predictor.reset();
        start = keyboard::Clock::now();
        auto scores = predictor.score(fields[1], words, 500, fields[0]);
        double cold_score_ms = std::chrono::duration<double, std::milli>(keyboard::Clock::now() - start).count();
        predictor.score(fields[1], words, 5000, fields[0]);
        start = keyboard::Clock::now();
        scores = predictor.score(fields[1], words, 500, fields[0]);
        double score_ms = std::chrono::duration<double, std::milli>(keyboard::Clock::now() - start).count();
        std::cout << baseline_ms << '\t' << score_ms;
        for (double score : scores) std::cout << '\t' << score;
        std::cout << '\t';
        for (const auto &word : baseline) std::cout << word << ',';
        std::cout << '\t' << cold_baseline_ms << '\t' << cold_score_ms << std::endl;
    }
}

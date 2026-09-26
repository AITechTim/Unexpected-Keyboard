#include "predictor_core.h"
#include <cstdlib>
#include <iostream>

static void check(bool ok) { if (!ok) { std::cerr << "Word boundary test failed\n"; std::exit(1); } }
int main() {
    std::string word;
    using keyboard::word_state;
    check(word_state(" cof", " coffee", 1, word) == 0);
    check(word_state(" coffee", " cof", 1, word) == 0);
    check(word_state(" coffee tomorrow", " cof", 1, word) == 1 && word == "coffee");
    check(word_state(" tea ", " cof", 1, word) == -1);
    check(word_state("cof ", "coffee", 0, word) == -1);
    check(word_state("hello!", "", 0, word) == 1 && word == "hello");
    check(word_state("don't ", "don", 0, word) == 1 && word == "don't");
    check(word_state("caf\xc3", "café", 0, word) == 0);
    check(word_state("café ", "caf", 0, word) == 1 && word == "café");
    check(word_state(" \nhello!", " \n", 2, word) == 1 && word == "hello");
    check(word_state("\nsecret", " ", 1, word) == -1);
    check(word_state(std::string(65, 'a'), "", 0, word) == -1);
    using keyboard::phrase_state;
    check(phrase_state(" see you tomo", " s", 1, word) == 0 && word == "see you");
    check(phrase_state(" see you tomorrow!", " s", 1, word) == 1 && word == "see you tomorrow");
    check(phrase_state(" see you tomorrow\nsecret", " s", 1, word) == 1 && word == "see you tomorrow");
    check(phrase_state("one two three four five six", "", 0, word) == 1 && word == "one two three four five");
    check(phrase_state(" tea please ", " cof", 1, word) == -1);
    check(phrase_state(" café please ", " caf", 1, word) == 0 && word == "café please");
    check(phrase_state(" a ", " a", 1, word) == 0 && word == "a");
    std::cout << "19 word and phrase boundary checks passed\n";
}

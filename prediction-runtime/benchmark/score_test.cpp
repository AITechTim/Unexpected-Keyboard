#include "predictor_core.h"
#include <iostream>
#include <thread>
#include <stdexcept>

static void require(bool ok, const char *message) { if (!ok) throw std::runtime_error(message); }
static bool missing(const std::vector<double> &v) {
    return std::all_of(v.begin(),v.end(),[](double d){return std::isnan(d);});
}
int main(int argc,char **argv) {
    if (argc != 2) return 2;
    keyboard::Predictor p;
    require(p.load(argv[1]),"load");
    std::vector<std::string> words={"turn","turbine","Turkish","turns"};
    auto a=p.score("How do I ",words,5000,"en");
    require(a.size()==4 && std::all_of(a.begin(),a.end(),[](double d){return std::isfinite(d);}),"complete scores");
    require(a[0]>a[1] && a[0]>a[2] && a[0]>a[3],"verb wins");
    auto warm=p.score("How do I ",words,5000,"en");
    require(warm[0]>warm[1] && warm[0]>warm[3],"cached verb wins");
    std::reverse(words.begin(),words.end());
    auto reversed=p.score("How do I ",words,5000,"en");
    require(reversed[3]>reversed[0] && reversed[3]>reversed[1],"candidate order does not choose winner");
    auto compounds=p.score("Das ist ein ",{"Baus","Baut","Bauen","Bauten","Bauliche","Baulichen","Bauweise","Bauwerk","Baum","Bau"},5000,"de");
    require(std::isfinite(compounds[8]),"shared prefixes cannot starve a short word");
    auto german=p.score("Wir ",{"gehen","geht","gehe","Gehirn"},5000,"de");
    require(german[0]>german[1] && german[0]>german[2],"German agreement and language cache");
    require(missing(p.score("hello ",words,0,"en")),"zero deadline yields no partial ranking");
    std::string long_context;for(int i=0;i<100;i++)long_context+="This is public test context. ";
    p.reset();std::vector<double> cancelled;
    std::thread run([&]{cancelled=p.score(long_context,words,5000,"en");});
    std::this_thread::sleep_for(std::chrono::milliseconds(2));++p.cancellation;run.join();
    require(missing(cancelled),"cancelled scoring yields no partial ranking");
    require(missing(p.score("",std::vector<std::string>(49,"word"),5000,"en")),"bounded input");
    std::cout<<"9 scoring checks passed\n";
}

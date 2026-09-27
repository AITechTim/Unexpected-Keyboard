import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import juloo.cdict.Cdict;
import juloo.keyboard2.prediction.*;

/** Runs production Java lexical selection/ranking with actual cdict files and the native model. */
public final class QualityReplay {
  static Map<Character,String> neighbors(String path) throws Exception {
    Map<Character,float[]> positions = new LinkedHashMap<>();
    NodeList rows = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(path)).getElementsByTagName("row");
    float y=0;
    for(int r=0;r<rows.getLength();r++) {
      Element row=(Element)rows.item(r); float height=value(row,"height",1); y+=value(row,"shift",0); float x=0;
      NodeList keys=row.getElementsByTagName("key");
      for(int k=0;k<keys.getLength();k++) {
        Element key=(Element)keys.item(k); x+=value(key,"shift",0); float width=value(key,"width",1);
        String c=key.getAttribute("c");
        if(c.length()==1 && Character.isLetter(c.charAt(0))) positions.put(c.charAt(0),new float[]{x+width/2,y+height/2});
        x+=width;
      }
      y+=height;
    }
    return KeyNeighbors.fromPositions(positions);
  }
  static float value(Element e,String name,float fallback) { String s=e.getAttribute(name); return s.isEmpty()?fallback:Float.parseFloat(s); }
  static Cdict dict(String path) throws Exception {
    for(Cdict d:Cdict.of_bytes(Files.readAllBytes(Paths.get(path)))) if(d.name.equals("main")) return d;
    throw new IOException("main dictionary missing");
  }
  static void addBaseline(Set<String> words,String text) {
    if(words.size()==3)return;
    for(String word:words)if(word.equalsIgnoreCase(text))return;
    words.add(text);
  }
  static double p95(List<Double> times) { Collections.sort(times);return times.get((int)Math.ceil(times.size()*.95)-1); }
  public static void main(String[] args) throws Exception {
    // server model en.dict de.dict corpus split
    Cdict en=dict(args[2]), de=dict(args[3]);
    Map<Character,String> enKeys=neighbors("srcs/layouts/latn_qwerty_us.xml"), deKeys=neighbors("srcs/layouts/latn_qwertz_de.xml");
    Process server=new ProcessBuilder(args[0],args[1]).redirectError(ProcessBuilder.Redirect.INHERIT).start();
    try (BufferedWriter out=new BufferedWriter(new OutputStreamWriter(server.getOutputStream(),StandardCharsets.UTF_8));
         BufferedReader in=new BufferedReader(new InputStreamReader(server.getInputStream(),StandardCharsets.UTF_8))) {
      int cases=0, first=0, top3=0, old3=0, missing=0, timeouts=0;
      List<Double> oldTimes=new ArrayList<>(),newTimes=new ArrayList<>(),lexTimes=new ArrayList<>(), coldOld=new ArrayList<>(), coldNew=new ArrayList<>();
      for(String line:Files.readAllLines(Paths.get(args[4]),StandardCharsets.UTF_8)) {
        if(line.startsWith("#")||line.isEmpty())continue;
        String[] f=line.split("\t",-1);if(!f[0].equals(args[5]))continue;
        Cdict dictionary=f[1].equals("en")?en:de;
        long t=System.nanoTime();
        WordCandidate[] pool=LexicalCandidates.find(f[3],new CdictLexicon(dictionary),f[1].equals("en")?enKeys:deKeys);
        double lexMs=(System.nanoTime()-t)/1e6;lexTimes.add(lexMs);
        if(pool.length==0){System.out.println("NO_POOL\t"+line);missing++;continue;}
        out.write(f[1]+"\t"+f[2]+"\t"+f[3]);for(WordCandidate c:pool)out.write("\t"+c.text);out.newLine();out.flush();
        String reply=in.readLine();if(reply==null)throw new IOException("scorer exited");
        String[] result=reply.split("\t",-1);
        double[] scores=new double[pool.length];for(int i=0;i<scores.length;i++)scores[i]=result[i+2].equals("nan")?Double.NaN:result[i+2].equals("-inf")?Double.NEGATIVE_INFINITY:Double.parseDouble(result[i+2]);
        boolean incomplete=false;for(double score:scores)incomplete|=Double.isNaN(score);if(incomplete)timeouts++;
        String[] ranked=CandidateRanking.rank(pool,scores);
        LinkedHashSet<String> baseline=new LinkedHashSet<>();
        PredictionSnapshot snapshot=new PredictionSnapshot(1,f[2]+f[3],"",f[2].length()+f[3].length());
        for(String w:result[2+pool.length].split(","))if(snapshot.validWord(w))addBaseline(baseline,w);
        String alias=CdictLexicon.aliases(f[3]);Cdict.Result dr=dictionary.find(alias);
        List<Integer> ids=new ArrayList<>();if(dr.found)ids.add(dr.index);
        int[] suffix=dictionary.suffixes(dr,3),edit=f[3].length()<3||ids.size()+1>=3?new int[0]:dictionary.distance(alias,1,3);
        for(int i=0;i<3&&ids.size()<3;i++){if(i<suffix.length)ids.add(suffix[i]);if(i<edit.length&&ids.size()<3)ids.add(edit[i]);}
        for(int id:ids) {
          String w=dictionary.word(id);
          if(Character.isUpperCase(f[3].charAt(0)))w=w.substring(0,1).toUpperCase()+w.substring(1);
          addBaseline(baseline,w);
        }
        cases++;if(ranked[0].equals(f[4]))first++;if(Arrays.asList(ranked).contains(f[4]))top3++;if(baseline.contains(f[4]))old3++;
        oldTimes.add(Double.parseDouble(result[0])+50);newTimes.add(Double.parseDouble(result[1])+lexMs+50);
        coldOld.add(Double.parseDouble(result[result.length-2]));coldNew.add(Double.parseDouble(result[result.length-1]));
        StringBuilder evidence=new StringBuilder();for(int i=0;i<pool.length;i++)evidence.append(pool[i].text+":"+pool[i].frequency+":"+pool[i].edits+":"+scores[i]+",");
        System.out.println(f[1]+"\t"+f[2]+f[3]+"\t"+f[4]+"\t"+String.join(",",ranked)+"\told="+baseline+"\t"+evidence);
      }
      System.out.println("SUMMARY cases="+cases+" top1="+first+" top3="+top3+" baselineTop3="+old3+" noPool="+missing+" incomplete="+timeouts+" oldP95="+p95(oldTimes)+" newP95="+p95(newTimes)+" lexicalP95="+p95(lexTimes)+" coldOldP95="+p95(coldOld)+" coldNewP95="+p95(coldNew));
    } finally {server.destroy();server.waitFor();}
  }
}

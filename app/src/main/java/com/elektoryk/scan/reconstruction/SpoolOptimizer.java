package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.CableCutList;
import com.elektoryk.scan.model.CableCutPiece;
import java.util.*;

/** First-fit decreasing, independently per cable type. */
public class SpoolOptimizer {
    public void optimize(CableCutList list,double rollM){
        if(list==null)return;list.sourceRollM=Math.max(10,rollM);list.estimatedWasteM=0;
        Map<String,List<CableCutPiece>> byCable=new LinkedHashMap<>();for(CableCutPiece p:list.pieces){p.spool="";List<CableCutPiece> l=byCable.get(p.cableKey);if(l==null){l=new ArrayList<>();byCable.put(p.cableKey,l);}l.add(p);}
        for(Map.Entry<String,List<CableCutPiece>> e:byCable.entrySet()){
            List<CableCutPiece> pieces=e.getValue();Collections.sort(pieces,new Comparator<CableCutPiece>(){@Override public int compare(CableCutPiece a,CableCutPiece b){return Double.compare(b.cutLengthM,a.cutLengthM);}});
            List<Double> remaining=new ArrayList<>();for(CableCutPiece p:pieces){int chosen=-1;for(int i=0;i<remaining.size();i++)if(remaining.get(i)+1e-9>=p.cutLengthM){chosen=i;break;}if(chosen<0){remaining.add(list.sourceRollM);chosen=remaining.size()-1;}if(p.cutLengthM>list.sourceRollM){p.spool=e.getKey()+" • LONG-"+(chosen+1);remaining.set(chosen,0.0);}else{remaining.set(chosen,remaining.get(chosen)-p.cutLengthM);p.spool=e.getKey()+" • B"+(chosen+1);}}
            for(double r:remaining)list.estimatedWasteM+=Math.max(0,r);
        }
    }
}

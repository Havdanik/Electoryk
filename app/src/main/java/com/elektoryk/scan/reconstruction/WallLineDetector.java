package com.elektoryk.scan.reconstruction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Finds long axis-aligned vertical-surface runs after room-axis normalization. */
class WallLineDetector {
    static class Segment {
        boolean horizontal; // horizontal: X changes, fixed Z row. vertical: Z changes, fixed X col.
        int fixed;
        int start;
        int end;
        double support;

        int lengthCells(){ return Math.max(0,end-start+1); }
    }

    List<Segment> detect(ApartmentGrid g){
        List<Segment> all=new ArrayList<>();
        if(g==null)return all;

        // IMPORTANT: extract center-lines from the wall evidence itself. Older builds
        // expanded every wall cell to r-1/r/r+1 (or c-1/c/c+1) before line extraction.
        // On a 10 cm grid that turns one physical wall into two or three parallel
        // Digital-Twin walls 10-30 cm apart. Neighbour evidence belongs in confidence/
        // cleanup, not in the center-line coordinate.
        for(int r=0;r<g.rows;r++){
            boolean[] line=new boolean[g.cols];
            for(int c=0;c<g.cols;c++) line[c]=g.isWall(r,c);
            collectRuns(all,true,r,line,4,1);
        }
        for(int c=0;c<g.cols;c++){
            boolean[] line=new boolean[g.rows];
            for(int r=0;r<g.rows;r++) line[r]=g.isWall(r,c);
            collectRuns(all,false,c,line,4,1);
        }

        Collections.sort(all,new Comparator<Segment>(){
            @Override public int compare(Segment a,Segment b){
                return Integer.compare(b.lengthCells(),a.lengthCells());
            }
        });

        List<Segment> out=new ArrayList<>();
        for(Segment s:all){
            boolean duplicate=false;
            for(Segment k:out){
                if(k.horizontal!=s.horizontal)continue;
                // One wall surface may occupy 2-3 adjacent 10 cm cells because of
                // depth noise / finite wall thickness. Collapse that band to ONE wall.
                // Distinct parallel room boundaries are normally much farther apart.
                if(Math.abs(k.fixed-s.fixed)>2)continue;
                int overlap=Math.min(k.end,s.end)-Math.max(k.start,s.start)+1;
                int shorter=Math.min(k.lengthCells(),s.lengthCells());
                if(overlap>0 && overlap>=shorter*0.60){
                    duplicate=true;
                    break;
                }
            }
            if(!duplicate)out.add(s);
        }
        return out;
    }

    private void collectRuns(List<Segment> out,boolean horizontal,int fixed,
                             boolean[] line,int minCells,int toleratedGap){
        int i=0;
        while(i<line.length){
            while(i<line.length&&!line[i])i++;
            if(i>=line.length)break;
            int start=i,lastTrue=i,gaps=0,trueCount=0;
            while(i<line.length){
                if(line[i]){
                    trueCount++;
                    lastTrue=i;
                    gaps=0;
                }else{
                    gaps++;
                    if(gaps>toleratedGap)break;
                }
                i++;
            }
            int end=lastTrue;
            int len=end-start+1;
            if(len>=minCells && trueCount>=Math.max(3,(int)Math.ceil(len*0.55))){
                Segment s=new Segment();
                s.horizontal=horizontal;s.fixed=fixed;s.start=start;s.end=end;
                s.support=(double)trueCount/len;
                out.add(s);
            }
            if(i<=end)i=end+1;
        }
    }

    boolean[][] barrierMap(ApartmentGrid g,List<Segment> segments){
        boolean[][] barrier=new boolean[g.rows][g.cols];
        for(Segment s:segments){
            if(s.horizontal){
                for(int c=s.start;c<=s.end;c++)if(g.inside(s.fixed,c))barrier[s.fixed][c]=true;
            }else{
                for(int r=s.start;r<=s.end;r++)if(g.inside(r,s.fixed))barrier[r][s.fixed]=true;
            }
        }
        return barrier;
    }
}

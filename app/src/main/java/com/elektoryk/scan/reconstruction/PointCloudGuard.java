package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.CloudPoint;
import java.util.ArrayList;
import java.util.List;

public final class PointCloudGuard {
    private PointCloudGuard(){}

    public static List<CloudPoint> reduce(List<CloudPoint> input,int maxPoints){
        if(input==null)return new ArrayList<>();
        if(maxPoints<=0||input.size()<=maxPoints)return new ArrayList<>(input);
        List<CloudPoint> out=new ArrayList<>(maxPoints);
        double step=input.size()/(double)maxPoints;
        double cursor=0;
        while((int)cursor<input.size()&&out.size()<maxPoints){
            int start=(int)cursor;
            int end=Math.min(input.size(),Math.max(start+1,(int)(cursor+step)));
            CloudPoint best=input.get(start);
            for(int i=start+1;i<end;i++)if(input.get(i).confidence>best.confidence)best=input.get(i);
            out.add(best);
            cursor+=step;
        }
        return out;
    }
}

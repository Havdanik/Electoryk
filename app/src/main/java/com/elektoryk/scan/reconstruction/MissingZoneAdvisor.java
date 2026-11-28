package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.ApartmentOpening;
import com.elektoryk.scan.model.AreaCheckResult;
import com.elektoryk.scan.model.MissingZoneHint;
import com.elektoryk.scan.model.SceneSnapshot;

import java.util.ArrayList;
import java.util.List;

public class MissingZoneAdvisor {
    public void apply(SceneSnapshot scene){
        if(scene==null||scene.apartmentPlan==null)return;
        scene.apartmentPlan.missingZoneHints.clear();

        List<ApartmentOpening> candidates=new ArrayList<>();
        for(ApartmentOpening o:scene.apartmentPlan.openings){
            if(!o.isWalkable())continue;
            boolean oneSide=o.roomAId.isEmpty() ^ o.roomBId.isEmpty();
            if(oneSide && !o.external) candidates.add(o);
        }

        double deficit=scene.areaCheck==null?0:scene.areaCheck.likelyMissingAreaM2;
        int n=Math.max(1,candidates.size());
        for(ApartmentOpening o:candidates){
            MissingZoneHint h=new MissingZoneHint();
            h.x=o.centerX();h.z=o.centerZ();
            h.relatedRoomId=o.roomAId.isEmpty()?o.roomBId:o.roomAId;
            h.confidence=Math.min(0.94,o.confidence+(o.crossedByScanPath?0.08:0));
            if(deficit>0.5){
                double share=deficit/n;
                h.estimatedAreaMinM2=Math.max(1.0,share*0.55);
                h.estimatedAreaMaxM2=Math.max(h.estimatedAreaMinM2+0.5,share*1.45);
            }
            h.reason=o.crossedByScanPath
                    ?"Внутрішній дверний прохід перетинався траєкторією скану, але за ним не сформувалась надійна кімната."
                    :"Є внутрішній дверний прохід, але з одного боку недостатньо відновленої підлоги.";
            scene.apartmentPlan.missingZoneHints.add(h);
        }
    }
}

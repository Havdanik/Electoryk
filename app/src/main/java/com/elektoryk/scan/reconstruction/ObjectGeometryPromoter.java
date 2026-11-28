package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.FurnitureItem;
import com.elektoryk.scan.model.ReviewIssue;
import com.elektoryk.scan.model.RoomZone;
import com.elektoryk.scan.model.RoomFootprint;

import java.util.List;

/** Keeps large interior depth clusters as objects instead of silently turning them into walls. */
public final class ObjectGeometryPromoter {
    public void apply(ApartmentPlan plan,List<ReviewIssue> issues){
        if(plan==null||issues==null)return;
        int added=0;
        for(ReviewIssue r:issues){
            if(r==null||r.suggestedType==ReviewIssue.SuggestedType.SCAN_ARTIFACT)continue;
            if(r.supportPoints<24||r.widthM()<0.12||r.heightM()<0.12||r.depthM()<0.08)continue;
            if(r.widthM()>2.8||r.depthM()>2.8||r.heightM()>2.8)continue;
            FurnitureItem f=new FurnitureItem();f.type=FurnitureItem.Type.CUSTOM;f.name="Нерозпізнаний об'єкт";f.assetKey="SCANNED_OBJECT";
            f.x=(r.minX+r.maxX)/2.0;f.y=Math.max(0,r.minY-plan.floorY);f.z=(r.minZ+r.maxZ)/2.0;
            f.widthM=Math.max(.10,r.widthM());f.depthM=Math.max(.10,r.depthM());f.heightM=Math.max(.10,r.heightM());
            RoomZone room=roomAt(plan,f.x,f.z);if(room!=null)f.roomId=room.id;
            f.blocksElectricalPlacement=true;plan.furniture.add(f);added++;if(added>=18)break;
        }
    }
    private RoomZone roomAt(ApartmentPlan p,double x,double z){
        for(RoomZone r:p.rooms)if(RoomFootprint.contains(p,r,x,z))return r;return null;
    }
}

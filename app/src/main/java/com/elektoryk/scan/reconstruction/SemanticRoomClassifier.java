package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.ApartmentPlan;
import com.elektoryk.scan.model.RoomZone;
import com.elektoryk.scan.model.RoomFootprint;
import com.elektoryk.scan.model.SemanticEntity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Room naming from objects that were actually recognized inside each reconstructed room. */
public final class SemanticRoomClassifier {
    public void apply(ApartmentPlan p,List<SemanticEntity> entities){
        if(p==null||entities==null)return;
        for(RoomZone r:p.rooms){
            Map<RoomZone.RoomType,Double> score=new EnumMap<>(RoomZone.RoomType.class);
            for(SemanticEntity e:entities){
                if(e==null||e.confidence<.45)continue;double x=e.centerX(),z=e.centerZ();if(!RoomFootprint.contains(p,r,x,z))continue;
                switch(e.type){
                    case BED:add(score,RoomZone.RoomType.BEDROOM,3.0);break;
                    case TOILET:case SHOWER:case BATHTUB:add(score,RoomZone.RoomType.BATHROOM,4.0);break;
                    case SINK:add(score,RoomZone.RoomType.BATHROOM,1.4);add(score,RoomZone.RoomType.KITCHEN,1.0);break;
                    case REFRIGERATOR:case OVEN:case MICROWAVE:case STOVE:case DISHWASHER:add(score,RoomZone.RoomType.KITCHEN,3.0);break;
                    case SOFA:case ARMCHAIR:case TELEVISION:add(score,RoomZone.RoomType.LIVING_ROOM,2.0);break;
                    case DESK:case COMPUTER:add(score,RoomZone.RoomType.OFFICE,2.5);break;
                    case WARDROBE:add(score,RoomZone.RoomType.WARDROBE,1.4);break;
                    default:break;
                }
            }
            RoomZone.RoomType best=RoomZone.RoomType.UNKNOWN;double bs=0;for(Map.Entry<RoomZone.RoomType,Double> e:score.entrySet())if(e.getValue()>bs){bs=e.getValue();best=e.getKey();}
            if(best!=RoomZone.RoomType.UNKNOWN&&bs>=2.0){r.type=best;r.confidence=Math.max(r.confidence,Math.min(.96,.55+bs*.08));}
            else if(r.narrowPassageLike&&r.type==RoomZone.RoomType.UNKNOWN){r.type=RoomZone.RoomType.HALL;r.confidence=Math.max(r.confidence,.62);}
        }
    }
    private void add(Map<RoomZone.RoomType,Double> m,RoomZone.RoomType t,double v){m.put(t,(m.containsKey(t)?m.get(t):0.0)+v);}
}

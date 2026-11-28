package com.elektoryk.scan.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class MountingSegment {
    public enum Kind { CEILING, WALL_VERTICAL, WALL_HORIZONTAL, FLOOR, PENETRATION }

    public String id=UUID.randomUUID().toString();
    public Kind kind=Kind.CEILING;
    public double x1,y1,z1,x2,y2,z2;
    public String wallId="";
    public final Set<Integer> circuits=new LinkedHashSet<>();
    public int cableCount;
    public String protection="NONE";
    public boolean shared;

    public double lengthM(){
        double dx=x2-x1,dy=y2-y1,dz=z2-z1;
        return Math.sqrt(dx*dx+dy*dy+dz*dz);
    }
}

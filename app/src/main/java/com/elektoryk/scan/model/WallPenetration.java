package com.elektoryk.scan.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class WallPenetration {
    public String id=UUID.randomUUID().toString();
    public String wallId="";
    public double x,y,z;
    public double wallThicknessM;
    public double extraCableM;
    public String method="AUTO";
    public final Set<Integer> circuits=new LinkedHashSet<>();
}

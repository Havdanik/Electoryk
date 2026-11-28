package com.elektoryk.scan.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class InstallationMaterialEstimate {
    public final Map<String,Double> cableM=new LinkedHashMap<>();
    public double corrugatedConduitM;
    public double trayM;
    public double wallChaseM;
    public double ceilingChaseM;
    public double floorRouteM;
    public int wallPenetrations;
    public int fasteners;
    public int deepSocketBoxes;
    public int junctionBoxes;
    public double bundledCorridorM;
    public double unbundledCablePathM;
}

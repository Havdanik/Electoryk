package com.elektoryk.scan.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CircuitAnalysis {
    public int circuitNo;
    public int pointCount;
    public int moduleCount;
    public double plannedLoadW;
    public final Set<String> cableKeys=new LinkedHashSet<>();
    public final Set<String> roomIds=new LinkedHashSet<>();
    public final List<String> warnings=new ArrayList<>();
}

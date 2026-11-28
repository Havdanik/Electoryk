package com.elektoryk.scan.model;

import java.util.ArrayList;
import java.util.List;

public class ElectricalDesignReport {
    public int score;
    public int panelCount;
    public int totalPoints;
    public int unassignedCircuitPoints;
    public int unboundRoomPoints;
    public int unboundWallPoints;
    public int duplicateCandidates;
    public double totalPlannedLoadW;
    public final List<CircuitAnalysis> circuits=new ArrayList<>();
    public final List<String> warnings=new ArrayList<>();
}

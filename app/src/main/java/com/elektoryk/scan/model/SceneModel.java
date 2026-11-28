package com.elektoryk.scan.model;

import java.util.ArrayList;
import java.util.List;

/** Separates raw observations from reviewed architectural geometry. */
public class SceneModel {
    public final List<SurfaceObservation> raw = new ArrayList<>();
    public final List<SurfaceObservation> architecture = new ArrayList<>();
    public final List<SurfaceObservation> temporaryObjects = new ArrayList<>();
    public final List<SurfaceObservation> artifacts = new ArrayList<>();
    public final List<ElectricalPoint> electricalPoints = new ArrayList<>();

    public double scaleCorrection = 1.0;
    public boolean calibrated;
}

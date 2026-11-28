package com.elektoryk.scan.model;

import java.util.ArrayList;
import java.util.List;

public class InstallationRoutingResult {
    public final List<CableRoute> circuitRoutes=new ArrayList<>();
    public final List<MountingSegment> mountingSegments=new ArrayList<>();
    public final List<WallPenetration> penetrations=new ArrayList<>();
    public final List<String> warnings=new ArrayList<>();
    public final InstallationMaterialEstimate materials=new InstallationMaterialEstimate();

    public double totalRawCableM;
    public double totalPurchaseCableM;
}

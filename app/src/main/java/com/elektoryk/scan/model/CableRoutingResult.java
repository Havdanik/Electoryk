package com.elektoryk.scan.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CableRoutingResult {
    public final List<CableRoute> routes=new ArrayList<>();
    public final Map<String,Double> purchaseByCable=new LinkedHashMap<>();
    public final List<String> warnings=new ArrayList<>();
    public double totalRawLengthM;
    public double totalPurchaseLengthM;
}

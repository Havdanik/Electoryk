package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

public class PlanGridRun {
    public int row;
    public int startCol;
    public int endCol;

    public PlanGridRun() {}

    public PlanGridRun(int row, int startCol, int endCol) {
        this.row = row;
        this.startCol = startCol;
        this.endCol = endCol;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("row", row);
        o.put("startCol", startCol);
        o.put("endCol", endCol);
        return o;
    }

    public static PlanGridRun fromJson(JSONObject o) {
        if (o == null) return null;
        PlanGridRun r = new PlanGridRun();
        r.row = o.optInt("row", 0);
        r.startCol = o.optInt("startCol", 0);
        r.endCol = o.optInt("endCol", r.startCol);
        return r;
    }
}

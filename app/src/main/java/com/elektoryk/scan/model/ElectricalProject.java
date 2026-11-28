package com.elektoryk.scan.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class ElectricalProject {
    public String id = UUID.randomUUID().toString();
    public String name = "";
    public String address = "";
    /** Reference area entered by the electrician/client. Kept under the old JSON key too. */
    public double approximateAreaM2 = 0.0;
    public int roomCount = 0;
    public long createdAt = System.currentTimeMillis();
    public long updatedAt = createdAt;
    public ProjectSettings settings = new ProjectSettings();
    public ProjectProfile profile = new ProjectProfile();

    public String displayName() {
        if (name != null && !name.trim().isEmpty() && !"Новий об'єкт".equals(name)) return name.trim();
        StringBuilder s = new StringBuilder(profile.objectType.label);
        if (approximateAreaM2 > 0) s.append(" • ").append(formatArea(approximateAreaM2)).append(" м²");
        return s.toString();
    }

    public String autoName() {
        String date = new SimpleDateFormat("dd.MM", Locale.getDefault()).format(new Date(createdAt));
        return displayName() + " • " + date;
    }

    private String formatArea(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.05) return String.valueOf((int)Math.rint(v));
        return String.format(Locale.US, "%.1f", v);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("address", address);
        o.put("approximateAreaM2", approximateAreaM2);
        o.put("referenceAreaM2", approximateAreaM2);
        o.put("roomCount", roomCount);
        o.put("createdAt", createdAt);
        o.put("updatedAt", updatedAt);
        o.put("settings", settings.toJson());
        o.put("profile", profile.toJson());
        return o;
    }

    public static ElectricalProject fromJson(JSONObject o) {
        ElectricalProject p = new ElectricalProject();
        p.id = o.optString("id", p.id);
        p.name = o.optString("name", "");
        p.address = o.optString("address", "");
        p.approximateAreaM2 = o.has("referenceAreaM2")
                ? o.optDouble("referenceAreaM2", 0.0)
                : o.optDouble("approximateAreaM2", 0.0);
        p.roomCount = o.optInt("roomCount", 0);
        p.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        p.updatedAt = o.optLong("updatedAt", p.createdAt);
        p.settings = ProjectSettings.fromJson(o.optJSONObject("settings"));
        p.profile = ProjectProfile.fromJson(o.optJSONObject("profile"));
        return p;
    }
}

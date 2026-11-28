package com.elektoryk.scan.scanner;

public class CoverageGrid {
    private final boolean[][] bins = new boolean[12][5];
    private int visited;

    public void reset() {
        for (int x = 0; x < bins.length; x++) {
            for (int y = 0; y < bins[x].length; y++) bins[x][y] = false;
        }
        visited = 0;
    }

    public int add(float yawDeg, float pitchDeg) {
        float yaw = yawDeg % 360f;
        if (yaw < 0) yaw += 360f;
        float pitch = Math.max(-60f, Math.min(60f, pitchDeg));

        int xi = Math.min(11, (int)(yaw / 30f));
        int yi = Math.min(4, Math.max(0, (int)((pitch + 60f) / 24f)));

        if (!bins[xi][yi]) {
            bins[xi][yi] = true;
            visited++;
        }
        return percent();
    }

    public int percent() {
        return Math.round(visited * 100f / (12f * 5f));
    }
}

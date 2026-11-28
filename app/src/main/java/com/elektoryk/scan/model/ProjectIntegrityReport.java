package com.elektoryk.scan.model;

import java.util.ArrayList;
import java.util.List;

public class ProjectIntegrityReport {
    public enum Severity { OK, WARNING, ERROR }

    public Severity severity = Severity.OK;
    public int score = 100;
    public final List<String> issues = new ArrayList<>();
    public final List<String> repaired = new ArrayList<>();

    public void warning(String message) {
        issues.add(message);
        if (severity == Severity.OK) severity = Severity.WARNING;
        score = Math.max(0, score - 8);
    }

    public void error(String message) {
        issues.add(message);
        severity = Severity.ERROR;
        score = Math.max(0, score - 20);
    }
}

package com.elektoryk.scan.model;

import java.util.ArrayList;
import java.util.List;

public class ProjectWorkflowSnapshot {
    public int overallProgress;
    public ProjectWorkflowStage nextStage = ProjectWorkflowStage.SETUP;
    public final List<WorkflowStageState> stages = new ArrayList<>();
    public final List<String> globalWarnings = new ArrayList<>();

    public WorkflowStageState state(ProjectWorkflowStage stage) {
        for (WorkflowStageState s : stages) if (s.stage == stage) return s;
        return null;
    }
}

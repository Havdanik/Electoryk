package com.elektoryk.scan.model;

import java.util.ArrayList;
import java.util.List;

public class WorkflowStageState {
    public enum Status { COMPLETE, READY, IN_PROGRESS, BLOCKED }

    public ProjectWorkflowStage stage;
    public Status status = Status.BLOCKED;
    public int progress;
    public String primaryAction = "";
    public final List<String> blockers = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();

    public WorkflowStageState(ProjectWorkflowStage stage) {
        this.stage = stage;
    }

    public boolean complete() { return status == Status.COMPLETE; }
}

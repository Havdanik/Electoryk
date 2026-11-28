package com.elektoryk.scan.scanner.pipeline;

import com.elektoryk.scan.model.SceneModel;

public interface SceneCleanupEngine {
    class Result {
        public SceneModel scene;
        public int autoAccepted;
        public int needsReview;
        public int rejectedArtifacts;
    }

    Result clean(SceneModel rawScene);
}

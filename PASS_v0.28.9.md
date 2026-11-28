# Deep pass v0.28.9

Focus: semantic object geometry consistency and regression hardening.

The v0.28.8 pose hardening correctly rebuilt centroid and PCA from samples surviving robust XYZ bounds. Audit found orientedWidth()/orientedDepth() still projected the unfiltered reservoir, so a rejected wall/background leak could still inflate dimensions. The projection path now consumes the same robust survivor population as pose estimation. This closes the remaining split-evidence path without changing scene persistence.

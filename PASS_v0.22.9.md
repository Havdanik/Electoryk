# ElektorykScan v0.22.9 — hot-path allocation hardening

- Removed the redundant full-frame `int[]` clone before semantic inference. The visual recorder owns a fresh immutable RGB array per acquired camera image; semantic inference and JPEG compression only read it, so sharing is safe and cuts one large allocation per semantic sample.
- Removed per-Depth-point `new float[]{x,y,z}` allocations in both coarse keyframe depth projection and semantic Depth→RGB fusion. Reusable 3-float source vectors are now used for the full point loops. This eliminates thousands of tiny allocations per sampled frame on dense scans.
- No project/schema/scene format changes. All v0.22.1–v0.22.8 durability, epoch, backpressure and shutdown fixes remain intact.

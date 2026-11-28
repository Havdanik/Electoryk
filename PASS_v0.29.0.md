# Deep engineering pass v0.29.0

Audited the current v0.28.9 tree across scanner/ARCore, reconstruction, semantic fusion, editor/UI, import/export and build configuration. The concrete regression/performance issue fixed in this pass is semantic-history front eviction during long scans. The retained-history limits were already sensible, but the backing data structure made every oldest-record eviction linear. Converted it to a FIFO deque without changing public snapshot ordering or persistence formats.

Validation performed: ZIP source integrity, Java structural delimiter scan, version metadata, source count, changed-file diff review. Full Android Gradle compilation remains environment-dependent where the required Gradle/Android toolchain is unavailable.

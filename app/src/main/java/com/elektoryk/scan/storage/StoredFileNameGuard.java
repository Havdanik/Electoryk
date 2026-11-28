package com.elektoryk.scan.storage;

/** Restricts persisted attachment names to a single safe path segment. */
public final class StoredFileNameGuard {
    private StoredFileNameGuard() {}

    public static String sanitizeLeaf(String name) {
        if (name == null) return "";
        String x = name.replace('\\', '/');
        int slash = x.lastIndexOf('/');
        if (slash >= 0) x = x.substring(slash + 1);
        x = x.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (x.length() > 180) x = x.substring(0, 180);
        if (x.isEmpty() || ".".equals(x) || "..".equals(x) || x.startsWith(".")) return "";
        return x;
    }

    public static boolean isSafeLeaf(String name) {
        if (name == null || name.isEmpty()) return false;
        String safe = sanitizeLeaf(name);
        return !safe.isEmpty() && safe.equals(name);
    }

    public static String requireSafeLeaf(String name) {
        if (!isSafeLeaf(name)) throw new IllegalArgumentException("Unsafe stored file name");
        return name;
    }
}

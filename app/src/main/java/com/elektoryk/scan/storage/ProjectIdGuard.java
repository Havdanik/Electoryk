package com.elektoryk.scan.storage;

/** Keeps project identifiers safe for use as file/directory names. */
public final class ProjectIdGuard {
    private ProjectIdGuard() {}

    public static boolean isSafe(String id) {
        if (id == null || id.length() < 1 || id.length() > 96) return false;
        for (int i=0;i<id.length();i++) {
            char c=id.charAt(i);
            boolean ok=(c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='_'||c=='-';
            if(!ok) return false;
        }
        return true;
    }

    public static String requireSafe(String id) {
        if(!isSafe(id)) throw new IllegalArgumentException("Unsafe project id");
        return id;
    }
}

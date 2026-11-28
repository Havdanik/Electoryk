package com.elektoryk.scan.model;

public class Vec3 {
    public double x;
    public double y;
    public double z;

    public Vec3() {}

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3 add(Vec3 b) { return new Vec3(x + b.x, y + b.y, z + b.z); }
    public Vec3 sub(Vec3 b) { return new Vec3(x - b.x, y - b.y, z - b.z); }
    public Vec3 mul(double s) { return new Vec3(x * s, y * s, z * s); }

    public double dot(Vec3 b) { return x*b.x + y*b.y + z*b.z; }

    public double length() { return Math.sqrt(dot(this)); }

    public Vec3 normalized() {
        double len = length();
        return len < 1e-9 ? new Vec3(0,0,0) : mul(1.0 / len);
    }
}

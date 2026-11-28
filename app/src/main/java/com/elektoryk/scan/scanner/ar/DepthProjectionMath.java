package com.elektoryk.scan.scanner.ar;

/** Pure math used to unproject ARCore depth pixels. Kept Android-free for regression tests. */
public final class DepthProjectionMath {
    private DepthProjectionMath() {}

    public static final class Intrinsics {
        public final float fx, fy, cx, cy;
        Intrinsics(float fx, float fy, float cx, float cy) {
            this.fx = fx; this.fy = fy; this.cx = cx; this.cy = cy;
        }
    }

    /**
     * ARCore Raw Depth uses the GPU/texture camera intrinsics. The texture intrinsics
     * must be scaled to the actual depth-image dimensions before unprojection.
     */
    public static Intrinsics scaleTextureIntrinsics(float fx, float fy, float cx, float cy,
                                                    int textureWidth, int textureHeight,
                                                    int depthWidth, int depthHeight) {
        if (textureWidth <= 0 || textureHeight <= 0 || depthWidth <= 0 || depthHeight <= 0)
            throw new IllegalArgumentException("Invalid intrinsics/image dimensions");
        float sx = depthWidth / (float) textureWidth;
        float sy = depthHeight / (float) textureHeight;
        return new Intrinsics(fx * sx, fy * sy, cx * sx, cy * sy);
    }

    /** Returns camera-space XYZ, where camera looks down -Z. Convenience allocating API. */
    public static float[] unproject(float pixelX, float pixelY, float depthMeters, Intrinsics i) {
        float[] out=new float[3];
        return unprojectInto(pixelX,pixelY,depthMeters,i,out,0)?out:null;
    }

    /** Allocation-free hot-path variant used by the live depth collector. */
    public static boolean unprojectInto(float pixelX,float pixelY,float depthMeters,Intrinsics i,float[] out,int offset){
        if(i==null||i.fx==0f||i.fy==0f||out==null||offset<0||offset+2>=out.length
                ||!Float.isFinite(depthMeters)||depthMeters<=0f)return false;
        out[offset]=depthMeters*(pixelX-i.cx)/i.fx;
        out[offset+1]=depthMeters*(i.cy-pixelY)/i.fy;
        out[offset+2]=-depthMeters;
        return Float.isFinite(out[offset])&&Float.isFinite(out[offset+1])&&Float.isFinite(out[offset+2]);
    }
    /** Scalar-intrinsics overload for the live collector; avoids constructing Intrinsics per frame. */
    public static boolean unprojectInto(float pixelX,float pixelY,float depthMeters,
                                        float fx,float fy,float cx,float cy,float[] out,int offset){
        if(fx==0f||fy==0f||out==null||offset<0||offset+2>=out.length
                ||!Float.isFinite(depthMeters)||depthMeters<=0f)return false;
        out[offset]=depthMeters*(pixelX-cx)/fx;
        out[offset+1]=depthMeters*(cy-pixelY)/fy;
        out[offset+2]=-depthMeters;
        return Float.isFinite(out[offset])&&Float.isFinite(out[offset+1])&&Float.isFinite(out[offset+2]);
    }

}

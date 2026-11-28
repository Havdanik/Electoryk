package com.elektoryk.scan.semantic;

/** Downsampled semantic mask plus the label currently under the center of the camera. */
public final class SemanticFrame {
    public final long timestampNs;public final int width,height;public final byte[] labels;
    public final SemanticLabel centerLabel;public final float centerShare;public final int sourceWidth,sourceHeight;
    public final SemanticModelManager.Flavor flavor;

    public SemanticFrame(long ts,int w,int h,byte[] labels,SemanticLabel centerLabel,float centerShare,int sourceWidth,int sourceHeight,SemanticModelManager.Flavor flavor){
        this.timestampNs=ts;this.width=w;this.height=h;this.labels=labels;this.centerLabel=centerLabel;this.centerShare=centerShare;this.sourceWidth=sourceWidth;this.sourceHeight=sourceHeight;this.flavor=flavor;
    }
    public int rawLabelAtSource(float x,float y){
        if(labels==null||labels.length==0||sourceWidth<=0||sourceHeight<=0)return -1;
        int ix=Math.max(0,Math.min(width-1,(int)(x/sourceWidth*width)));int iy=Math.max(0,Math.min(height-1,(int)(y/sourceHeight*height)));return labels[iy*width+ix]&0xff;
    }
    public SemanticLabel labelAtSource(float x,float y){return SemanticLabel.fromModelIndex(rawLabelAtSource(x,y),flavor);}
    public int maskX(float sourceX){return Math.max(0,Math.min(width-1,(int)(sourceX/sourceWidth*width)));}
    public int maskY(float sourceY){return Math.max(0,Math.min(height-1,(int)(sourceY/sourceHeight*height)));}
    public SemanticLabel labelAtMask(int x,int y){if(x<0||y<0||x>=width||y>=height)return SemanticLabel.UNKNOWN;return SemanticLabel.fromModelIndex(labels[y*width+x]&0xff,flavor);}
}

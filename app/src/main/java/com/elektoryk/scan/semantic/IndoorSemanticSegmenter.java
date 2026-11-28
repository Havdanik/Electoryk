package com.elektoryk.scan.semantic;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/** On-device dense semantic segmentation. Inference is deliberately called off the GL thread. */
public final class IndoorSemanticSegmenter implements AutoCloseable {
    private static final int MASK_W=128,MASK_H=128;
    private Interpreter interpreter;
    private File loadedFile;
    private SemanticModelManager.Flavor loadedFlavor=SemanticModelManager.Flavor.NONE;
    // Reused direct tensor buffers: allocating multi-megabyte native buffers every ~260 ms
    // creates avoidable native-memory/GC pressure during long room scans. segment() is synchronized,
    // so one reusable input/output pair is safe and is resized only when the model shape changes.
    private ByteBuffer inputBuffer,outputBuffer;
    private int inputCapacity,outputCapacity;

    public synchronized boolean ensureLoaded(File model,SemanticModelManager.Flavor flavor){
        if(model==null||!model.isFile()||flavor==SemanticModelManager.Flavor.NONE)return false;
        if(interpreter!=null&&model.equals(loadedFile)&&flavor==loadedFlavor)return true;
        close();
        try(FileInputStream in=new FileInputStream(model);FileChannel ch=in.getChannel()){
            java.nio.MappedByteBuffer map=ch.map(FileChannel.MapMode.READ_ONLY,0,ch.size());
            Interpreter.Options opt=new Interpreter.Options();opt.setNumThreads(4);
            interpreter=new Interpreter(map,opt);
            if(!compatibleModel(interpreter,flavor)){close();return false;}
            loadedFile=model;loadedFlavor=flavor;return true;
        }catch(Throwable t){close();return false;}
    }

    private static boolean compatibleModel(Interpreter model,SemanticModelManager.Flavor flavor){
        Tensor input=model.getInputTensor(0),output=model.getOutputTensor(0);
        int[] in=input.shape(),out=output.shape();
        if(in.length!=4||in[0]!=1||in[3]!=3||in[1]<=0||in[2]<=0||in[1]>2048||in[2]>2048
                ||input.dataType()!=DataType.FLOAT32)return false;
        if(out.length!=3&&out.length!=4)return false;
        if(out[0]!=1||out[1]<=0||out[2]<=0)return false;
        if(out.length==4&&(out[3]<=0||out[3]>1&&out[3]!=(flavor==SemanticModelManager.Flavor.ML_PERF_32?32:151)))return false;
        int bytes=bytesPer(output.dataType());if(bytes<=0)return false;
        long elements=1;for(int n:out){if(n<=0)return false;elements*=n;if(elements>256_000_000L/bytes)return false;}
        return true;
    }

    public synchronized SemanticFrame segment(int[] argb,int sw,int sh,long timestampNs){
        if(interpreter==null||argb==null||sw<=0||sh<=0||(long)sw*sh>argb.length)return null;
        Tensor inT=interpreter.getInputTensor(0);int[] is=inT.shape();
        if(is.length!=4||is[0]!=1||is[3]!=3)return null;int ih=is[1],iw=is[2];
        if(iw<=0||ih<=0||iw>2048||ih>2048)return null;
        if(inT.dataType()!=DataType.FLOAT32)return null; // both bundled download choices expose float RGB input
        int inputBytes=iw*ih*3*4;
        ByteBuffer input=buffer(true,inputBytes);
        // TensorFlow DeepLab MobileNetV2 ADE20K exports use MobileNet-style [-1,1] RGB preprocessing.
        // Camera keyframes are generally landscape while both models expect a square image.
        // Preserve the aspect ratio: a stretched sofa/window can change class entirely, and
        // stretching also invalidates the mask-to-Depth projection in SemanticSceneTracker.
        final float scale=Math.min(iw/(float)sw,ih/(float)sh);
        final float left=(iw-sw*scale)*0.5f,top=(ih-sh*scale)*0.5f;
        final float right=left+sw*scale,bottom=top+sh*scale;
        for(int y=0;y<ih;y++){
            float py=y+0.5f;
            for(int x=0;x<iw;x++){
                float px=x+0.5f;
                if(px<left||px>=right||py<top||py>=bottom){
                    input.putFloat(-1f);input.putFloat(-1f);input.putFloat(-1f);continue;
                }
                float sx=(px-left)/scale-0.5f,sy=(py-top)/scale-0.5f;
                int x0=(int)Math.floor(sx),y0=(int)Math.floor(sy);
                float ax=sx-x0,ay=sy-y0;
                int ix0=Math.max(0,Math.min(sw-1,x0)),ix1=Math.max(0,Math.min(sw-1,x0+1));
                int iy0=Math.max(0,Math.min(sh-1,y0)),iy1=Math.max(0,Math.min(sh-1,y0+1));
                int c00=argb[iy0*sw+ix0],c10=argb[iy0*sw+ix1];
                int c01=argb[iy1*sw+ix0],c11=argb[iy1*sw+ix1];
                float w00=(1f-ax)*(1f-ay),w10=ax*(1f-ay),w01=(1f-ax)*ay,w11=ax*ay;
                input.putFloat((w00*((c00>>16)&255)+w10*((c10>>16)&255)+w01*((c01>>16)&255)+w11*((c11>>16)&255)-127.5f)/127.5f);
                input.putFloat((w00*((c00>>8)&255)+w10*((c10>>8)&255)+w01*((c01>>8)&255)+w11*((c11>>8)&255)-127.5f)/127.5f);
                input.putFloat((w00*(c00&255)+w10*(c10&255)+w01*(c01&255)+w11*(c11&255)-127.5f)/127.5f);
            }
        }
        input.rewind();
        Tensor outT=interpreter.getOutputTensor(0);int[] os=outT.shape();
        int elems=1;for(int v:os)elems*=Math.max(1,v);int bytes=bytesPer(outT.dataType());if(bytes<=0)return null;
        ByteBuffer output=buffer(false,elems*bytes);
        interpreter.run(input,output);output.rewind();

        int oh,ow,classes=1;
        boolean logits=false;
        if(os.length==4){oh=os[1];ow=os[2];classes=Math.max(1,os[3]);logits=classes>1;}
        else if(os.length==3){oh=os[1];ow=os[2];}
        else return null;
        if(ow<=0||oh<=0)return null;

        int[] dense=new int[ow*oh];DataType dt=outT.dataType();
        if(logits){
            // Generic export support: [1,H,W,C] logits/probabilities -> argmax class.
            for(int p=0;p<dense.length;p++){
                int best=0;float bv=-Float.MAX_VALUE;
                for(int c=0;c<classes;c++){
                    float v=readAsFloat(output,dt);if(v>bv){bv=v;best=c;}
                }
                dense[p]=best;
            }
        }else{
            int count=Math.min(dense.length,elems);
            for(int i=0;i<count;i++)dense[i]=(int)readAsLong(output,dt);
        }

        byte[] mask=new byte[MASK_W*MASK_H];int maxLabels=loadedFlavor==SemanticModelManager.Flavor.ML_PERF_32?32:151;
        int[] centerCounts=new int[maxLabels];int centerTotal=0;
        for(int y=0;y<MASK_H;y++)for(int x=0;x<MASK_W;x++){
            // Undo the input padding before exposing the fixed-size mask. The tracker projects
            // camera/Depth points into this source-frame mask, so letterbox pixels must not shift it.
            float sourceX=(x+0.5f)*sw/MASK_W,sourceY=(y+0.5f)*sh/MASK_H;
            int ox=Math.max(0,Math.min(ow-1,(int)((left+sourceX*scale)*ow/iw)));
            int oy=Math.max(0,Math.min(oh-1,(int)((top+sourceY*scale)*oh/ih)));
            int lab=dense[oy*ow+ox];if(lab<0||lab>=maxLabels)lab=loadedFlavor==SemanticModelManager.Flavor.ML_PERF_32?31:0;
            mask[y*MASK_W+x]=(byte)lab;
            if(x>=MASK_W*0.30&&x<MASK_W*0.70&&y>=MASK_H*0.30&&y<MASK_H*0.70){centerCounts[lab]++;centerTotal++;}
        }
        int best=0;for(int i=1;i<centerCounts.length;i++)if(centerCounts[i]>centerCounts[best])best=i;
        float share=centerTotal<=0?0:centerCounts[best]/(float)centerTotal;
        return new SemanticFrame(timestampNs,MASK_W,MASK_H,mask,SemanticLabel.fromModelIndex(best,loadedFlavor),share,sw,sh,loadedFlavor);
    }

    private static long readAsLong(ByteBuffer b,DataType dt){
        if(dt==DataType.INT64)return b.getLong();if(dt==DataType.INT32)return b.getInt();if(dt==DataType.UINT8)return b.get()&255;if(dt==DataType.INT8)return b.get();if(dt==DataType.FLOAT32)return Math.round(b.getFloat());return 0;
    }
    private static float readAsFloat(ByteBuffer b,DataType dt){
        if(dt==DataType.FLOAT32)return b.getFloat();if(dt==DataType.INT64)return b.getLong();if(dt==DataType.INT32)return b.getInt();if(dt==DataType.UINT8)return b.get()&255;if(dt==DataType.INT8)return b.get();return -Float.MAX_VALUE;
    }
    private static int bytesPer(DataType t){if(t==DataType.INT64)return 8;if(t==DataType.INT32||t==DataType.FLOAT32)return 4;if(t==DataType.UINT8||t==DataType.INT8)return 1;return -1;}
    private ByteBuffer buffer(boolean input,int bytes){
        ByteBuffer b=input?inputBuffer:outputBuffer;int cap=input?inputCapacity:outputCapacity;
        if(b==null||cap<bytes){
            b=ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
            if(input){inputBuffer=b;inputCapacity=bytes;}else{outputBuffer=b;outputCapacity=bytes;}
        }
        b.clear();b.limit(bytes);return b;
    }
    @Override public synchronized void close(){if(interpreter!=null)try{interpreter.close();}catch(Throwable ignored){}interpreter=null;loadedFile=null;loadedFlavor=SemanticModelManager.Flavor.NONE;inputBuffer=null;outputBuffer=null;inputCapacity=outputCapacity=0;}
}

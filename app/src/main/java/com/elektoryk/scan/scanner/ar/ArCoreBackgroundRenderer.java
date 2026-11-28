package com.elektoryk.scan.scanner.ar;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class ArCoreBackgroundRenderer {
    private static final float[] QUAD = {
            -1f,-1f,
             1f,-1f,
            -1f, 1f,
             1f, 1f
    };

    private final FloatBuffer vertices;
    private final FloatBuffer texCoords;
    private final float[] transformed = new float[8];

    private int textureId = -1;
    private int program;
    private int aPosition;
    private int aTexCoord;
    private int uTexture;

    public ArCoreBackgroundRenderer() {
        vertices = buffer(QUAD);
        texCoords = buffer(new float[8]);
    }

    public void createOnGlThread() {
        int[] t = new int[1];
        GLES20.glGenTextures(1,t,0);
        textureId=t[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        String vs =
                "attribute vec2 a_Position;\n" +
                "attribute vec2 a_TexCoord;\n" +
                "varying vec2 v_TexCoord;\n" +
                "void main(){ gl_Position=vec4(a_Position,0.0,1.0); v_TexCoord=a_TexCoord; }";
        String fs =
                "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "uniform samplerExternalOES u_Texture;\n" +
                "varying vec2 v_TexCoord;\n" +
                "void main(){ gl_FragColor=texture2D(u_Texture,v_TexCoord); }";

        program = link(vs,fs);
        aPosition=GLES20.glGetAttribLocation(program,"a_Position");
        aTexCoord=GLES20.glGetAttribLocation(program,"a_TexCoord");
        uTexture=GLES20.glGetUniformLocation(program,"u_Texture");
    }

    public int getTextureId() { return textureId; }

    public void draw(Frame frame) {
        frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                QUAD,
                Coordinates2d.TEXTURE_NORMALIZED,
                transformed);

        texCoords.position(0);
        texCoords.put(transformed);
        texCoords.position(0);
        vertices.position(0);

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);
        GLES20.glUniform1i(uTexture,0);

        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition,2,GLES20.GL_FLOAT,false,0,vertices);
        GLES20.glEnableVertexAttribArray(aTexCoord);
        GLES20.glVertexAttribPointer(aTexCoord,2,GLES20.GL_FLOAT,false,0,texCoords);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glDisableVertexAttribArray(aTexCoord);
    }

    private FloatBuffer buffer(float[] f) {
        FloatBuffer b=ByteBuffer.allocateDirect(f.length*4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.put(f).position(0);
        return b;
    }

    private int shader(int type,String src) {
        int s=GLES20.glCreateShader(type);
        GLES20.glShaderSource(s,src);
        GLES20.glCompileShader(s);
        int[] ok=new int[1];
        GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0) throw new RuntimeException(GLES20.glGetShaderInfoLog(s));
        return s;
    }

    private int link(String vs,String fs) {
        int p=GLES20.glCreateProgram();
        int v=shader(GLES20.GL_VERTEX_SHADER,vs);
        int f=shader(GLES20.GL_FRAGMENT_SHADER,fs);
        GLES20.glAttachShader(p,v); GLES20.glAttachShader(p,f);
        GLES20.glLinkProgram(p);
        int[] ok=new int[1];
        GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);
        if(ok[0]==0) throw new RuntimeException(GLES20.glGetProgramInfoLog(p));
        GLES20.glDeleteShader(v); GLES20.glDeleteShader(f);
        return p;
    }
}

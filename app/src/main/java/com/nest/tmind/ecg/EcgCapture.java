package com.nest.tmind.ecg;

import java.util.ArrayList;

public class EcgCapture {
    private static final EcgCapture I = new EcgCapture();
    public static EcgCapture i(){ return I; }

    private final ArrayList<Float> buf = new ArrayList<>();
    private int fs = 250;

    public synchronized void setFs(int fs){ this.fs = fs; }
    public synchronized int fs(){ return fs; }

    public synchronized void add(float[] mv){
        for (float v: mv) buf.add(v);
    }
    public synchronized float[] consumeAll(){
        float[] out = new float[buf.size()];
        for (int i=0;i<out.length;i++) out[i] = buf.get(i);
        buf.clear();
        return out;
    }
}

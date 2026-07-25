package com.nest.tmind.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import org.json.JSONArray;
import org.json.JSONObject;

/** 오프라인 큐: 네트워크 복구 시 자동 전송 (로컬 저장) */
public class DataQueueManager {

    private static final String PREF = "tmind_data_queue";
    private static final String KEY_QUEUE = "queue";

    private final Context ctx;
    private final SharedPreferences sp;

    public DataQueueManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        sp = this.ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public void enqueue(String type, JSONObject payload) {
        try {
            JSONArray queue = loadQueue();
            JSONObject item = new JSONObject();
            item.put("type", type);
            item.put("payload", payload);
            item.put("ts", System.currentTimeMillis());
            queue.put(item);
            sp.edit().putString(KEY_QUEUE, queue.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public void flushIfOnline() {
        if (!isOnline()) return;
        JSONArray queue = loadQueue();
        if (queue.length() == 0) return;
        // 서버 연동 전: 전송 성공으로 간주하고 큐 비움
        sp.edit().putString(KEY_QUEUE, new JSONArray().toString()).apply();
    }

    private JSONArray loadQueue() {
        String js = sp.getString(KEY_QUEUE, "[]");
        try {
            return new JSONArray(js);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.Network net = cm.getActiveNetwork();
        if (net == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(net);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}

package com.nest.tmind.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/** 오프라인 큐: AES-256 암호화 저장 + 전송 시 암호화 봉투 */
public class DataQueueManager {

    private static final String TAG = "DataQueueManager";
    private static final String PREF = "tmind_data_queue";
    private static final String KEY_QUEUE = "queue_enc";
    private static final String KEY_QUEUE_LEGACY = "queue";

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
            // 페이로드 자체도 암호화하여 큐에 보관
            item.put("payload_enc", AesCrypto.encryptToBase64(payload.toString()));
            item.put("encrypted", true);
            item.put("algo", "AES-256-GCM");
            item.put("ts", System.currentTimeMillis());
            queue.put(item);
            saveQueue(queue);
        } catch (Exception e) {
            Log.w(TAG, "enqueue encrypt failed, fallback plain", e);
            try {
                JSONArray queue = loadQueue();
                JSONObject item = new JSONObject();
                item.put("type", type);
                item.put("payload", payload);
                item.put("encrypted", false);
                item.put("ts", System.currentTimeMillis());
                queue.put(item);
                saveQueue(queue);
            } catch (Exception ignored) {
            }
        }
    }

    public void flushIfOnline() {
        if (!isOnline()) return;
        JSONArray queue = loadQueue();
        if (queue.length() == 0) return;

        // 전송 봉투: 전체 큐를 AES-256으로 암호화한 뒤 "전송" (서버 연동 전 성공 처리)
        try {
            JSONObject envelope = new JSONObject();
            envelope.put("algo", "AES-256-GCM");
            envelope.put("ts", System.currentTimeMillis());
            envelope.put("count", queue.length());
            envelope.put("cipher", AesCrypto.encryptToBase64(queue.toString()));
            // TODO: HTTPS POST envelope to server
            Log.i(TAG, "flush encrypted envelope bytes≈" + envelope.toString().length());
        } catch (Exception e) {
            Log.w(TAG, "flush encrypt failed", e);
        }
        sp.edit()
                .putString(KEY_QUEUE, AesCrypto.encryptSafe(ctx, "[]"))
                .remove(KEY_QUEUE_LEGACY)
                .apply();
    }

    private void saveQueue(JSONArray queue) {
        String plain = queue.toString();
        sp.edit()
                .putString(KEY_QUEUE, AesCrypto.encryptSafe(ctx, plain))
                .remove(KEY_QUEUE_LEGACY)
                .apply();
    }

    private JSONArray loadQueue() {
        String enc = sp.getString(KEY_QUEUE, null);
        if (enc != null) {
            try {
                String plain = AesCrypto.decryptOrPlain(enc);
                return new JSONArray(plain);
            } catch (Exception e) {
                return new JSONArray();
            }
        }
        // 레거시 평문 마이그레이션
        String legacy = sp.getString(KEY_QUEUE_LEGACY, null);
        if (legacy != null) {
            try {
                JSONArray arr = new JSONArray(legacy);
                saveQueue(arr);
                return arr;
            } catch (Exception e) {
                return new JSONArray();
            }
        }
        return new JSONArray();
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

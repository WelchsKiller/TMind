package com.nest.tmind.ecg.model;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class BleManager {

    /* NUS UUID */
    public static final UUID UUID_SERVICE     = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static final UUID UUID_CHAR_WRITE  = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    public static final UUID UUID_CHAR_NOTIFY = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    public static final UUID UUID_CCCD        = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /* Singleton */
    private static BleManager I;
    public static synchronized BleManager getInstance(Context c){
        if (I == null) I = new BleManager(c.getApplicationContext());
        return I;
    }
    public String debugId(){ return Integer.toHexString(System.identityHashCode(this)); }

    /* Fields */
    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic chWrite, chNotify;

    private final MutableLiveData<EkgParser.EkgFrame> frames = new MutableLiveData<>();
    public LiveData<EkgParser.EkgFrame> liveFrames(){ return frames; }

    private volatile String pendingCmd;
    private volatile boolean waitingBond;

    /* UI 우회 경로 */
    public interface FrameListener { void onFrame(EkgParser.EkgFrame f); }
    private final CopyOnWriteArrayList<FrameListener> listeners = new CopyOnWriteArrayList<>();
    public void addFrameListener(FrameListener l) { if (l!=null){ listeners.add(l); Log.d("EKG_MGR","add size="+listeners.size()+" mgr="+debugId()); } }
    public void removeFrameListener(FrameListener l) { if (l!=null){ listeners.remove(l); Log.d("EKG_MGR","rm size="+listeners.size()+" mgr="+debugId()); } }

    private BleManager(Context app) {
        this.app = app.getApplicationContext();
        // Bond 후 보안 필요 시 보류 명령 재전송
        IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        this.app.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int st = i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                if (gatt != null && waitingBond && d != null
                        && d.getAddress().equals(gatt.getDevice().getAddress())
                        && st == BluetoothDevice.BOND_BONDED) {
                    waitingBond = false;
                    if (pendingCmd != null) sendAscii(pendingCmd);
                }
            }
        }, f);
    }

    public void attachGatt(BluetoothGatt g) { this.gatt = g; }

    @SuppressLint("MissingPermission")
    public void bindCharacteristics() {
        if (gatt == null) return;
        BluetoothGattService s = gatt.getService(UUID_SERVICE);
        if (s == null) return;
        chWrite  = s.getCharacteristic(UUID_CHAR_WRITE);
        chNotify = s.getCharacteristic(UUID_CHAR_NOTIFY);
        if (chNotify != null) try { gatt.setCharacteristicNotification(chNotify, true); } catch (Exception ignore) {}
    }

    /** GATT notify → 파서 → UI/LiveData */
    public void onNotify(byte[] data) {
        Log.d("EKG_SINK", "notify bytes=" + (data!=null?data.length:0));

        EkgParser.feedAscii(data, frame -> main.post(() -> {
            frames.setValue(frame); // 메인에서 setValue → coalesce 방지
            for (FrameListener l : listeners) {
                try { l.onFrame(frame); } catch (Throwable ignore) {}
            }
        }));
    }

    /* ===== 쓰기 ===== */
    private int preferredWriteType() {
        if (chWrite == null) return BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        int props = chWrite.getProperties();
        if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 &&
                (props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) {
            return BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; // With Response만 가능
        }
        return BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; // 둘 다 가능 → NO_RESPONSE 기본
    }

    public void sendAsciiAck(String cmd) {
        if (gatt == null || chWrite == null) { Log.d("BLE_DBG","no gatt/write"); return; }
        pendingCmd = cmd;
        byte[] b = cmd.getBytes(StandardCharsets.US_ASCII);
        chWrite.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        chWrite.setValue(b);
        @SuppressLint("MissingPermission") boolean ok = gatt.writeCharacteristic(chWrite);
        Log.d("BLE_DBG", "write(ACK) '" + cmd.replace("\r\n","\\r\\n") + "' ok=" + ok);
    }

    public void sendAscii(String cmd) {
        if (gatt == null || chWrite == null) { Log.d("BLE_DBG","no gatt/write"); return; }
        pendingCmd = cmd;
        byte[] b = cmd.getBytes(StandardCharsets.US_ASCII);
        int wt = preferredWriteType();
        chWrite.setWriteType(wt);
        chWrite.setValue(b);
        @SuppressLint("MissingPermission") boolean ok = gatt.writeCharacteristic(chWrite);
        Log.d("BLE_DBG", "write '" + cmd.replace("\r\n","\\r\\n") + "' wt=" + wt + " ok=" + ok);
    }

    @SuppressLint("MissingPermission")
    public void onWriteResult(int status) {
        Log.d("EKG_CMD", "onCharacteristicWrite status=" + status);
        if (status == 5 || status == 13) {
            if (gatt != null) {
                waitingBond = true;
                try { gatt.getDevice().createBond(); } catch (Exception ignore) {}
            }
        }
    }

    // BleManager 클래스 안에 추가
    @SuppressLint("MissingPermission")
    public boolean rearmNotifications() {
        if (gatt == null) return false;
        BluetoothGattService svc = gatt.getService(UUID_SERVICE);
        if (svc == null) return false;
        BluetoothGattCharacteristic chN = svc.getCharacteristic(UUID_CHAR_NOTIFY);
        if (chN == null) return false;

        try {
            gatt.setCharacteristicNotification(chN, false);
            gatt.setCharacteristicNotification(chN, true);
            BluetoothGattDescriptor cccd = chN.getDescriptor(UUID_CCCD);
            if (cccd != null) {
                int props = chN.getProperties();
                cccd.setValue((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                        ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                return gatt.writeDescriptor(cccd); // 실제로 쓰기 재시도
            }
            return true; // CCCD가 없으면 토글만으로 OK
        } catch (Exception e) {
            android.util.Log.d("EKG_DBG", "rearm ex: " + e);
            return false;
        }
    }

}

package com.nest.tmind.util;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/** 시니어 모드 TTS 읽어주기 (앱 전역 싱글톤 — Activity destroy와 init 콜백 충돌 방지) */
public class TtsHelper implements TextToSpeech.OnInitListener {

    private static TtsHelper instance;

    private TextToSpeech tts;
    private boolean ready;
    private volatile boolean initializing;

    private TtsHelper(Context appContext) {
        initializing = true;
        tts = new TextToSpeech(appContext, this);
    }

    public static synchronized TtsHelper getInstance(Context context) {
        if (instance == null) {
            instance = new TtsHelper(context.getApplicationContext());
        }
        return instance;
    }

    @Override
    public void onInit(int status) {
        initializing = false;
        if (tts == null) return;
        if (status == TextToSpeech.SUCCESS) {
            int r = tts.setLanguage(Locale.KOREAN);
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                r = tts.setLanguage(Locale.getDefault());
            }
            ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
            if (ready) {
                tts.setSpeechRate(0.9f);
            }
        }
    }

    public boolean isReady() {
        return ready && tts != null;
    }

    public void speak(String text) {
        if (text == null || text.isEmpty() || tts == null) return;
        if (!ready) return;
        tts.stop();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tmind_tts");
    }

    public void stop() {
        if (tts != null) tts.stop();
    }
}

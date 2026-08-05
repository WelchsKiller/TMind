package com.nest.tmind.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.nest.tmind.R;
import com.nest.tmind.util.DataQueueManager;
import com.nest.tmind.util.MissionManager;
import com.nest.tmind.view.VoiceWaveformView;

import org.json.JSONObject;

import java.io.File;
import java.util.Random;

/** APP-USR-008: 음성 일기 (최대 3분, AAC 16kHz) */
public class VoiceDiaryActivity extends BaseSeniorActivity {

    private static final int MAX_SEC = 3 * 60;

    private MediaRecorder recorder;
    private File audioFile;
    private boolean recording;
    private int elapsedSec;
    private TextView tvTimer, tvStatus;
    private VoiceWaveformView waveformView;
    private ImageButton btnRecord;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private float[] waveLevels = new float[40];

    private ActivityResultLauncher<String> micPermLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_diary);

        tvTimer = findViewById(R.id.tvTimer);
        tvStatus = findViewById(R.id.tvStatus);
        waveformView = findViewById(R.id.waveformView);
        btnRecord = findViewById(R.id.btnRecord);

        findViewById(R.id.btnBack).setOnClickListener(v -> confirmExit());
        setupTtsFromViews(R.id.btnTts, R.id.tvInstruction);

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });

        micPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) toggleRecording();
                    else tts.speak("마이크 권한이 필요합니다");
                });

        btnRecord.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                micPermLauncher.launch(Manifest.permission.RECORD_AUDIO);
            } else {
                toggleRecording();
            }
        });

        updateTimer();
        tvStatus.setText(R.string.recording_idle);
    }

    private void confirmExit() {
        if (tts != null) tts.stop();
        // 녹음 중이 아니어도 일기 화면 이탈 확인
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage(R.string.confirm_exit_missions)
                .setPositiveButton(R.string.dialog_end, (d, w) -> {
                    if (recording) {
                        try {
                            if (recorder != null) {
                                recorder.stop();
                                recorder.release();
                            }
                        } catch (Exception ignored) {
                        }
                        recorder = null;
                        recording = false;
                    }
                    finish();
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void toggleRecording() {
        if (recording) stopRecording();
        else startRecording();
    }

    private void startRecording() {
        try {
            audioFile = new File(getCacheDir(), "diary_" + System.currentTimeMillis() + ".m4a");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(16000);
            recorder.setAudioEncodingBitRate(64000);
            recorder.setOutputFile(audioFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            elapsedSec = 0;
            tvStatus.setText(R.string.recording_status);
            btnRecord.setImageResource(R.drawable.ic_stop);
            handler.post(waveRunnable);
            handler.post(timerRunnable);
        } catch (Exception e) {
            tts.speak("녹음을 시작할 수 없습니다");
        }
    }

    private void stopRecording() {
        handler.removeCallbacks(waveRunnable);
        handler.removeCallbacks(timerRunnable);
        recording = false;
        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
            }
        } catch (Exception ignored) {
        }
        recorder = null;
        btnRecord.setImageResource(R.drawable.ic_mic);
        tvStatus.setText(R.string.recording_done);

        try {
            JSONObject payload = new JSONObject();
            payload.put("path", audioFile != null ? audioFile.getAbsolutePath() : "");
            payload.put("durationSec", elapsedSec);
            payload.put("sampleRate", 16000);
            new DataQueueManager(this).enqueue("voice_diary", payload);
            new DataQueueManager(this).flushIfOnline();
        } catch (Exception ignored) {
        }

        new MissionManager(this).setDiaryDone();
        MissionManager mission = new MissionManager(this);
        if (mission.isAllDone()) {
            startActivity(new Intent(this, AnalysisResultActivity.class));
        } else {
            Intent i = new Intent(this, DashboardActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        }
        finish();
    }

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!recording) return;
            elapsedSec++;
            updateTimer();
            if (elapsedSec >= MAX_SEC) stopRecording();
            else handler.postDelayed(this, 1000);
        }
    };

    private final Runnable waveRunnable = new Runnable() {
        @Override
        public void run() {
            if (!recording) return;
            for (int i = 0; i < waveLevels.length - 1; i++) {
                waveLevels[i] = waveLevels[i + 1];
            }
            waveLevels[waveLevels.length - 1] = 0.2f + random.nextFloat() * 0.8f;
            waveformView.setLevels(waveLevels);
            handler.postDelayed(this, 80);
        }
    };

    private void updateTimer() {
        int min = elapsedSec / 60;
        int sec = elapsedSec % 60;
        int maxMin = MAX_SEC / 60;
        tvTimer.setText(String.format("%d:%02d / %d:00", min, sec, maxMin));
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(waveRunnable);
        handler.removeCallbacks(timerRunnable);
        if (recording) {
            try {
                if (recorder != null) {
                    recorder.stop();
                    recorder.release();
                }
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }
}

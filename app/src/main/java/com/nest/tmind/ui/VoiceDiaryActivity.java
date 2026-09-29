package com.nest.tmind.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.nest.tmind.R;
import com.nest.tmind.api.MemberApiManager;
import com.nest.tmind.util.DataQueueManager;
import com.nest.tmind.util.MissionManager;
import com.nest.tmind.util.SessionManager;
import com.nest.tmind.view.VoiceWaveformView;

import org.json.JSONObject;

import java.io.File;
import java.util.Random;

/** APP-USR-008: 음성 일기 (최대 3분, AAC 16kHz) */
public class VoiceDiaryActivity extends BaseSeniorActivity {

    public static final String EXTRA_EDIT_MODE = "edit_mode";

    private static final int MAX_SEC = 3 * 60;

    private MediaRecorder recorder;
    private File audioFile;
    private boolean recording;
    private boolean editMode;
    private boolean uploading;
    private int elapsedSec;
    /** 실제 녹음 시작 시각. 서버 수신 시각과 구분해 보내야 한다. */
    private long recordedAtMs;
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
        editMode = getIntent().getBooleanExtra(EXTRA_EDIT_MODE, false);

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
        tvStatus.setText(editMode ? R.string.diary_edit_hint : R.string.recording_idle);
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
        if (uploading) return;
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
            recordedAtMs = System.currentTimeMillis();
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
            payload.put("editMode", editMode);
            new DataQueueManager(this).enqueue(editMode ? "voice_diary_edit" : "voice_diary", payload);
            new DataQueueManager(this).flushIfOnline();
        } catch (Exception ignored) {
        }

        MissionManager mission = new MissionManager(this);
        boolean additional = getIntent().getBooleanExtra(AnalysisResultActivity.EXTRA_ADDITIONAL, false)
                || mission.isAdditionalMeasureMode();
        if (additional) {
            mission.setAdditionalMeasureMode(true);
        }
        if (editMode) {
            AfterMissionSaved.goDashboard(this);
            return;
        }

        uploading = true;
        btnRecord.setEnabled(false);
        tvStatus.setText("음성 일기를 서버에 저장하는 중입니다.");
        SessionManager sm = new SessionManager(this);
        long sessionId = MemberApiManager.getCurrentSessionId(this, additional);
        if (sessionId > 0 && sm.isPredictionSaved(sessionId)) {
            uploading = false;
            btnRecord.setEnabled(true);
            Toast.makeText(this, R.string.mission_locked_after_result, Toast.LENGTH_LONG).show();
            return;
        }
        boolean hrvInvalid = !additional && (sm.isRemoteHrvInvalid()
                || MemberApiManager.isTodayHrvInvalid(MemberApiManager.lastToday()));
        boolean priorDone = additional
                ? (mission.isHrvDone(MissionManager.Session.EVENT)
                && mission.isEmaDone(MissionManager.Session.EVENT))
                : (sm.isRemoteHrvDone()
                && (sm.isTodayEmaDone()
                || sm.isRemoteEmaDone(sessionId)
                || mission.isEmaDone()));
        if (sessionId <= 0 || hrvInvalid || !priorDone) {
            uploading = false;
            btnRecord.setEnabled(true);
            int msg = sessionId <= 0
                    ? 0
                    : (hrvInvalid ? R.string.mission_need_valid_hrv : R.string.mission_need_ema_first);
            Toast.makeText(this, sessionId <= 0
                            ? "세션이 없어 음성 일기를 저장하지 못했습니다."
                            : getString(msg),
                    Toast.LENGTH_LONG).show();
            return;
        }
        MemberApiManager.uploadVoiceDiary(this, additional, audioFile, elapsedSec, recordedAtMs,
                new MemberApiManager.ResultCallback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        runOnUiThread(() -> {
                            new MissionManager(VoiceDiaryActivity.this).setDiaryDone();
                            AfterMissionSaved.afterDiary(VoiceDiaryActivity.this, additional);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            uploading = false;
                            btnRecord.setEnabled(true);
                            Toast.makeText(VoiceDiaryActivity.this,
                                    message != null ? message : "음성 일기 저장에 실패했습니다.",
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
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

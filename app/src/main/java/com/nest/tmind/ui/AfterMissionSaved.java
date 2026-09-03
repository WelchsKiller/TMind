package com.nest.tmind.ui;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.nest.tmind.api.ApiModels;
import com.nest.tmind.api.MemberApiManager;
import com.nest.tmind.util.EmaQuestionBank;
import com.nest.tmind.util.MissionManager;

/**
 * 서버 순서: 심박변이도 → 설문 → 마음일기 → 예측 → 결과(4분면) → 피드백.
 * 일기 저장 후 /today 의 세 Done 이 모두 true 일 때만 결과 화면으로 보낸다.
 */
final class AfterMissionSaved {

    private static final String TAG = "TMindToday";

    private AfterMissionSaved() {
    }

    static void afterHrv(Activity activity, boolean additional) {
        if (activity == null || activity.isFinishing()) return;
        if (additional) {
            new MissionManager(activity).setAdditionalMeasureMode(true);
        }
        Intent ema = new Intent(activity, EmaIntroActivity.class);
        ema.putExtra(EmaSurveyActivity.EXTRA_SESSION_TYPE, emaSessionType(additional));
        ema.putExtra(AnalysisResultActivity.EXTRA_ADDITIONAL, additional);
        activity.startActivity(ema);
        activity.finish();
    }

    static void afterEma(Activity activity, boolean additional) {
        if (activity == null || activity.isFinishing()) return;
        if (additional) {
            new MissionManager(activity).setAdditionalMeasureMode(true);
        }
        Intent diary = new Intent(activity, VoiceDiaryActivity.class);
        diary.putExtra(AnalysisResultActivity.EXTRA_ADDITIONAL, additional);
        activity.startActivity(diary);
        activity.finish();
    }

    static void afterDiary(Activity activity, boolean additional) {
        if (activity == null || activity.isFinishing()) return;
        if (additional) {
            new MissionManager(activity).setAdditionalMeasureMode(true);
        }
        Log.i(TAG, "after diary, fetch /today to check Done flags additional=" + additional);
        MemberApiManager.fetchToday(activity, new MemberApiManager.ResultCallback<ApiModels.TodayResponse>() {
            @Override
            public void onSuccess(ApiModels.TodayResponse data) {
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    boolean allDone = additional
                            ? new MissionManager(activity).isSessionAllDone(MissionManager.Session.EVENT)
                            : isTodayTrioDone(data);
                    Log.i(TAG, "after diary /today hrvDone=" + (data != null ? data.hrvDone : null)
                            + " emaDone=" + (data != null ? data.emaDone : null)
                            + " diaryDone=" + (data != null ? data.diaryDone : null)
                            + " allDone=" + allDone);
                    if (allDone) {
                        openAnalysis(activity, additional);
                    } else {
                        goDashboard(activity);
                    }
                });
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "after diary /today failed: " + message);
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing()) goDashboard(activity);
                });
            }
        });
    }

    static void goDashboard(Activity activity) {
        Intent i = new Intent(activity, DashboardActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(i);
        activity.finish();
    }

    static boolean isTodayTrioDone(ApiModels.TodayResponse data) {
        return data != null
                && Boolean.TRUE.equals(data.hrvDone)
                && Boolean.TRUE.equals(data.emaDone)
                && Boolean.TRUE.equals(data.diaryDone);
    }

    private static void openAnalysis(Activity activity, boolean additional) {
        Intent i = new Intent(activity, AnalysisResultActivity.class);
        i.putExtra(AnalysisResultActivity.EXTRA_ADDITIONAL, additional);
        activity.startActivity(i);
        activity.finish();
    }

    private static String emaSessionType(boolean additional) {
        if (additional) {
            return EmaQuestionBank.SessionType.EVENT.name();
        }
        ApiModels.TodayResponse today = MemberApiManager.lastToday();
        if (today != null && today.currentType != null) {
            switch (today.currentType.trim().toUpperCase()) {
                case "PM":
                case "AFTERNOON":
                    return EmaQuestionBank.SessionType.AFTERNOON.name();
                default:
                    return EmaQuestionBank.SessionType.MORNING.name();
            }
        }
        return MissionManager.mainSessionByHour() == MissionManager.Session.AFTERNOON
                ? EmaQuestionBank.SessionType.AFTERNOON.name()
                : EmaQuestionBank.SessionType.MORNING.name();
    }
}

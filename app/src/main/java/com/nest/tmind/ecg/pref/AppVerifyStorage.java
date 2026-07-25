package com.nest.tmind.ecg.pref;

// AppVerifyStorage.java
import android.content.Context;

import com.nest.tmind.ecg.model.AppVerifyResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class AppVerifyStorage {

    private static final String FILE_NAME = "app_verify_results.json";

    // 결과 한 건 추가 (최근 것이 위로 오게)
    public static void addResult(Context context, AppVerifyResult result) {
        List<AppVerifyResult> list = loadResults(context);
        list.add(0, result);   // 최신 데이터 앞에 추가
        saveResults(context, list);
    }

    // 전체 저장
    private static void saveResults(Context context, List<AppVerifyResult> list) {
        JSONArray array = new JSONArray();
        try {
            for (AppVerifyResult r : list) {
                JSONObject obj = new JSONObject();
                obj.put("dateTime", r.dateTime);
                obj.put("successRate", r.successRate);
                obj.put("appRunSeconds", r.appRunSeconds);
                obj.put("uiResponseMs", r.uiResponseMs);
                obj.put("connectionStability", r.connectionStability);
                array.put(obj);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE)
                )
        )) {
            writer.write(array.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 전체 불러오기
    public static ArrayList<AppVerifyResult> loadResults(Context context) {
        ArrayList<AppVerifyResult> list = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        try {
            InputStream is = context.openFileInput(FILE_NAME);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
        } catch (IOException e) {
            // 처음 실행 시 파일 없을 수 있음 → 무시
            return list;
        }

        try {
            JSONArray array = new JSONArray(sb.toString());
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                AppVerifyResult r = new AppVerifyResult(
                        obj.getString("dateTime"),
                        obj.getInt("successRate"),
                        obj.getDouble("appRunSeconds"),
                        obj.getInt("uiResponseMs"),
                        obj.getInt("connectionStability")
                );
                list.add(r);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return list;
    }
}

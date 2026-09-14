package com.sgggzg.qmzg;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Log;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public class OcrEngine {

    private static final String TAG = "OcrEngine";
    private TessBaseAPI tessAPI;
    private boolean isReady = false;

    public interface InitCallback {
        void onSuccess();
        void onFailure(String errorMsg);
    }

    public void init(final Context context, final InitCallback callback) {
        new Thread(() -> {
            try {
                File dataDir = new File(context.getFilesDir(), "tesseract");
                if (!dataDir.exists() && !dataDir.mkdirs()) {
                    if (callback != null) callback.onFailure("创建 tessdata 目录失败");
                    return;
                }

                File trainedData = new File(dataDir, "tessdata/chi_sim.traineddata");
                if (!trainedData.exists() || trainedData.length() == 0) {
                    File tessdataDir = new File(dataDir, "tessdata");
                    if (!tessdataDir.exists()) tessdataDir.mkdirs();

                    try (InputStream in = context.getAssets().open("tessdata/chi_sim.traineddata");
                    OutputStream out = new FileOutputStream(trainedData)) {
                        byte[] buffer = new byte[16384];
                        int len;
                        long total = 0;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                            total += len;
                        }
                        Log.i(TAG, "语言包复制成功，大小: " + total + " 字节");
                    } catch (Exception e) {
                        if (callback != null) callback.onFailure("复制语言包失败: " + e.getMessage());
                        return;
                    }
                }

                tessAPI = new TessBaseAPI();
                boolean success = tessAPI.init(dataDir.getAbsolutePath(), "chi_sim");
                if (success) {
                    tessAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);

                    // 【核心修复】处理白名单开关与自动补充逻辑
                    SharedPreferences prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE);

                    boolean whitelistEnabled = prefs.getBoolean("whitelist_enabled", true);
                    String userWhitelist = prefs.getString("whitelist", 
                                                           "0123456789来自军团的成功坚守至正在尝试占领计时结束击飞了一名敌人，。！？-犁庭扫穴平定中原快来阻止他们已经达成占领条件");

                    if (!whitelistEnabled || userWhitelist.isEmpty()) {
                        // 情况1：用户关闭了白名单，或者白名单被用户清空 -> 全量识别
                        tessAPI.setVariable("tessedit_char_whitelist", "");
                        Log.i(TAG, "白名单模式已关闭，启用全量识别");
                    } else {
                        // 情况2：白名单开启且不为空，进行自动补充
                        StringBuilder sb = new StringBuilder(userWhitelist);
                        boolean changed = false;

                        try {
                            // 提取条件组里的字符
                            org.json.JSONArray groupArray = new org.json.JSONArray(prefs.getString("conditionGroups", "[]"));
                            for (int i = 0; i < groupArray.length(); i++) {
                                org.json.JSONArray inner = groupArray.getJSONArray(i);
                                for (int j = 0; j < inner.length(); j++) {
                                    String kw = inner.getString(j);
                                    for (char c : kw.toCharArray()) {
                                        if (sb.indexOf(String.valueOf(c)) == -1) {
                                            sb.append(c);
                                            changed = true;
                                        }
                                    }
                                }
                            }
                            // 提取剔除词里的字符
                            org.json.JSONArray excludeArray = new org.json.JSONArray(prefs.getString("excludeWords", "[]"));
                            for (int i = 0; i < excludeArray.length(); i++) {
                                String ew = excludeArray.getString(i);
                                for (char c : ew.toCharArray()) {
                                    if (sb.indexOf(String.valueOf(c)) == -1) {
                                        sb.append(c);
                                        changed = true;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "自动补充白名单解析异常: " + e.getMessage());
                        }

                        // 如果有新增字符，保存回配置，方便用户下次查看
                        if (changed) {
                            prefs.edit().putString("whitelist", sb.toString()).apply();
                            Log.i(TAG, "白名单已自动补充新字符");
                        }

                        String finalWhitelist = sb.toString();
                        Log.i(TAG, "最终使用的白名单: " + finalWhitelist);
                        tessAPI.setVariable("tessedit_char_whitelist", finalWhitelist);
                    }

                    tessAPI.setVariable("tessedit_ocr_engine_mode", "3");
                    isReady = true;
                    if (callback != null) callback.onSuccess();
                } else {
                    if (callback != null) callback.onFailure("Tesseract 初始化失败");
                }
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                if (callback != null) callback.onFailure("初始化异常: " + sw.toString());
            }
        }).start();
    }

    public String recognize(Bitmap bitmap) {
        if (!isReady || tessAPI == null || bitmap == null) return "";

        try {
            Bitmap processedBitmap = Bitmap.createScaledBitmap(bitmap,
                                                               bitmap.getWidth() * 2, bitmap.getHeight() * 2, true);

            tessAPI.setImage(processedBitmap);
            String result = tessAPI.getUTF8Text();

            processedBitmap.recycle();
            return result != null ? result : "";
        } catch (Exception e) {
            Log.e(TAG, "OCR识别异常: " + e.getMessage());
            return "";
        }
    }

    public void recycle() {
        isReady = false;
        if (tessAPI != null) {
            try {
                tessAPI.recycle();
            } catch (Exception e) {
                Log.e(TAG, "Tesseract 释放异常: " + e.getMessage());
            }
            tessAPI = null;
        }
    }

    public boolean isReady() {
        return isReady;
    }
}

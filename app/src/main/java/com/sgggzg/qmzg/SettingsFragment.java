package com.sgggzg.qmzg;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.flexbox.FlexboxLayout;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class SettingsFragment extends Fragment {

    private MaterialSwitch swVibrate, swSound, swWhitelistEnabled, swSkipNoChange;
    private EditText etTopRatio, etBottomRatio, etOcrInterval;
    private Button btnPreview, btnAddConditionGroup, btnEditWhitelist, btnEditExcludeWords;
    private FlexboxLayout layoutConditionContainer;

    private SharedPreferences prefs;
    private List<List<String>> conditionGroups = new ArrayList<>();
    private List<String> excludeWords = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        prefs = requireActivity().getSharedPreferences("config", Context.MODE_PRIVATE);

        // 绑定控件
        swVibrate = view.findViewById(R.id.swVibrate);
        swSound = view.findViewById(R.id.swSound);
        swWhitelistEnabled = view.findViewById(R.id.swWhitelistEnabled);
        swSkipNoChange = view.findViewById(R.id.swSkipNoChange);
        etTopRatio = view.findViewById(R.id.etTopRatio);
        etBottomRatio = view.findViewById(R.id.etBottomRatio);
        etOcrInterval = view.findViewById(R.id.etOcrInterval);
        btnPreview = view.findViewById(R.id.btnPreview);
        btnAddConditionGroup = view.findViewById(R.id.btnAddConditionGroup);
        btnEditWhitelist = view.findViewById(R.id.btnEditWhitelist);
        btnEditExcludeWords = view.findViewById(R.id.btnEditExcludeWords);
        layoutConditionContainer = view.findViewById(R.id.layoutConditionContainer);

        loadData();
        renderConditionGroups();

        // 1. 开关联动
        swVibrate.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("vibrate", isChecked).apply());
        swSound.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("sound", isChecked).apply());
        swWhitelistEnabled.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("whitelist_enabled", isChecked).apply());
        swSkipNoChange.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("no_change_skip", isChecked).apply());

        // 2. 区域边界保存
        View.OnFocusChangeListener saveRatioListener = (v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    prefs.edit()
                        .putFloat("topRatio", Float.parseFloat(etTopRatio.getText().toString()))
                        .putFloat("bottomRatio", Float.parseFloat(etBottomRatio.getText().toString()))
                        .apply();
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
        };
        etTopRatio.setOnFocusChangeListener(saveRatioListener);
        etBottomRatio.setOnFocusChangeListener(saveRatioListener);

        // OCR 间隔的校验与保存（限制最低 1.5 秒，四舍五入保留两位小数）
        etOcrInterval.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    float val = Float.parseFloat(etOcrInterval.getText().toString());
                    if (val < 1.5f) {
                        val = 1.5f;
                        Toast.makeText(requireActivity(), "间隔不能低于 1.5 秒，已重置", Toast.LENGTH_SHORT).show();
                    }
                    val = Math.round(val * 100) / 100.0f;
                    etOcrInterval.setText(String.valueOf(val));
                    prefs.edit().putFloat("ocr_interval", val).apply();
                } catch (NumberFormatException e) {
                    etOcrInterval.setText("3.0");
                    prefs.edit().putFloat("ocr_interval", 3.0f).apply();
                }
            }
        });

        // 3. 预览区域（修复：改为带返回结果的方式）
        btnPreview.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(requireActivity(), PreviewActivity.class);
                intent.putExtra("top", Float.parseFloat(etTopRatio.getText().toString()));
                intent.putExtra("bottom", Float.parseFloat(etBottomRatio.getText().toString()));
                // 【核心修复】使用带结果返回的方式启动
                startActivityForResult(intent, 1005);
            } catch (Exception e) {
                Toast.makeText(requireActivity(), "比例输入错误", Toast.LENGTH_SHORT).show();
            }
        });

        // 4. 条件组
        btnAddConditionGroup.setOnClickListener(v -> showAddConditionDialog());

        // 5. 白名单编辑
        btnEditWhitelist.setOnClickListener(v -> showTextEditDialog("修改OCR白名单", "whitelist",
                                                                    "0123456789来自军团的成功坚守至正在尝试占领计时结束击飞了一名敌人，。！？-犁庭扫穴平定中原快来阻止他们已经达成占领条件"));

        // 6. 剔除词编辑
        btnEditExcludeWords.setOnClickListener(v -> showManageExcludeDialog());

        return view;
    }

    // 【核心新增】接收预览界面返回的上下边界值，并立刻更新
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1005 && resultCode == Activity.RESULT_OK && data != null) {
            float top = data.getFloatExtra("top", 0.66f);
            float bottom = data.getFloatExtra("bottom", 0.81f);

            // 1. 更新输入框
            etTopRatio.setText(String.valueOf(top));
            etBottomRatio.setText(String.valueOf(bottom));

            // 2. 保存到配置
            prefs.edit()
                .putFloat("topRatio", top)
                .putFloat("bottomRatio", bottom)
                .apply();

            // 3. 写入日志方便排查
            AppState.appendLog("预览区域已应用: top=" + top + ", bottom=" + bottom);
        }
    }

    private void loadData() {
        swVibrate.setChecked(prefs.getBoolean("vibrate", true));
        swSound.setChecked(prefs.getBoolean("sound", true));
        swWhitelistEnabled.setChecked(prefs.getBoolean("whitelist_enabled", true));
        swSkipNoChange.setChecked(prefs.getBoolean("no_change_skip", true));

        etTopRatio.setText(String.valueOf(prefs.getFloat("topRatio", 0.66f)));
        etBottomRatio.setText(String.valueOf(prefs.getFloat("bottomRatio", 0.81f)));
        etOcrInterval.setText(String.valueOf(prefs.getFloat("ocr_interval", 3.0f)));

        String jsonStr = prefs.getString("conditionGroups", "[[\"158\",\"正在尝试占领\"]]");
        try {
            JSONArray jsonArray = new JSONArray(jsonStr);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONArray groupArray = jsonArray.getJSONArray(i);
                List<String> group = new ArrayList<>();
                for (int j = 0; j < groupArray.length(); j++) group.add(groupArray.getString(j));
                conditionGroups.add(group);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            List<String> defaultGroup = new ArrayList<>();
            defaultGroup.add("158");
            defaultGroup.add("正在尝试占领");
            conditionGroups.add(defaultGroup);
        }

        String excludeJson = prefs.getString("excludeWords", "[\"击飞了一名敌人\",\"成功坚守至占领计时结束\",\"来自101\"]");
        excludeWords.clear();
        try {
            JSONArray jsonArray = new JSONArray(excludeJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                excludeWords.add(jsonArray.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            excludeWords.add("击飞了一名敌人");
            excludeWords.add("成功坚守至占领计时结束");
        }
    }

    private void saveConditionGroups() {
        JSONArray jsonArray = new JSONArray();
        for (List<String> group : conditionGroups) {
            JSONArray groupArray = new JSONArray();
            for (String keyword : group) {
                groupArray.put(keyword);
            }
            jsonArray.put(groupArray);
        }
        prefs.edit().putString("conditionGroups", jsonArray.toString()).apply();
    }

    private void renderConditionGroups() {
        layoutConditionContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireActivity());

        for (int i = 0; i < conditionGroups.size(); i++) {
            final int groupIndex = i;
            List<String> group = conditionGroups.get(i);

            View groupView = inflater.inflate(R.layout.item_condition_group, layoutConditionContainer, false);
            TextView tvLogic = groupView.findViewById(R.id.tvGroupLogic);
            FlexboxLayout layoutKeywords = groupView.findViewById(R.id.layoutKeywords);
            Button btnDeleteGroup = groupView.findViewById(R.id.btnDeleteGroup);
            Button btnAddKeyword = groupView.findViewById(R.id.btnAddKeyword);

            tvLogic.setText("条件组 " + (groupIndex + 1) + "：需同时满足以下关键词");

            layoutKeywords.removeAllViews();
            for (int j = 0; j < group.size(); j++) {
                final int keywordIndex = j;
                String keyword = group.get(j);

                TextView tvKeyword = (TextView) inflater.inflate(R.layout.item_keyword_tag, layoutKeywords, false);
                tvKeyword.setText(keyword + "  ×");

                tvKeyword.setOnClickListener(v -> {
                    conditionGroups.get(groupIndex).remove(keywordIndex);
                    if (conditionGroups.get(groupIndex).isEmpty()) {
                        conditionGroups.remove(groupIndex);
                    }
                    saveConditionGroups();
                    renderConditionGroups();
                });
                layoutKeywords.addView(tvKeyword);
            }

            btnDeleteGroup.setOnClickListener(v -> {
                conditionGroups.remove(groupIndex);
                saveConditionGroups();
                renderConditionGroups();
            });

            btnAddKeyword.setOnClickListener(v -> showAddKeywordDialog(groupIndex));

            layoutConditionContainer.addView(groupView);
        }
    }

    private void showAddConditionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle("新建条件组");
        final EditText input = new EditText(requireActivity());
        input.setHint("输入第一个关键词 (如 158)");
        builder.setView(input);
        builder.setPositiveButton("创建", (dialog, which) -> {
            String keyword = input.getText().toString().trim();
            if (!keyword.isEmpty()) {
                List<String> newGroup = new ArrayList<>();
                newGroup.add(keyword);
                conditionGroups.add(newGroup);
                saveConditionGroups();
                renderConditionGroups();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showAddKeywordDialog(final int groupIndex) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle("添加关键词到条件组 " + (groupIndex + 1));
        final EditText input = new EditText(requireActivity());
        input.setHint("输入关键词 (如 正在尝试占领)");
        builder.setView(input);
        builder.setPositiveButton("添加", (dialog, which) -> {
            String keyword = input.getText().toString().trim();
            if (!keyword.isEmpty()) {
                conditionGroups.get(groupIndex).add(keyword);
                saveConditionGroups();
                renderConditionGroups();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void saveExcludeWords() {
        org.json.JSONArray jsonArray = new org.json.JSONArray();
        for (String word : excludeWords) {
            jsonArray.put(word);
        }
        prefs.edit().putString("excludeWords", jsonArray.toString()).apply();
    }

    private void showManageExcludeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        View view = LayoutInflater.from(requireActivity()).inflate(R.layout.dialog_manage_exclude, null);
        builder.setView(view);
        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);

        FlexboxLayout layoutExcludeContainer = view.findViewById(R.id.layoutExcludeContainer);
        Button btnAddExclude = view.findViewById(R.id.btnAddExclude);
        Button btnClose = view.findViewById(R.id.btnCloseExcludeDialog);

        renderExcludeTags(layoutExcludeContainer);

        btnAddExclude.setOnClickListener(v -> {
            AlertDialog.Builder inputBuilder = new AlertDialog.Builder(requireActivity());
            inputBuilder.setTitle("添加新的剔除词");
            final EditText input = new EditText(requireActivity());
            input.setHint("例如：连斩 / 一骑当千");
            inputBuilder.setView(input);
            inputBuilder.setPositiveButton("添加", (d, which) -> {
                String word = input.getText().toString().trim();
                if (!word.isEmpty() && !excludeWords.contains(word)) {
                    excludeWords.add(word);
                    saveExcludeWords();
                    renderExcludeTags(layoutExcludeContainer);
                }
            });
            inputBuilder.setNegativeButton("取消", null);
            inputBuilder.show();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void renderExcludeTags(FlexboxLayout container) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireActivity());
        for (int i = 0; i < excludeWords.size(); i++) {
            final int index = i;
            String word = excludeWords.get(i);
            TextView tag = (TextView) inflater.inflate(R.layout.item_keyword_tag, container, false);
            tag.setText(word + "  ×");
            tag.setOnClickListener(v -> {
                excludeWords.remove(index);
                saveExcludeWords();
                renderExcludeTags(container);
            });
            container.addView(tag);
        }
    }

    private void showTextEditDialog(String title, final String key, String defaultVal) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        View view = LayoutInflater.from(requireActivity()).inflate(R.layout.dialog_text_edit, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);

        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        final EditText etInput = view.findViewById(R.id.etDialogInput);
        Button btnCancel = view.findViewById(R.id.btnDialogCancel);
        Button btnSave = view.findViewById(R.id.btnDialogSave);

        tvTitle.setText(title);
        etInput.setText(prefs.getString(key, defaultVal));
        etInput.setSelection(etInput.getText().length());

        etInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String str = s.toString();
                    StringBuilder filtered = new StringBuilder();
                    boolean hasInvalid = false;
                    for (char c : str.toCharArray()) {
                        if (Character.isWhitespace(c)) { hasInvalid = true; continue; }
                        if (Character.isLetterOrDigit(c) || (c >= 0x4E00 && c <= 0x9FFF)) {
                            filtered.append(c);
                        }
                        else if ("，。！？、-—|,.!?;:'\"()（）【】《》".indexOf(c) != -1) {
                            filtered.append(c);
                        }
                        else { hasInvalid = true; }
                    }
                    if (hasInvalid) {
                        etInput.removeTextChangedListener(this);
                        etInput.setText(filtered.toString());
                        etInput.setSelection(filtered.length());
                        etInput.addTextChangedListener(this);
                        Toast.makeText(requireActivity(), "已过滤非法字符", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String val = etInput.getText().toString().trim();
            prefs.edit().putString(key, val).apply();
            Toast.makeText(requireActivity(), "已保存，重启监控后生效", Toast.LENGTH_LONG).show();
            dialog.dismiss();
        });

        dialog.show();
    }
}

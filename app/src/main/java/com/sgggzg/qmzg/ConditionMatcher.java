package com.sgggzg.qmzg;

import java.util.List;

public class ConditionMatcher {

    // 【新增】日志回调接口
    public interface MatchLogCallback {
        void onLog(String msg);
    }

    public static boolean shouldTriggerAlarm(String text, 
                                             List<List<String>> conditionGroups, 
                                             List<String> excludeWords,
                                             MatchLogCallback logCallback) { // 【修改】传入日志回调
        if (text == null || text.trim().isEmpty()) return false;
        if (conditionGroups == null || conditionGroups.isEmpty()) return false;

        String[] lines = text.split("\n");
        for (String line : lines) {
            // 1. 清洗行内空格（保留行结构，避免跨行误报）
            String cleanedLine = line.replace(" ", "").replace("　", "");
            if (cleanedLine.isEmpty()) continue;

            // 2. 排除词过滤（出现其一则忽略本行）
            boolean isExcluded = false;
            if (excludeWords != null) {
                for (String excludeWord : excludeWords) {
                    if (cleanedLine.contains(excludeWord)) {
                        isExcluded = true;
                        // 【新增】输出剔除词命中日志
                        if (logCallback != null) {
                            logCallback.onLog("检测到剔除词 [" + excludeWord + "]，忽略本行: " + cleanedLine);
                        }
                        break;
                    }
                }
            }
            if (isExcluded) continue;

            // 3. 条件组匹配（块外 OR，块内 AND）
            for (List<String> group : conditionGroups) {
                if (group == null || group.isEmpty()) continue;
                boolean groupMatched = true;
                for (String keyword : group) {
                    if (!cleanedLine.contains(keyword)) {
                        groupMatched = false; // 组内有一个关键词没匹配上，该组判定失败
                        break;
                    }
                }
                if (groupMatched) {
                    // 只要有一组匹配成功，立即返回 true
                    if (logCallback != null) {
                        logCallback.onLog("匹配到目标行(已清洗空格): " + cleanedLine);
                    }
                    return true; 
                }
            }
        }
        return false;
    }
}

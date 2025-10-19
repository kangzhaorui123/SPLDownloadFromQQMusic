package com.kenny.spldownloader;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.*;

public class LrcConverter {

    public String convertYrcToStandardLrc(String yrcContent) {
        if (yrcContent == null || yrcContent.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        String[] lines = yrcContent.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                result.append("\n");
                continue;
            }

            if (line.startsWith("[ti:") || line.startsWith("[ar:") || line.startsWith("[al:") ||
                    line.startsWith("[by:") || line.startsWith("[offset:")) {
                result.append(line).append("\n");
                continue;
            }

            if (line.startsWith("[") && line.contains("]") && line.contains("(") && line.contains(")")) {
                String convertedLine = convertYrcLine(line);
                if (convertedLine != null && !convertedLine.isEmpty()) {
                    result.append(convertedLine).append("\n");
                }
            } else if (line.startsWith("[") && line.contains("]") && !line.contains("(")) {
                result.append(line).append("\n");
            } else {
                result.append(line).append("\n");
            }
        }

        return result.toString();
    }

    private String convertYrcLine(String line) {
        try {
            int firstBracketEnd = line.indexOf(']');
            if (firstBracketEnd == -1) return line;

            String timePart = line.substring(0, firstBracketEnd + 1);
            String contentPart = line.substring(firstBracketEnd + 1);

            if (contentPart.isEmpty()) {
                return "";
            }

            StringBuilder convertedContent = new StringBuilder();
            Pattern pattern = Pattern.compile("([^()]*)\\((\\d+),(\\d+)\\)");
            Matcher matcher = pattern.matcher(contentPart);

            long lastEndTime = 0;
            boolean hasMatches = false;

            while (matcher.find()) {
                hasMatches = true;
                String word = matcher.group(1);
                if (word == null) word = "";

                long startTime = Long.parseLong(Objects.requireNonNull(matcher.group(2)));
                long duration = Long.parseLong(Objects.requireNonNull(matcher.group(3)));
                long endTime = startTime + duration;

                String startTimeStr = formatTime(startTime);
                convertedContent.append(startTimeStr).append(word);

                lastEndTime = endTime;
            }

            if (hasMatches && lastEndTime > 0) {
                String endTimeStr = formatTime(lastEndTime);
                convertedContent.append(endTimeStr);
                return convertedContent.toString();
            } else {
                return contentPart;
            }

        } catch (Exception e) {
            System.err.println("转换行失败: " + line + ", 错误: " + e.getMessage());
            int firstBracketEnd = line.indexOf(']');
            return firstBracketEnd != -1 ? line.substring(firstBracketEnd + 1) : line;
        }
    }

    private String formatTime(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long hundredths = (milliseconds % 1000) / 10;
        // 修复：明确指定Locale为US，确保时间格式一致
        return String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, hundredths);
    }
}
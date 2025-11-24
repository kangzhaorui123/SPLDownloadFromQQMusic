// LyricResponse.java
package com.kenny.spldownloader.model;

import org.json.JSONObject;

public class LyricResponse {
    private String lrc;
    private String yrc;
    private String trans;
    private String roma;

    public static LyricResponse fromJson(JSONObject data) {
        LyricResponse response = new LyricResponse();
        response.lrc = data.optString("lrc", "");
        response.yrc = data.optString("yrc", "");
        response.trans = data.optString("trans", "");
        response.roma = data.optString("roma", "");
        return response;
    }

    // Getter方法
    public String getLrc() { return lrc; }
    public String getYrc() { return yrc; }
    public String getTrans() { return trans; }
    public String getRoma() { return roma; }

    public boolean hasNormalLyric() {
        return lrc != null && !lrc.trim().isEmpty();
    }

    public boolean hasWordByWordLyric() {
        return yrc != null && !yrc.trim().isEmpty();
    }
}
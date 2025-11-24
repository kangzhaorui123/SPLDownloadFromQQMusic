// SongInfo.java
package com.kenny.spldownloader.model;

import androidx.annotation.NonNull;
import java.util.Objects;

public class SongInfo {
    private String mid;
    private String songName;
    private String singer;
    private DownloadStatus downloadStatus = DownloadStatus.NONE;

    public enum DownloadStatus {
        NONE, SUCCESS, FAILED
    }

    // 构造方法
    public SongInfo() {}

    public SongInfo(String mid, String songName, String singer) {
        this.mid = mid;
        this.songName = songName;
        this.singer = singer;
    }

    // Getter和Setter
    public String getMid() { return mid; }
    public void setMid(String mid) { this.mid = mid; }

    public String getSongName() { return songName; }
    public void setSongName(String songName) { this.songName = songName; }

    public String getSinger() { return singer; }
    public void setSinger(String singer) {
        this.singer = singer != null ? singer.replace("/", "&") : "未知歌手";
    }

    public DownloadStatus getDownloadStatus() { return downloadStatus; }
    public void setDownloadStatus(DownloadStatus downloadStatus) {
        this.downloadStatus = downloadStatus;
    }

    public String getFileName() {
        String cleanSongName = songName != null ? songName : "未知歌曲";
        String cleanSinger = singer != null ? singer : "未知歌手";

        String fileName = cleanSongName + " - " + cleanSinger;

        // 移除特殊字符
        fileName = fileName.replaceAll("[<>:\"/\\\\|?*]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("\\s+", " ")
                .trim();

        // 确保以.lrc结尾
        if (!fileName.toLowerCase().endsWith(".lrc")) {
            fileName += ".lrc";
        }

        return fileName;
    }

    @NonNull
    @Override
    public String toString() {
        return songName + " - " + singer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SongInfo songInfo = (SongInfo) o;
        return Objects.equals(mid, songInfo.mid) &&
                Objects.equals(songName, songInfo.songName) &&
                Objects.equals(singer, songInfo.singer) &&
                downloadStatus == songInfo.downloadStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mid, songName, singer, downloadStatus);
    }
}
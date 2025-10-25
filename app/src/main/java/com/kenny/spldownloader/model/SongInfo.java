package com.kenny.spldownloader.model;

import androidx.annotation.NonNull;

import java.util.Objects;

public class SongInfo {
    private String mid;
    private String songName;
    private String singer;

    // 下载状态枚举
    public enum DownloadStatus {
        NONE,       // 未下载
        SUCCESS,    // 下载成功
        FAILED      // 下载失败
    }

    private DownloadStatus downloadStatus = DownloadStatus.NONE;

    public String getMid() { return mid; }
    public void setMid(String mid) { this.mid = mid; }

    public String getSongName() { return songName; }
    public void setSongName(String songName) { this.songName = songName; }

    public String getSinger() { return singer; }
    public void setSinger(String singer) {
        // 将歌手分隔符从斜杠改为&
        this.singer = singer != null ? singer.replace("/", "&") : "未知歌手";
    }

    public DownloadStatus getDownloadStatus() { return downloadStatus; }
    public void setDownloadStatus(DownloadStatus downloadStatus) { this.downloadStatus = downloadStatus; }

    public String getFileName() {
        String cleanSongName = songName != null ? songName : "未知歌曲";
        String cleanSinger = singer != null ? singer : "未知歌手";

        // 生成文件名，确保以.lrc结尾
        String fileName = cleanSongName + " - " + cleanSinger;

        // 移除或替换特殊字符
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

    // 添加equals和hashCode方法，确保状态更新能正确反映在UI上
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
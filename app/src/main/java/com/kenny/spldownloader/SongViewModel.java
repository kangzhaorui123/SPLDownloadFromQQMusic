package com.kenny.spldownloader;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.kenny.spldownloader.model.SongInfo;

import java.util.ArrayList;
import java.util.List;

public class SongViewModel extends ViewModel {
    private final MutableLiveData<List<SongInfo>> songList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> parseError = new MutableLiveData<>();
    private final MutableLiveData<ParseProgress> parseProgress = new MutableLiveData<>(); // 修改：使用ParseProgress对象

    // 解析进度数据类
        public record ParseProgress(int current, int total) {
    }

    public LiveData<List<SongInfo>> getSongList() {
        return songList;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getParseError() {
        return parseError;
    }

    public LiveData<ParseProgress> getParseProgress() {
        return parseProgress;
    }

    public void setSongList(List<SongInfo> songs) {
        songList.setValue(songs != null ? new ArrayList<>(songs) : new ArrayList<>());
    }

    public void addSong(SongInfo song) {
        List<SongInfo> currentList = songList.getValue();
        if (currentList != null) {
            currentList.add(song);
            songList.setValue(new ArrayList<>(currentList));
        }
    }

    public void clearSongs() {
        songList.setValue(new ArrayList<>());
    }

    public void setLoading(boolean loading) {
        isLoading.setValue(loading);
    }

    public void setParseError(String error) {
        parseError.setValue(error);
    }

    public void setParseProgress(ParseProgress progress) {
        parseProgress.setValue(progress);
    }

    public void updateSongStatus(int position, SongInfo.DownloadStatus status) {
        List<SongInfo> currentList = songList.getValue();
        if (currentList != null && position >= 0 && position < currentList.size()) {
            currentList.get(position).setDownloadStatus(status);
            songList.postValue(new ArrayList<>(currentList)); // 修复：使用postValue避免线程问题
        }
    }

    public void updateSong(SongInfo song) {
        List<SongInfo> currentList = songList.getValue();
        if (currentList != null) {
            for (int i = 0; i < currentList.size(); i++) {
                if (currentList.get(i).getMid().equals(song.getMid())) {
                    currentList.set(i, song);
                    songList.postValue(new ArrayList<>(currentList)); // 修复：使用postValue避免线程问题
                    break;
                }
            }
        }
    }

    public record RetryProgress(int currentRetry, int maxRetry, int retryingCount) {
    }

    private final MutableLiveData<RetryProgress> retryProgress = new MutableLiveData<>();

    public LiveData<RetryProgress> getRetryProgress() {
        return retryProgress;
    }

    public void setRetryProgress(RetryProgress progress) {
        retryProgress.postValue(progress);
    }
}
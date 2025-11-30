// SongViewModel.java
package com.kenny.spldownloader.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.kenny.spldownloader.model.SongInfo;

import java.util.ArrayList;
import java.util.List;

public class SongViewModel extends ViewModel {
    private final MutableLiveData<List<SongInfo>> songList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<ProgressInfo> progressInfo = new MutableLiveData<>();

    // 分页相关状态
    private final MutableLiveData<Boolean> hasMoreData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadMoreLoading = new MutableLiveData<>(false);
    private String currentSearchKeyword = "";
    private int currentPage = 1;
    private boolean isSearchMode = false;

    public LiveData<List<SongInfo>> getSongList() { return songList; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<ProgressInfo> getProgressInfo() { return progressInfo; }
    public LiveData<Boolean> getHasMoreData() { return hasMoreData; }
    public LiveData<Boolean> getLoadMoreLoading() { return loadMoreLoading; }

    public void setSongList(List<SongInfo> songs) {
        songList.setValue(songs != null ? new ArrayList<>(songs) : new ArrayList<>());
        currentPage = 1;
        hasMoreData.setValue(isSearchMode && songs != null && !songs.isEmpty());
    }

    public void appendSongList(List<SongInfo> songs) {
        List<SongInfo> current = songList.getValue();
        if (current != null && songs != null) {
            current.addAll(songs);
            songList.setValue(new ArrayList<>(current));
        }
    }

    public void addSong(SongInfo song) {
        List<SongInfo> current = songList.getValue();
        if (current != null) {
            current.add(song);
            songList.setValue(new ArrayList<>(current));
        }
    }

    public void clearSongs() {
        songList.setValue(new ArrayList<>());
        currentPage = 1;
        currentSearchKeyword = "";
        hasMoreData.setValue(false);
    }

    public void setLoading(boolean loading) {
        isLoading.setValue(loading);
    }

    public void setLoadMoreLoading(boolean loading) {
        loadMoreLoading.setValue(loading);
    }

    public void setError(String error) {
        errorMessage.setValue(error);
    }

    public void clearError() {
        errorMessage.setValue(null);
    }

    public void setProgress(ProgressInfo progress) {
        progressInfo.setValue(progress);
    }

    public void setHasMoreData(boolean hasMore) {
        hasMoreData.setValue(isSearchMode && hasMore);
    }

    public void updateSongStatus(int position, SongInfo.DownloadStatus status) {
        List<SongInfo> current = songList.getValue();
        if (current != null && position >= 0 && position < current.size()) {
            SongInfo song = current.get(position);
            SongInfo updatedSong = new SongInfo(song.getMid(), song.getSongName(), song.getSinger());
            updatedSong.setDownloadStatus(status);

            current.set(position, updatedSong);
            songList.postValue(new ArrayList<>(current));
        }
    }

    // 分页相关方法
    public void setCurrentSearchKeyword(String keyword) {
        this.currentSearchKeyword = keyword;
    }

    public String getCurrentSearchKeyword() {
        return currentSearchKeyword;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void incrementPage() {
        currentPage++;
    }

    public void resetPagination() {
        currentPage = 1;
        hasMoreData.setValue(false);
    }

    public void setSearchMode(boolean searchMode) {
        this.isSearchMode = searchMode;
        if (!searchMode) {
            hasMoreData.setValue(false);
        }
    }

    public boolean isSearchMode() {
        return isSearchMode;
    }

    public record ProgressInfo(int current, int total, String message) {
    }
}
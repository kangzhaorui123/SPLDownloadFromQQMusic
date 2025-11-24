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

    public LiveData<List<SongInfo>> getSongList() { return songList; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<ProgressInfo> getProgressInfo() { return progressInfo; }

    public void setSongList(List<SongInfo> songs) {
        songList.setValue(songs != null ? new ArrayList<>(songs) : new ArrayList<>());
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
    }

    public void setLoading(boolean loading) {
        isLoading.setValue(loading);
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

    public record ProgressInfo(int current, int total, String message) {
    }
}
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

    public LiveData<List<SongInfo>> getSongList() {
        return songList;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getParseError() {
        return parseError;
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

    public void updateSongStatus(int position, SongInfo.DownloadStatus status) {
        List<SongInfo> currentList = songList.getValue();
        if (currentList != null && position >= 0 && position < currentList.size()) {
            currentList.get(position).setDownloadStatus(status);
            songList.setValue(new ArrayList<>(currentList));
        }
    }

    public void updateSong(SongInfo song) {
        List<SongInfo> currentList = songList.getValue();
        if (currentList != null) {
            for (int i = 0; i < currentList.size(); i++) {
                if (currentList.get(i).getMid().equals(song.getMid())) {
                    currentList.set(i, song);
                    songList.setValue(new ArrayList<>(currentList));
                    break;
                }
            }
        }
    }
}
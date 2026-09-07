package com.tiktoksoundalert.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.tiktoksoundalert.models.EventType;
import com.tiktoksoundalert.tiktok.models.TikTokEvent;

import java.util.ArrayList;
import java.util.List;

public class LiveEventViewModel extends ViewModel {

    private final MutableLiveData<List<TikTokEvent>> events = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> connected = new MutableLiveData<>(false);

    public LiveData<List<TikTokEvent>> getEvents() {
        return events;
    }

    public LiveData<Boolean> isConnected() {
        return connected;
    }

    public synchronized void addEvent(TikTokEvent event) {
        if (event.getType() == EventType.DEBUG) return;
        List<TikTokEvent> current = new ArrayList<>(events.getValue());
        current.add(0, event);
        if (current.size() > 500) {
            current = current.subList(0, 500);
        }
        events.postValue(current);
    }

    public void setConnected(boolean isConnected) {
        connected.postValue(isConnected);
    }

    public void clear() {
        events.postValue(new ArrayList<>());
    }
}
package com.tiktoksoundalert.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.tiktoksoundalert.R;
import com.tiktoksoundalert.SettingsManager;
import com.tiktoksoundalert.SettingsRepository;

public class HomeFragment extends Fragment {

    private SettingsManager settingsManager;
    private SwitchCompat swSoundAlert;
    private SwitchCompat swTts;
    private boolean loading;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        swSoundAlert = view.findViewById(R.id.sw_sound_alert);
        swTts = view.findViewById(R.id.sw_tts);

        swSoundAlert.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (settingsManager != null && !loading) {
                settingsManager.setGiftSoundEnabled(isChecked);
            }
        });
        swTts.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (settingsManager != null && !loading) {
                settingsManager.setTtsEnabled(isChecked);
            }
        });

        reloadSettings();
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadSettings();
    }

    private void reloadSettings() {
        long accountId = SettingsRepository.activeAccountId(requireContext());
        settingsManager = new SettingsManager(requireContext(), accountId);
        loading = true;
        if (swSoundAlert != null) {
            swSoundAlert.setChecked(settingsManager.isGiftSoundEnabled());
        }
        if (swTts != null) {
            swTts.setChecked(settingsManager.isTtsEnabled());
        }
        loading = false;
    }
}
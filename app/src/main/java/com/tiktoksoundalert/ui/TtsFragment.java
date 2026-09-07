package com.tiktoksoundalert.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.tabs.TabLayout;

import com.tiktoksoundalert.R;
import com.tiktoksoundalert.SettingsManager;
import com.tiktoksoundalert.SettingsRepository;
import com.tiktoksoundalert.audio.TTSManager;

public class TtsFragment extends Fragment {

    private static final String MODE_BANNED = "banned";
    private static final String MODE_PRIORITY = "priority";
    private static final String MODE_FAVORITES = "favorites";

    private SettingsManager settingsManager;

    private TabLayout tabLayout;
    private NestedScrollView scrollChat;
    private NestedScrollView scrollGift;

    private TextView tvChatSpeed, tvChatPitch, tvChatVolume, tvChatCooldown,
            tvChatQueue, tvChatMaxlen, tvBannedCount, tvPriorityCount, tvFavCount;
    private SeekBar sbChatSpeed, sbChatPitch, sbChatVolume, sbChatCooldown,
            sbChatQueue, sbChatMaxlen;
    private SwitchCompat swLetterSpam, swOnceUser;
    private TextInputEditText etChatTemplate;
    private MaterialButtonToggleGroup groupCmd, groupWho;

    private SwitchCompat swGiftEnabled;
    private TextView tvGiftSpeed, tvGiftPitch, tvGiftVolume, tvGiftMindia, tvGiftCooldown;
    private SeekBar sbGiftSpeed, sbGiftPitch, sbGiftVolume, sbGiftMindia, sbGiftCooldown;
    private TextInputEditText etGiftTemplate;

    private TTSManager previewTts;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabLayout = view.findViewById(R.id.tab_layout);
        scrollChat = view.findViewById(R.id.scroll_chat);
        scrollGift = view.findViewById(R.id.scroll_gift);

        tvChatSpeed = view.findViewById(R.id.tv_chat_speed);
        tvChatPitch = view.findViewById(R.id.tv_chat_pitch);
        tvChatVolume = view.findViewById(R.id.tv_chat_volume);
        tvChatCooldown = view.findViewById(R.id.tv_chat_cooldown);
        tvChatQueue = view.findViewById(R.id.tv_chat_queue);
        tvChatMaxlen = view.findViewById(R.id.tv_chat_maxlen);
        tvBannedCount = view.findViewById(R.id.tv_banned_count);
        tvPriorityCount = view.findViewById(R.id.tv_priority_count);
        tvFavCount = view.findViewById(R.id.tv_fav_count);
        sbChatSpeed = view.findViewById(R.id.sb_chat_speed);
        sbChatPitch = view.findViewById(R.id.sb_chat_pitch);
        sbChatVolume = view.findViewById(R.id.sb_chat_volume);
        sbChatCooldown = view.findViewById(R.id.sb_chat_cooldown);
        sbChatQueue = view.findViewById(R.id.sb_chat_queue);
        sbChatMaxlen = view.findViewById(R.id.sb_chat_maxlen);
        swLetterSpam = view.findViewById(R.id.sw_letter_spam);
        swOnceUser = view.findViewById(R.id.sw_once_user);
        etChatTemplate = view.findViewById(R.id.et_chat_template);
        groupCmd = view.findViewById(R.id.group_cmd);
        groupWho = view.findViewById(R.id.group_who);

        swGiftEnabled = view.findViewById(R.id.sw_gift_enabled);
        tvGiftSpeed = view.findViewById(R.id.tv_gift_speed);
        tvGiftPitch = view.findViewById(R.id.tv_gift_pitch);
        tvGiftVolume = view.findViewById(R.id.tv_gift_volume);
        tvGiftMindia = view.findViewById(R.id.tv_gift_mindia);
        tvGiftCooldown = view.findViewById(R.id.tv_gift_cooldown);
        sbGiftSpeed = view.findViewById(R.id.sb_gift_speed);
        sbGiftPitch = view.findViewById(R.id.sb_gift_pitch);
        sbGiftVolume = view.findViewById(R.id.sb_gift_volume);
        sbGiftMindia = view.findViewById(R.id.sb_gift_mindia);
        sbGiftCooldown = view.findViewById(R.id.sb_gift_cooldown);
        etGiftTemplate = view.findViewById(R.id.et_gift_template);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                boolean gift = tab.getPosition() == 1;
                scrollChat.setVisibility(gift ? View.GONE : View.VISIBLE);
                scrollGift.setVisibility(gift ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });

        bindSlider(sbChatSpeed, tvChatSpeed, "%.1fx");
        bindSlider(sbChatPitch, tvChatPitch, "%.1fx");
        bindSlider(sbChatVolume, tvChatVolume, "%d%%");
        bindSlider(sbChatCooldown, tvChatCooldown, "%d dtk");
        bindSlider(sbChatQueue, tvChatQueue, "%d");
        bindSlider(sbChatMaxlen, tvChatMaxlen, "%d");

        bindSlider(sbGiftSpeed, tvGiftSpeed, "%.1fx");
        bindSlider(sbGiftPitch, tvGiftPitch, "%.1fx");
        bindSlider(sbGiftVolume, tvGiftVolume, "%d%%");
        bindSlider(sbGiftMindia, tvGiftMindia, "%d");
        bindSlider(sbGiftCooldown, tvGiftCooldown, "%d dtk");

        view.findViewById(R.id.row_banned).setOnClickListener(v ->
                openWordList(MODE_BANNED));
        view.findViewById(R.id.row_priority).setOnClickListener(v ->
                openWordList(MODE_PRIORITY));
        view.findViewById(R.id.row_favorites).setOnClickListener(v ->
                openWordList(MODE_FAVORITES));

        ((MaterialButton) view.findViewById(R.id.btn_chat_preview)).setOnClickListener(v ->
                previewChat());
        ((MaterialButton) view.findViewById(R.id.btn_gift_preview)).setOnClickListener(v ->
                previewGift());

        view.findViewById(R.id.btn_tts_save).setOnClickListener(v -> saveSettings());

        reloadSettings();
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadSettings();
    }

    /** Slider hanya memperbarui label; belum menulis ke DB. */
    private void bindSlider(SeekBar bar, TextView label, String format) {
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText(String.format(format, (Object) valueFor(progress, format)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    private Object valueFor(int progress, String format) {
        if (format.endsWith("%%") || format.endsWith("%d") || format.endsWith("%d dtk")) {
            return progress;
        }
        return progress / 10f;
    }

    private void reloadSettings() {
        long accountId = SettingsRepository.activeAccountId(requireContext());
        if (settingsManager == null || settingsManager.accountId != accountId) {
            settingsManager = new SettingsManager(requireContext(), accountId);
        } else {
            settingsManager.reload();
        }

        sbChatSpeed.setProgress(clampTo(sbChatSpeed, settingsManager.getInt(
                SettingsRepository.KEY_TTS_SPEED, 10)));
        sbChatPitch.setProgress(clampTo(sbChatPitch, settingsManager.getInt(
                SettingsRepository.KEY_TTS_PITCH, 10)));
        sbChatVolume.setProgress(clampTo(sbChatVolume, settingsManager.getChatTtsVolume()));
        sbChatCooldown.setProgress(clampTo(sbChatCooldown,
                settingsManager.getTtsCooldownMs() / 1000));
        sbChatQueue.setProgress(clampTo(sbChatQueue, settingsManager.getTtsMaxQueue()));
        sbChatMaxlen.setProgress(clampTo(sbChatMaxlen,
                settingsManager.getCommentTtsMaxLength()));
        swLetterSpam.setChecked(settingsManager.isLetterSpamBlocked());
        swOnceUser.setChecked(settingsManager.isOncePerUserEnabled());
        etChatTemplate.setText(settingsManager.getTtsTemplate());
        selectCommand(settingsManager.getTtsCommand());
        selectWho(settingsManager.getAllowedUsersMode());
        updateCounts();

        swGiftEnabled.setChecked(settingsManager.isGiftTtsEnabled());
        sbGiftSpeed.setProgress(clampTo(sbGiftSpeed, settingsManager.getInt(
                SettingsRepository.KEY_GIFT_TTS_SPEED, 10)));
        sbGiftPitch.setProgress(clampTo(sbGiftPitch, settingsManager.getInt(
                SettingsRepository.KEY_GIFT_TTS_PITCH, 10)));
        sbGiftVolume.setProgress(clampTo(sbGiftVolume, settingsManager.getGiftTtsVolume()));
        sbGiftMindia.setProgress(clampTo(sbGiftMindia,
                settingsManager.getGiftTtsMinDiamonds()));
        sbGiftCooldown.setProgress(clampTo(sbGiftCooldown,
                settingsManager.getGiftTtsCooldownMs() / 1000));
        etGiftTemplate.setText(settingsManager.getGiftTtsTemplate());
    }

    private int clampTo(SeekBar bar, int value) {
        return Math.max(0, Math.min(bar.getMax(), value));
    }

    private void selectCommand(String command) {
        int id = R.id.btn_cmd_all;
        if (".".equals(command)) id = R.id.btn_cmd_dot;
        else if ("/".equals(command)) id = R.id.btn_cmd_slash;
        else if ("both".equals(command)) id = R.id.btn_cmd_both;
        groupCmd.check(id);
    }

    private void selectWho(String mode) {
        groupWho.check("favorites".equals(mode) ? R.id.btn_who_fav : R.id.btn_who_all);
    }

    private String commandFromGroup() {
        int id = groupCmd.getCheckedButtonId();
        if (id == R.id.btn_cmd_dot) return ".";
        if (id == R.id.btn_cmd_slash) return "/";
        if (id == R.id.btn_cmd_both) return "both";
        return "";
    }

    private String whoFromGroup() {
        return groupWho.getCheckedButtonId() == R.id.btn_who_fav ? "favorites" : "all";
    }

    private String textOf(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private void saveSettings() {
        if (settingsManager == null) {
            settingsManager = new SettingsManager(requireContext(),
                    SettingsRepository.activeAccountId(requireContext()));
        }

        settingsManager.setTtsSpeed(sbChatSpeed.getProgress() / 10f);
        settingsManager.setTtsPitch(sbChatPitch.getProgress() / 10f);
        settingsManager.setChatTtsVolume(sbChatVolume.getProgress());
        settingsManager.setTtsCooldownMs(sbChatCooldown.getProgress() * 1000);
        settingsManager.setTtsMaxQueue(sbChatQueue.getProgress());
        settingsManager.setCommentTtsMaxLength(sbChatMaxlen.getProgress());
        settingsManager.setLetterSpamBlocked(swLetterSpam.isChecked());
        settingsManager.setOncePerUserEnabled(swOnceUser.isChecked());
        settingsManager.setTtsCommand(commandFromGroup());
        settingsManager.setAllowedUsersMode(whoFromGroup());
        if (!textOf(etChatTemplate).isEmpty()) {
            settingsManager.setTtsTemplate(textOf(etChatTemplate));
        }

        settingsManager.setGiftTtsEnabled(swGiftEnabled.isChecked());
        settingsManager.setGiftTtsSpeed(sbGiftSpeed.getProgress() / 10f);
        settingsManager.setGiftTtsPitch(sbGiftPitch.getProgress() / 10f);
        settingsManager.setGiftTtsVolume(sbGiftVolume.getProgress());
        settingsManager.setGiftTtsMinDiamonds(sbGiftMindia.getProgress());
        settingsManager.setGiftTtsCooldownMs(sbGiftCooldown.getProgress() * 1000);
        if (!textOf(etGiftTemplate).isEmpty()) {
            settingsManager.setGiftTtsTemplate(textOf(etGiftTemplate));
        }

        Toast.makeText(requireContext(), "Pengaturan disimpan", Toast.LENGTH_SHORT).show();
    }

    private void updateCounts() {
        if (settingsManager == null) return;
        tvBannedCount.setText(String.valueOf(settingsManager.getBlockedWords().size()));
        tvPriorityCount.setText(String.valueOf(settingsManager.getPriorityWords().size()));
        tvFavCount.setText(String.valueOf(settingsManager.getFavoriteUsers().size()));
    }

    private void openWordList(String mode) {
        Intent intent = new Intent(requireContext(), WordListActivity.class);
        intent.putExtra(WordListActivity.EXTRA_MODE, mode);
        startActivity(intent);
    }

    private void previewChat() {
        String template = textOf(etChatTemplate);
        if (template.isEmpty()) {
            template = settingsManager.getTtsTemplate();
        }
        String text = template
                .replace("{username}", "User123")
                .replace("{comment}", "Halo, ini preview suara chat.");
        preview(text, sbChatSpeed.getProgress() / 10f,
                sbChatPitch.getProgress() / 10f, sbChatVolume.getProgress());
    }

    private void previewGift() {
        String template = textOf(etGiftTemplate);
        if (template.isEmpty()) {
            template = settingsManager.getGiftTtsTemplate();
        }
        String text = template
                .replace("{username}", "User123")
                .replace("{giftname}", "Rose")
                .replace("{count}", "1")
                .replace("{diamonds}", "5");
        preview(text, sbGiftSpeed.getProgress() / 10f,
                sbGiftPitch.getProgress() / 10f, sbGiftVolume.getProgress());
    }

    private void preview(String text, float speed, float pitch, int volume) {
        if (previewTts == null) {
            previewTts = new TTSManager(requireContext());
            previewTts.setOnReadyListener(new TTSManager.OnTTSReadyListener() {
                @Override
                public void onReady() {
                    speakPreview(text, speed, pitch, volume);
                }

                @Override
                public void onError(String error) { }
            });
        } else if (previewTts.isInitialized()) {
            speakPreview(text, speed, pitch, volume);
        }
    }

    private void speakPreview(String text, float speed, float pitch, int volume) {
        if (previewTts == null) return;
        previewTts.setLanguage(settingsManager.getTtsLanguage());
        previewTts.applyVoice(speed, pitch);
        previewTts.flush();
        previewTts.speak(text, "preview", volume);
    }

    @Override
    public void onDestroyView() {
        if (previewTts != null) {
            previewTts.release();
            previewTts = null;
        }
        super.onDestroyView();
    }
}
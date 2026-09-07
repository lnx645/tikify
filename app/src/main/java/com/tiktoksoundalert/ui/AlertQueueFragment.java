package com.tiktoksoundalert.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.tiktoksoundalert.GiftSoundStore;
import com.tiktoksoundalert.R;
import com.tiktoksoundalert.SettingsManager;
import com.tiktoksoundalert.SettingsRepository;
import com.tiktoksoundalert.audio.GiftSoundManager;
import com.tiktoksoundalert.models.AlertEventRule;
import com.tiktoksoundalert.models.GiftInfo;
import com.tiktoksoundalert.scraper.GiftCatalogScraper;
import com.tiktoksoundalert.scraper.MyInstantsScraper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Alert tab: configures the "Queue Sound Alert (By Event)" system.
 *
 * Each event type (Follow / Share / Any Gift) can be assigned exactly one sound
 * (from the built-in sound library or a user-provided file via the in-app file
 * manager), plus its own enabled switch, volume and preview.
 */
public class AlertQueueFragment extends Fragment {

    private SettingsManager settingsManager;
    private GiftSoundStore soundStore;
    private GiftSoundManager previewManager;
    private List<AlertEventRule> rules = new ArrayList<>();

    private SwitchCompat swAlertQueue;
    private SwitchCompat swAlertInterrupts;
    private boolean reloadingMixSwitch = false;
    private TextView tvEmpty;
    private RecyclerView rvAlerts;
    private AlertAdapter adapter;

    private Map<String, String> builtinLabels = new HashMap<>();
    private PickerHost activePickerHost;

    private final ActivityResultLauncher<Intent> libraryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                            return;
                        }
                        String name = result.getData().getStringExtra(
                                SoundLibraryActivity.EXTRA_SOUND_NAME);
                        String path = result.getData().getStringExtra(
                                SoundLibraryActivity.EXTRA_SOUND_PATH);
                        PickerHost host = activePickerHost;
                        if (host != null && name != null && path != null) {
                            soundStore = new GiftSoundStore(requireContext());
                            if (previewManager != null) {
                                previewManager.loadCustomSoundFromPath(name, path);
                            }
                            host.temp.sound = name;
                            host.btnSelectSound.setText(soundLabel(name));
                            host.sheet.dismiss();
                            activePickerHost = null;
                        } else {
                            Toast.makeText(requireContext(), "Suara tidak valid",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

    private final ActivityResultLauncher<String[]> openAudioLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    this::onAudioPicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_alert_queue, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        swAlertQueue = view.findViewById(R.id.sw_alert_queue);
        tvEmpty = view.findViewById(R.id.tv_empty);
        rvAlerts = view.findViewById(R.id.rv_alerts);
        rvAlerts.setLayoutManager(new LinearLayoutManager(requireContext()));
        buildBuiltinLabels();

        swAlertQueue.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (settingsManager != null) {
                settingsManager.setAlertQueueEnabled(isChecked);
                Toast.makeText(requireContext(),
                        isChecked ? "Queue Sound Alert aktif" : "Queue Sound Alert nonaktif",
                        Toast.LENGTH_SHORT).show();
            }
        });
        androidx.appcompat.widget.SwitchCompat swInterrupt =
                view.findViewById(R.id.sw_alert_interrupts_tts);
        this.swAlertInterrupts = swInterrupt;
        swInterrupt.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (settingsManager != null && !reloadingMixSwitch) {
                settingsManager.setAlertInterruptsTts(isChecked);
                Toast.makeText(requireContext(),
                        isChecked ? "TTS akan dijeda saat suara alert berbunyi"
                                : "TTS tidak dijeda oleh suara alert",
                        Toast.LENGTH_SHORT).show();
            }
        });
        view.findViewById(R.id.btn_add_event).setOnClickListener(v -> openEditor(null));

        adapter = new AlertAdapter(rules, new AlertAdapter.Callbacks() {
            @Override
            public void onEdit(AlertEventRule rule) {
                openEditor(rule);
            }

            @Override
            public void onToggle(AlertEventRule rule, boolean enabled) {
                rule.enabled = enabled;
                persist(rules);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onPreview(AlertEventRule rule) {
                previewSound(rule.sound, rule.volume);
            }

            @Override
            public void onDelete(AlertEventRule rule) {
                confirmDelete(rule);
            }
        });
        rvAlerts.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadSettings();
    }

    private void buildBuiltinLabels() {
        builtinLabels.clear();
        String[] keys = getResources().getStringArray(R.array.sound_keys);
        String[] names = getResources().getStringArray(R.array.sound_names);
        for (int i = 0; i < keys.length && i < names.length; i++) {
            builtinLabels.put(keys[i], names[i]);
        }
    }

    private void reloadSettings() {
        long accountId = SettingsRepository.activeAccountId(requireContext());
        settingsManager = new SettingsManager(requireContext(), accountId);
        soundStore = new GiftSoundStore(requireContext());

        previewManager = new GiftSoundManager(requireContext());
        previewManager.loadBuiltinSounds();
        for (Map.Entry<String, String> e : soundStore.getCustomSounds().entrySet()) {
            previewManager.loadCustomSoundFromPath(e.getKey(), e.getValue());
        }

        swAlertQueue.setChecked(settingsManager.isAlertQueueEnabled());
        reloadingMixSwitch = true;
        try {
            if (swAlertInterrupts != null) {
                swAlertInterrupts.setChecked(settingsManager.isAlertInterruptsTts());
            }
        } finally {
            reloadingMixSwitch = false;
        }
        rules = new ArrayList<>(settingsManager.getAlertQueueRules());
        adapter.submit(rules);
        tvEmpty.setVisibility(rules.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void persist(List<AlertEventRule> list) {
        if (settingsManager != null) {
            settingsManager.setAlertQueueRules(list);
        }
    }

    // ---- editor sheet ----

    private void openEditor(@Nullable AlertEventRule existing) {
        AlertEventRule temp;
        if (existing != null) {
            temp = new AlertEventRule(existing.id,
                    AlertEventRule.TYPE_GIFT_SPECIFIC.equals(existing.type)
                            ? AlertEventRule.TYPE_GIFT_SPECIFIC : existing.type,
                    existing.giftName, existing.sound,
                    existing.enabled, existing.volume);
        } else {
            temp = new AlertEventRule(UUID.randomUUID().toString(),
                    AlertEventRule.TYPE_FOLLOW, null, true, 80);
        }

        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.sheet_alert_event, null, false);
        sheet.setContentView(content);

        TextView title = content.findViewById(R.id.tv_sheet_title);
        title.setText(existing != null ? "Edit Event" : "Add New Event");

        ChipGroup chips = content.findViewById(R.id.chips_type);
        final View llGiftSelector = content.findViewById(R.id.ll_gift_selector);
        MaterialButton btnSelectGift = content.findViewById(R.id.btn_select_gift);
        boolean specific = AlertEventRule.TYPE_GIFT_SPECIFIC.equals(temp.type);
        if (AlertEventRule.TYPE_FOLLOW.equals(temp.type)) chips.check(R.id.chip_follow);
        else if (AlertEventRule.TYPE_SHARE.equals(temp.type)) chips.check(R.id.chip_share);
        else if (specific) chips.check(R.id.chip_gift_name);
        else chips.check(R.id.chip_gift);
        if (temp.giftName != null) {
            btnSelectGift.setText(temp.giftName);
        }
        updateGiftSelectorVisibility(chips.getCheckedChipId(), llGiftSelector,
                temp.giftName, btnSelectGift);

        chips.setOnCheckedStateChangeListener((group, checkedIds) ->
                updateGiftSelectorVisibility(group.getCheckedChipId(), llGiftSelector,
                        temp.giftName, btnSelectGift));

        btnSelectGift.setOnClickListener(v -> showGiftPicker(temp, btnSelectGift));

        MaterialButton btnSelectSound = content.findViewById(R.id.btn_select_sound);
        btnSelectSound.setText(soundLabel(temp.sound));
        btnSelectSound.setOnClickListener(v -> showSoundPicker(temp, btnSelectSound));

        SwitchCompat swEnabled = content.findViewById(R.id.sw_enabled);
        swEnabled.setChecked(temp.enabled);

        SeekBar seek = content.findViewById(R.id.seek_volume);
        TextView tvVolume = content.findViewById(R.id.tv_volume_value);
        seek.setProgress(temp.volume);
        tvVolume.setText(temp.volume + "%");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvVolume.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        content.findViewById(R.id.btn_preview).setOnClickListener(v -> {
            if (!temp.hasSound()) {
                Toast.makeText(requireContext(), "Pilih suara dulu", Toast.LENGTH_SHORT).show();
                return;
            }
            previewSound(temp.sound, seek.getProgress());
        });

        content.findViewById(R.id.btn_save).setOnClickListener(v -> {
            int checkedId = chips.getCheckedChipId();
            String type;
            if (checkedId == R.id.chip_follow) type = AlertEventRule.TYPE_FOLLOW;
            else if (checkedId == R.id.chip_share) type = AlertEventRule.TYPE_SHARE;
            else if (checkedId == R.id.chip_gift_name) type = AlertEventRule.TYPE_GIFT_SPECIFIC;
            else type = AlertEventRule.TYPE_GIFT;

            if (AlertEventRule.TYPE_GIFT_SPECIFIC.equals(type)
                    && (temp.giftName == null || temp.giftName.isEmpty())) {
                Toast.makeText(requireContext(), "Pilih gift tertentu dulu", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!temp.hasSound() || !isValidSound(temp.sound)) {
                Toast.makeText(requireContext(), "Pilih suara untuk event ini", Toast.LENGTH_SHORT).show();
                return;
            }

            // One rule per event target: replace an existing rule with the same
            // target (any-gift replaces any-gift; a gift-name replaces the same
            // gift name).
            final boolean specificNow = AlertEventRule.TYPE_GIFT_SPECIFIC.equals(type);
            final String giftTarget = specificNow ? normalizeGift(temp.giftName) : null;
            List<AlertEventRule> list = new ArrayList<>(settingsManager.getAlertQueueRules());
            list.removeIf(r -> {
                if (specificNow) {
                    return AlertEventRule.TYPE_GIFT_SPECIFIC.equals(r.type)
                            && normalizeGift(r.giftName).equals(giftTarget);
                }
                return type.equals(r.type);
            });
            temp.type = type;
            if (!specificNow) {
                temp.giftName = null;
            }
            temp.enabled = swEnabled.isChecked();
            temp.volume = seek.getProgress();
            list.add(temp);
            settingsManager.setAlertQueueRules(list);
            sheet.dismiss();
            reloadSettings();
        });

        sheet.show();
    }

    private void updateGiftSelectorVisibility(int checkedChipId, View llGiftSelector,
                                              String giftName, MaterialButton btn) {
        if (checkedChipId == R.id.chip_gift_name) {
            llGiftSelector.setVisibility(View.VISIBLE);
            if (giftName != null && !giftName.isEmpty()) {
                btn.setText(giftName);
            } else {
                btn.setText("Pilih gift...");
            }
        } else {
            llGiftSelector.setVisibility(View.GONE);
        }
    }

    private static String normalizeGift(String name) {
        return name == null ? "" : name.replaceAll("\\s+", " ").trim()
                .toLowerCase(Locale.ROOT);
    }

    private void showGiftPicker(final AlertEventRule temp, final MaterialButton btn) {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.sheet_gift_picker, null, false);
        sheet.setContentView(content);

        RecyclerView rv = content.findViewById(R.id.rv_gifts);
        TextView tvStatus = content.findViewById(R.id.tv_gift_status);
        GridLayoutManager grid = new GridLayoutManager(requireContext(), 3);
        rv.setLayoutManager(grid);

        List<GiftInfo> gifts = new ArrayList<>();
        GiftAdapter adapter = new GiftAdapter(gifts, gift -> {
            temp.giftName = gift.name;
            btn.setText(gift.name);
            sheet.dismiss();
        });
        rv.setAdapter(adapter);

        GiftCatalogScraper.fetchGifts(new MyInstantsScraper.Callback<List<GiftInfo>>() {
            @Override
            public void onSuccess(List<GiftInfo> value) {
                if (!isAdded()) return;
                gifts.clear();
                gifts.addAll(value);
                adapter.notifyDataSetChanged();
                tvStatus.setText(gifts.isEmpty() ? "Daftar gift kosong"
                        : gifts.size() + " gift tersedia");
            }

            @Override
            public void onFailure(IOException error) {
                if (!isAdded()) return;
                tvStatus.setText("Gagal memuat gift: " + error.getMessage());
            }
        });

        sheet.show();
    }

    private boolean isValidSound(String soundKey) {
        if (builtinLabels.containsKey(soundKey)) return true;
        return soundStore != null && soundStore.getCustomSounds().containsKey(soundKey);
    }

    private String soundLabel(String soundKey) {
        if (soundKey == null || soundKey.isEmpty()) return "Pilih suara...";
        String display = builtinLabels.get(soundKey);
        if (display != null) return display + " (Library)";
        if (soundStore != null && soundStore.getCustomSounds().containsKey(soundKey)) {
            return soundKey + " (Custom)";
        }
        return soundKey;
    }

    // ---- sound picker sheet ----

    private void showSoundPicker(AlertEventRule temp, MaterialButton btnSelectSound) {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.sheet_sound_picker, null, false);
        sheet.setContentView(content);

        LinearLayout llCustom = content.findViewById(R.id.ll_custom);
        LinearLayout wrapper = content.findViewById(R.id.ll_custom_wrapper);

        PickerHost host = new PickerHost(sheet, llCustom, temp, btnSelectSound);
        activePickerHost = host;
        rebuildCustomRows(host);

        content.findViewById(R.id.btn_library).setOnClickListener(v ->
                libraryLauncher.launch(
                        new Intent(requireContext(), SoundLibraryActivity.class)));

        content.findViewById(R.id.btn_custom).setOnClickListener(v -> {
            boolean show = wrapper.getVisibility() == View.GONE;
            wrapper.setVisibility(show ? View.VISIBLE : View.GONE);
        });

        content.findViewById(R.id.btn_add_custom).setOnClickListener(v ->
                openAudioPicker());

        sheet.show();
    }

    private void rebuildCustomRows(PickerHost host) {
        host.llCustom.removeAllViews();
        if (soundStore == null) return;
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, String> e : soundStore.getCustomSounds().entrySet()) {
            if (!seen.add(e.getKey())) continue;
            host.llCustom.addView(inflateSoundRow(e.getKey(), e.getKey(), "Custom Sound",
                    () -> selectSound(host.temp, e.getKey(), host.btnSelectSound, host.sheet),
                    () -> removeCustomSound(e.getKey())));
        }
    }

    private View inflateSoundRow(String key, String label, String tag,
                                 Runnable onSelect, Runnable onDelete) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_sound, null, false);
        TextView avatar = row.findViewById(R.id.tv_sound_avatar);
        avatar.setText(label != null && !label.isEmpty()
                ? label.substring(0, 1).toUpperCase(Locale.ROOT) : "?");
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(ContextCompat.getColor(requireContext(),
                "Custom Sound".equals(tag) ? R.color.share_color : R.color.primary));
        avatar.setBackground(bg);

        ((TextView) row.findViewById(R.id.tv_sound_name)).setText(label);
        ((TextView) row.findViewById(R.id.tv_sound_tag)).setText(tag);

        ImageView ivDelete = row.findViewById(R.id.iv_sound_delete);
        if (onDelete != null) {
            ivDelete.setVisibility(View.VISIBLE);
            ivDelete.setOnClickListener(v -> onDelete.run());
        } else {
            ivDelete.setVisibility(View.GONE);
        }

        row.setOnClickListener(v -> onSelect.run());
        row.findViewById(R.id.iv_sound_play).setOnClickListener(v -> previewSound(key, 80));
        return row;
    }

    private void selectSound(AlertEventRule temp, String soundKey, MaterialButton btn,
                             BottomSheetDialog sheet) {
        temp.sound = soundKey;
        btn.setText(soundLabel(soundKey));
        sheet.dismiss();
    }

    // ---- preview ----

    private void previewSound(String soundKey, int volume) {
        if (previewManager == null || soundKey == null) return;
        previewManager.playSound(soundKey, Math.max(0, Math.min(100, volume)));
    }

    // ---- delete ----

    private void confirmDelete(AlertEventRule rule) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Hapus event?")
                .setMessage("Hapus alert untuk " + AlertEventRule.displayType(rule.type) + "?")
                .setPositiveButton("Hapus", (d, w) -> {
                    List<AlertEventRule> list = new ArrayList<>(settingsManager.getAlertQueueRules());
                    list.removeIf(r -> r.id.equals(rule.id));
                    settingsManager.setAlertQueueRules(list);
                    reloadSettings();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // ---- file manager (SAF) flow ----

    private void openAudioPicker() {
        try {
            openAudioLauncher.launch(new String[]{
                    "audio/mpeg", "audio/wav", "audio/x-wav",
                    "audio/x-m4a", "audio/mp4", "audio/aac",
                    "audio/ogg", "audio/flac", "audio/amr", "audio/3gpp",
                    "application/octet-stream", "application/ogg"});
        } catch (Exception e) {
            Toast.makeText(requireContext(), "File manager tidak dapat dibuka",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void onAudioPicked(Uri uri) {
        if (uri == null) return;
        promptCustomSoundName(fileNameOf(uri), uri);
    }

    private String fileNameOf(Uri uri) {
        String segment = uri.getLastPathSegment();
        if (segment == null || segment.isEmpty()) return "custom_sound";
        int slash = segment.lastIndexOf('/');
        if (slash >= 0) segment = segment.substring(slash + 1);
        int dot = segment.lastIndexOf('.');
        return dot > 0 ? segment.substring(0, dot) : segment;
    }

    private void promptCustomSoundName(String suggested, final Uri uri) {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setText(suggested);

        new AlertDialog.Builder(requireContext())
                .setTitle("Nama suara kustom")
                .setMessage("Nama ini dipakai untuk mengidentifikasi suara")
                .setView(input)
                .setPositiveButton("Simpan", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    copyCustomSoundFromUri(name, uri);
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void copyCustomSoundFromUri(final String name, final Uri uri) {
        try {
            File customDir = new File(requireContext().getFilesDir(), "custom_sounds");
            if (!customDir.exists() && !customDir.mkdirs()) {
                throw new java.io.IOException("Gagal membuat folder suara");
            }

            // Re-using a name overwrites the previous copy so the list never
            // accumulates duplicates.
            File target = new File(customDir, name + "." + extensionFor(name, uri));
            try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(target)) {
                if (is == null) {
                    throw new java.io.IOException("File tidak dapat dibaca");
                }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }

            soundStore.setCustomSound(name, target.getAbsolutePath());
            if (previewManager != null) {
                previewManager.loadCustomSoundFromPath(name, target.getAbsolutePath());
            }
            Toast.makeText(requireContext(), "Suara \"" + name + "\" ditambahkan",
                    Toast.LENGTH_SHORT).show();

            PickerHost host = activePickerHost;
            onCustomRowReady(host);
            selectCustomSound(host, name);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Gagal menyimpan suara: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private String extensionFor(String name, Uri uri) {
        String ext = "m4a";
        try {
            String mime = requireContext().getContentResolver().getType(uri);
            if (mime != null) {
                if (mime.startsWith("audio/mpeg")) ext = "mp3";
                else if (mime.contains("wav")) ext = "wav";
                else if (mime.contains("x-m4a") || mime.startsWith("audio/mp4")) ext = "m4a";
                else if (mime.contains("aac")) ext = "aac";
                else if (mime.contains("ogg") || mime.contains("opus")) ext = "ogg";
                else if (mime.contains("flac")) ext = "flac";
                else if (mime.contains("amr")) ext = "amr";
                else if (mime.contains("3gpp")) ext = "3gp";
                else if (mime.contains("octet-stream")) ext = "mp3";
            }
        } catch (Exception ignored) {
        }
        String segment = uri.getLastPathSegment();
        if (segment != null) {
            int dot = segment.lastIndexOf('.');
            String s = dot > 0 ? segment.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
            if (!s.isEmpty() && s.length() <= 5 && s.matches("[a-z0-9]+")) {
                ext = s;
            }
        }
        int dot2 = name.lastIndexOf('.');
        return dot2 > 0 ? name.substring(dot2 + 1) : ext;
    }

    private void onCustomRowReady(PickerHost host) {
        if (host == null) {
            return;
        }
        rebuildCustomRows(host);
    }

    private void selectCustomSound(PickerHost host, String name) {
        if (host == null || name == null) {
            return;
        }
        selectSound(host.temp, name, host.btnSelectSound, host.sheet);
    }

    private void removeCustomSound(final String name) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Hapus suara kustom?")
                .setMessage("Hapus \"" + name + "\" dari daftar suara kustom?")
                .setPositiveButton("Hapus", (d, w) -> {
                    String storedPath = soundStore.getCustomSounds().get(name);
                    soundStore.removeCustomSound(name);
                    try {
                        File f = storedPath == null ? null : new File(storedPath);
                        String base = requireContext().getFilesDir().getAbsolutePath();
                        if (f != null && f.getAbsolutePath().startsWith(base)
                                && f.exists() && !f.delete()) {
                            f.deleteOnExit();
                        }
                    } catch (Exception ignored) {
                    }
                    PickerHost host = activePickerHost;
                    if (host != null && name.equals(host.temp.sound)) {
                        host.temp.sound = null;
                        host.btnSelectSound.setText(soundLabel(null));
                    }
                    onCustomRowReady(host);
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // ---- helper ----

    private static final class PickerHost {
        final BottomSheetDialog sheet;
        final LinearLayout llCustom;
        final AlertEventRule temp;
        final MaterialButton btnSelectSound;

        PickerHost(BottomSheetDialog sheet, LinearLayout llCustom,
                   AlertEventRule temp, MaterialButton btnSelectSound) {
            this.sheet = sheet;
            this.llCustom = llCustom;
            this.temp = temp;
            this.btnSelectSound = btnSelectSound;
        }
    }

    // ---- gift catalog adapter ----

    interface GiftSelectionListener {
        void onGiftSelected(GiftInfo gift);
    }

    private static class GiftAdapter extends RecyclerView.Adapter<GiftAdapter.VH> {
        private final List<GiftInfo> items;
        private final GiftSelectionListener listener;

        GiftAdapter(List<GiftInfo> items, GiftSelectionListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_gift, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            final GiftInfo gift = items.get(position);
            holder.tvName.setText(gift.name);
            holder.tvPrice.setText(gift.price > 0 ? gift.price + " coin(s)" : "coin");
            Glide.with(holder.itemView.getContext())
                    .load(gift.imageUrl)
                    .placeholder(holder.itemView.getContext().getDrawable(R.drawable.ic_alert))
                    .into(holder.ivIcon);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onGiftSelected(gift);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final ImageView ivIcon;
            final TextView tvName;
            final TextView tvPrice;

            VH(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.iv_gift_icon);
                tvName = itemView.findViewById(R.id.tv_gift_name);
                tvPrice = itemView.findViewById(R.id.tv_gift_price);
            }
        }
    }

    // ---- adapter ----

    private static class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.VH> {

        interface Callbacks {
            void onEdit(AlertEventRule rule);

            void onToggle(AlertEventRule rule, boolean enabled);

            void onPreview(AlertEventRule rule);

            void onDelete(AlertEventRule rule);
        }

        private final Callbacks callbacks;
        private final List<AlertEventRule> items = new ArrayList<>();

        AlertAdapter(List<AlertEventRule> items, Callbacks callbacks) {
            this.items.addAll(items);
            this.callbacks = callbacks;
        }

        void submit(List<AlertEventRule> items) {
            this.items.clear();
            this.items.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_alert_event, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            final AlertEventRule rule = items.get(position);
            Context ctx = holder.itemView.getContext();
            holder.tvAvatar.setText(initialFor(rule));
            holder.tvAvatar.setBackground(oval(colorFor(rule, ctx)));
            if (AlertEventRule.TYPE_GIFT_SPECIFIC.equals(rule.type)) {
                holder.tvTitle.setText(rule.giftName == null || rule.giftName.isEmpty()
                        ? "Gift Name" : rule.giftName);
            } else {
                holder.tvTitle.setText(AlertEventRule.displayType(rule.type));
            }
            holder.tvSubtitle.setText(ctx.getString(R.string.alert_row_subtitle,
                    soundLabel(rule.sound), rule.volume));
            holder.swEnabled.setChecked(rule.enabled);
            holder.swEnabled.setOnCheckedChangeListener(null);
            holder.swEnabled.setOnCheckedChangeListener((buttonView, isChecked) ->
                    callbacks.onToggle(rule, isChecked));

            holder.itemView.setOnClickListener(v -> callbacks.onEdit(rule));
            holder.ivPreview.setOnClickListener(v -> callbacks.onPreview(rule));
            holder.ivDelete.setOnClickListener(v -> callbacks.onDelete(rule));
        }

        private static String soundLabel(String soundKey) {
            return soundKey == null || soundKey.isEmpty() ? "Belum pilih suara" : soundKey;
        }

        private static String initialFor(AlertEventRule rule) {
            String type = rule.type;
            if (AlertEventRule.TYPE_SHARE.equals(type)) return "S";
            if (AlertEventRule.TYPE_GIFT.equals(type)) return "G";
            if (AlertEventRule.TYPE_GIFT_SPECIFIC.equals(type)) {
                String gn = rule.giftName;
                if (gn != null && !gn.isEmpty()) {
                    return gn.substring(0, 1).toUpperCase(Locale.ROOT);
                }
                return "G";
            }
            return "F";
        }

        private static int colorFor(AlertEventRule rule, Context context) {
            String type = rule.type;
            if (AlertEventRule.TYPE_SHARE.equals(type)) {
                return ContextCompat.getColor(context, R.color.share_color);
            }
            if (AlertEventRule.TYPE_GIFT.equals(type)
                    || AlertEventRule.TYPE_GIFT_SPECIFIC.equals(type)) {
                return ContextCompat.getColor(context, R.color.primary);
            }
            return ContextCompat.getColor(context, R.color.follow_color);
        }

        private static GradientDrawable oval(int color) {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            return bg;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvAvatar;
            final TextView tvTitle;
            final TextView tvSubtitle;
            final SwitchCompat swEnabled;
            final ImageView ivPreview;
            final ImageView ivDelete;

            VH(@NonNull View itemView) {
                super(itemView);
                tvAvatar = itemView.findViewById(R.id.tv_avatar);
                tvTitle = itemView.findViewById(R.id.tv_title);
                tvSubtitle = itemView.findViewById(R.id.tv_subtitle);
                swEnabled = itemView.findViewById(R.id.sw_enabled);
                ivPreview = itemView.findViewById(R.id.iv_preview);
                ivDelete = itemView.findViewById(R.id.iv_delete);
            }
        }
    }
}
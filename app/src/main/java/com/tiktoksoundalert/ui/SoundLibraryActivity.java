package com.tiktoksoundalert.ui;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.tiktoksoundalert.GiftSoundStore;
import com.tiktoksoundalert.R;
import com.tiktoksoundalert.models.MyInstantSound;
import com.tiktoksoundalert.scraper.MyInstantsScraper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Request;
import okhttp3.Response;

/**
 * Browsable MyInstants sound library.
 *
 * <p>Data comes from the myinstants-api JSON project (trending/best for the
 * default Indonesian library, search for keywords). Tapping a row streams a
 * preview; "Pilih" downloads the mp3 into app storage, registers it as a
 * custom sound and returns it to the caller. Downloads try the direct
 * myinstants URL first and fall back to the Internet Archive copy when the
 * direct request is blocked.
 */
public class SoundLibraryActivity extends AppCompatActivity {

    public static final String EXTRA_SOUND_NAME = "extra_sound_name";
    public static final String EXTRA_SOUND_PATH = "extra_sound_path";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private EditText etSearch;
    private MaterialButton btnSearch;
    private RecyclerView rvResults;
    private ProgressBar pbLoading;
    private TextView tvStatus;
    private MaterialButton btnRetry;

    private final List<MyInstantSound> items = new ArrayList<>();
    private LibAdapter adapter;

    private GiftSoundStore soundStore;

    private String currentQuery = "";
    private boolean loading = false;

    private MediaPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sound_library);
        setTitle("Sound Library (MyInstants)");

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        soundStore = new GiftSoundStore(this);

        etSearch = findViewById(R.id.et_search);
        btnSearch = findViewById(R.id.btn_search);
        rvResults = findViewById(R.id.rv_results);
        pbLoading = findViewById(R.id.pb_loading);
        tvStatus = findViewById(R.id.tv_status);
        btnRetry = findViewById(R.id.btn_retry);

        rvResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LibAdapter(items, new LibAdapter.Callbacks() {
            @Override
            public void onSelect(MyInstantSound sound) {
                selectMyInstant(sound);
            }

            @Override
            public void onPreview(MyInstantSound sound) {
                preview(sound);
            }
        });
        rvResults.setAdapter(adapter);

        btnRetry.setOnClickListener(v -> loadApi(currentQuery));
        btnSearch.setOnClickListener(v -> doSearch());
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                doSearch();
                return true;
            }
            return false;
        });

        loadApi("");
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void doSearch() {
        String q = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
        loadApi(q);
    }

    private void loadApi(final String query) {
        if (loading) return;
        loading = true;
        currentQuery = query;
        btnRetry.setVisibility(View.GONE);
        tvStatus.setVisibility(View.GONE);
        setBusy(true);

        MyInstantsScraper.Callback<List<MyInstantSound>> callback =
                new MyInstantsScraper.Callback<List<MyInstantSound>>() {
                    @Override
                    public void onSuccess(List<MyInstantSound> sounds) {
                        loading = false;
                        setBusy(false);
                        items.clear();
                        for (MyInstantSound s : sounds) {
                            if (!containsPath(s.soundPath)) items.add(s);
                        }
                        adapter.notifyDataSetChanged();
                        if (items.isEmpty()) {
                            tvStatus.setText(query.isEmpty()
                                    ? "Tidak ada suara ditemukan."
                                    : "Suara \"" + query + "\" tidak ditemukan.");
                            tvStatus.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onFailure(IOException error) {
                        loading = false;
                        setBusy(false);
                        tvStatus.setText("Gagal memuat: " + error.getMessage());
                        tvStatus.setVisibility(View.VISIBLE);
                        btnRetry.setVisibility(View.VISIBLE);
                    }
                };

        if (query.isEmpty()) {
            MyInstantsScraper.fetchDefaultLibrary(callback);
        } else {
            MyInstantsScraper.fetchSearch(query, callback);
        }
    }

    private boolean containsPath(String path) {
        for (MyInstantSound s : items) {
            if (s.soundPath.equals(path)) return true;
        }
        return false;
    }

    private void setBusy(boolean busy) {
        pbLoading.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    // ---- preview ----

    private void preview(final MyInstantSound sound) {
        Toast.makeText(this, "Memuat preview...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                File f = download(sound, "preview_" + slugOf(sound.soundPath));
                main(() -> playFile(f));
            } catch (final IOException e) {
                main(() -> Toast.makeText(SoundLibraryActivity.this,
                        "Preview tidak tersedia: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void playFile(File file) {
        releasePlayer();
        try {
            MediaPlayer mp = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
            }
            mp.setDataSource(file.getAbsolutePath());
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setOnErrorListener((m, what, extra) -> {
                releasePlayer();
                Toast.makeText(this, "Gagal memutar file audio", Toast.LENGTH_LONG).show();
                return true;
            });
            player = mp;
            mp.prepareAsync();
        } catch (Exception e) {
            releasePlayer();
            Toast.makeText(this, "Gagal memutar preview: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    // ---- select: download, register as custom sound, return ----

    private void selectMyInstant(final MyInstantSound sound) {
        Toast.makeText(this, "Mengunduh suara...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                File target = download(sound, slugOf(sound.soundPath));
                // Re-using an existing name overwrites it, so the custom sound
                // list never accumulates "name" + "name (2)" duplicates.
                final String name = sanitizeName(sound.name);
                soundStore.setCustomSound(name, target.getAbsolutePath());
                main(() -> {
                    Toast.makeText(this, "Suara \"" + name + "\" ditambahkan",
                            Toast.LENGTH_LONG).show();
                    finishWith(name, target.getAbsolutePath());
                });
            } catch (final IOException e) {
                main(() -> Toast.makeText(this, "Gagal mengunduh: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    /**
     * Downloads mp3 bytes for a sound into the myinstants dir, reusing an
     * existing file where possible. Tries the direct myinstants URL first and
     * falls back to the Internet Archive copy if it is blocked.
     */
    private File download(MyInstantSound sound, String fileName) throws IOException {
        File dir = new File(getFilesDir(), "myinstants");
        if (!dir.exists()) dir.mkdirs();
        File target = new File(dir, fileName);
        if (target.exists() && target.length() > 0) return target;

        String url = sound.soundPath;
        IOException directError = null;
        try {
            downloadTo(url, target);
        } catch (IOException e) {
            directError = e;
        }
        if (target.exists() && target.length() > 0) return target;

        try {
            downloadTo(MyInstantsScraper.archivedMediaUrl(url), target);
        } catch (IOException archiveError) {
            if (directError != null) throw directError;
            throw archiveError;
        }
        if (!target.exists() || target.length() == 0) {
            throw new IOException("File kosong");
        }
        return target;
    }

    private void downloadTo(String url, File target) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", MyInstantsScraper.USER_AGENT)
                .header("Accept", "audio/mpeg,*/*")
                .build();
        try (Response response = MyInstantsScraper.CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            if (!MyInstantsScraper.isAudio(response)) {
                throw new IOException("Diblokir (HTTP " + response.code() + ")");
            }
            byte[] bytes = response.body().bytes();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(target);
            try {
                fos.write(bytes);
            } finally {
                fos.close();
            }
        }
    }

    private String slugOf(String mp3Url) {
        String slug = mp3Url.substring(mp3Url.lastIndexOf('/') + 1);
        if (slug.isEmpty()) slug = "sound.mp3";
        return slug;
    }

    private String sanitizeName(String raw) {
        String name = raw == null ? "" : raw.replaceAll("[\\r\\n]", " ").trim();
        if (name.isEmpty()) name = "Suara MyInstants";
        return name;
    }

    private void finishWith(String name, String path) {
        android.content.Intent data = new android.content.Intent();
        data.putExtra(EXTRA_SOUND_NAME, name);
        data.putExtra(EXTRA_SOUND_PATH, path);
        setResult(RESULT_OK, data);
        finish();
    }

    private void main(Runnable r) {
        runOnUiThread(r);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
        executor.shutdownNow();
    }

    // ---- adapter ----

    private static class LibAdapter extends RecyclerView.Adapter<LibAdapter.VH> {

        interface Callbacks {
            void onSelect(MyInstantSound sound);

            void onPreview(MyInstantSound sound);
        }

        private final List<MyInstantSound> items;
        private final Callbacks callbacks;

        LibAdapter(List<MyInstantSound> items, Callbacks callbacks) {
            this.items = items;
            this.callbacks = callbacks;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_library_sound, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            final MyInstantSound sound = items.get(position);
            String label = sound.name;
            holder.tvName.setText(label);
            holder.tvTag.setText("MyInstants");
            holder.tvAvatar.setText(label.isEmpty() ? "M"
                    : label.substring(0, 1).toUpperCase(Locale.ROOT));
            holder.itemView.setOnClickListener(v -> callbacks.onSelect(sound));
            holder.btnSelect.setOnClickListener(v -> callbacks.onSelect(sound));
            holder.ivPlay.setOnClickListener(v -> callbacks.onPreview(sound));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvAvatar;
            final TextView tvName;
            final TextView tvTag;
            final MaterialButton btnSelect;
            final ImageView ivPlay;

            VH(@NonNull View itemView) {
                super(itemView);
                tvAvatar = itemView.findViewById(R.id.tv_lib_avatar);
                tvName = itemView.findViewById(R.id.tv_lib_name);
                tvTag = itemView.findViewById(R.id.tv_lib_tag);
                btnSelect = itemView.findViewById(R.id.btn_lib_select);
                ivPlay = itemView.findViewById(R.id.iv_lib_play);
            }
        }
    }
}
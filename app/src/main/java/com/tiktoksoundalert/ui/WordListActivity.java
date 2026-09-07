package com.tiktoksoundalert.ui;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tiktoksoundalert.R;
import com.tiktoksoundalert.SettingsManager;
import com.tiktoksoundalert.SettingsRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WordListActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_BANNED = "banned";
    public static final String MODE_PRIORITY = "priority";
    public static final String MODE_FAVORITES = "favorites";

    private String mode;
    private SettingsManager settingsManager;
    private final List<String> items = new ArrayList<>();
    private WordAdapter adapter;
    private EditText etNewItem;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_word_list);

        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = MODE_BANNED;

        switch (mode) {
            case MODE_PRIORITY:
                setTitle("Kata Prioritas");
                break;
            case MODE_FAVORITES:
                setTitle("User Favorit");
                break;
            default:
                setTitle("Kata Terlarang");
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        etNewItem = findViewById(R.id.et_new_item);
        tvEmpty = findViewById(R.id.tv_empty);
        ListView listView = findViewById(R.id.list_items);
        adapter = new WordAdapter();
        listView.setAdapter(adapter);

        findViewById(R.id.btn_add).setOnClickListener(v -> addItem());

        long accountId = SettingsRepository.activeAccountId(this);
        settingsManager = new SettingsManager(this, accountId);
        reload();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void reload() {
        items.clear();
        items.addAll(getItems());
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private Set<String> getItems() {
        switch (mode) {
            case MODE_PRIORITY:
                return settingsManager.getPriorityWords();
            case MODE_FAVORITES:
                return settingsManager.getFavoriteUsers();
            default:
                return settingsManager.getBlockedWords();
        }
    }

    private void setItems(Set<String> values) {
        switch (mode) {
            case MODE_PRIORITY:
                settingsManager.setPriorityWords(values);
                break;
            case MODE_FAVORITES:
                settingsManager.setFavoriteUsers(values);
                break;
            default:
                settingsManager.setBlockedWords(values);
        }
    }

    private void addItem() {
        String value = etNewItem.getText() == null
                ? "" : etNewItem.getText().toString().trim();
        if (value.isEmpty()) return;

        Set<String> set = new LinkedHashSet<>(getItems());
        set.add(value.toLowerCase());
        setItems(set);
        etNewItem.setText("");
        reload();
    }

    private void removeItem(String value) {
        Set<String> set = new LinkedHashSet<>(getItems());
        set.remove(value);
        setItems(set);
        reload();
    }

    private class WordAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater()
                        .inflate(R.layout.item_word, parent, false);
            }
            final String value = items.get(position);
            TextView tv = convertView.findViewById(R.id.tv_item);
            tv.setText(value);
            convertView.findViewById(R.id.btn_delete).setOnClickListener(v ->
                    removeItem(value));
            return convertView;
        }
    }
}
package com.tiktoksoundalert.ui;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

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
    public static final String MODE_TRIGGERS = "triggers";

    private String mode;
    private SettingsManager settingsManager;
    private final List<String> items = new ArrayList<>();
    private WordAdapter adapter;
    private EditText etNewItem;
    private TextView tvEmpty;
    private View rowMatchMode;
    private Spinner spinnerMatchMode;
    private View rowTriggerSwitches;
    private TextView tvMatchGuide;
    private boolean settingMode = false;

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
            case MODE_TRIGGERS:
                setTitle("Custom");
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

        rowMatchMode = findViewById(R.id.row_match_mode);
        spinnerMatchMode = findViewById(R.id.spinner_match_mode);
        rowTriggerSwitches = findViewById(R.id.row_trigger_switches);
        tvMatchGuide = findViewById(R.id.tv_match_guide);
        if (MODE_TRIGGERS.equals(mode)) {
            rowMatchMode.setVisibility(View.VISIBLE);
            rowTriggerSwitches.setVisibility(View.VISIBLE);
            tvMatchGuide.setText(getString(R.string.trigger_match_guide));
            setupMatchModeSpinner();
            setupTriggerSwitches();
            findViewById(R.id.btn_full_guide).setOnClickListener(v -> showGuide());
        }

        reload();
    }

    private void showGuide() {
        TextView body = new TextView(this);
        body.setPadding(dp(16), dp(8), dp(16), dp(8));
        body.setTextSize(14f);
        body.setText(getString(R.string.trigger_full_guide));
        body.setMovementMethod(new ScrollingMovementMethod());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);

        new AlertDialog.Builder(this)
                .setTitle(R.string.trigger_guide_title)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void setupTriggerSwitches() {
        SwitchCompat swEnabled = findViewById(R.id.sw_trigger_enabled);

        swEnabled.setChecked(settingsManager.isTriggerEnabled());

        swEnabled.setOnCheckedChangeListener((buttonView, isChecked) ->
                settingsManager.setTriggerEnabled(isChecked));
    }

    private void setupMatchModeSpinner() {
        String[] labels = getResources().getStringArray(R.array.trigger_match_labels);
        final String[] values = getResources().getStringArray(R.array.trigger_match_values);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMatchMode.setAdapter(adapter);

        String current = settingsManager.getTriggerMatchMode();
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                index = i;
                break;
            }
        }
        settingMode = true;
        spinnerMatchMode.setSelection(index);
        settingMode = false;

        spinnerMatchMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (settingMode) return;
                settingsManager.setTriggerMatchMode(values[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
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
            case MODE_TRIGGERS:
                return settingsManager.getTriggerWords();
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
            case MODE_TRIGGERS:
                settingsManager.setTriggerWords(values);
                break;
            default:
                settingsManager.setBlockedWords(values);
        }
    }

    private void addItem() {
        String value = etNewItem.getText() == null
                ? "" : etNewItem.getText().toString().trim();
        if (value.isEmpty()) return;

        boolean regexMode = MODE_TRIGGERS.equals(mode)
                && SettingsManager.TRIGGER_MODE_REGEX.equals(settingsManager.getTriggerMatchMode());

        Set<String> set = new LinkedHashSet<>(getItems());
        set.add(regexMode ? value : value.toLowerCase());
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
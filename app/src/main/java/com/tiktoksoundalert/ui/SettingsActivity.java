package com.tiktoksoundalert.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.tiktoksoundalert.DbPreferenceDataStore;
import com.tiktoksoundalert.R;
import com.tiktoksoundalert.SettingsRepository;
import com.tiktoksoundalert.db.Account;
import com.tiktoksoundalert.db.AppDatabase;

public class SettingsActivity extends AppCompatActivity {

    public static final String EXTRA_SECTION = "section";
    public static final String KEY_GENERAL = "general";
    public static final String KEY_GIFT = "gift";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String section = getIntent().getStringExtra(EXTRA_SECTION);
        if (section == null) section = KEY_GENERAL;

        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, createFragment(section))
                .commit();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            switch (section) {
                case KEY_GIFT:
                    getSupportActionBar().setTitle("Alert Sound Queue");
                    break;
                default:
                    getSupportActionBar().setTitle("Settings");
                    break;
            }
        }
    }

    private Fragment createFragment(String section) {
        switch (section) {
            case KEY_GIFT:
                return new AlertQueueFragment();
            default:
                return new GeneralSettingsFragment();
        }
    }

    static void useAccountDataStore(PreferenceFragmentCompat fragment) {
        long accountId = SettingsRepository.activeAccountId(fragment.requireContext());
        fragment.getPreferenceManager().setPreferenceDataStore(
                new DbPreferenceDataStore(fragment.requireContext(), accountId));
    }

    static void addAccountInfoRow(PreferenceScreen screen) {
        if (screen == null) return;
        long accountId = SettingsRepository.activeAccountId(screen.getContext());
        Account active = AppDatabase.get(screen.getContext()).accountDao().getActiveNow();
        String label = active != null ? "@" + active.nickname : "Guest (no account)";
        Preference info = new Preference(screen.getContext());
        info.setKey("account_info");
        info.setTitle("For account: " + label);
        info.setSummary("Settings are saved separately for each account");
        info.setSelectable(false);
        info.setOrder(0);
        screen.addPreference(info);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    public static class GeneralSettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            useAccountDataStore(this);
            setPreferencesFromResource(R.xml.preferences_general, rootKey);
            addAccountInfoRow(getPreferenceScreen());
        }
    }
}
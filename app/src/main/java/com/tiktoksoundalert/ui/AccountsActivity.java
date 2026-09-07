package com.tiktoksoundalert.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.tiktoksoundalert.R;
import com.tiktoksoundalert.db.Account;
import com.tiktoksoundalert.db.AccountDao;
import com.tiktoksoundalert.db.AppDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AccountsActivity extends AppCompatActivity {

    private AccountDao dao;
    private TextInputEditText etAccount;
    private TextView tvEmpty;
    private AccountAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accounts);

        dao = AppDatabase.get(this).accountDao();

        com.google.android.material.appbar.MaterialToolbar toolbar =
                findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        androidx.appcompat.app.ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        etAccount = findViewById(R.id.et_account);
        tvEmpty = findViewById(R.id.tv_empty);
        MaterialButton btnAdd = findViewById(R.id.btn_add);

        adapter = new AccountAdapter();
        adapter.setListener(new AccountAdapter.Listener() {
            @Override
            public void onUse(Account account) {
                useAccount(account);
            }

            @Override
            public void onDelete(Account account) {
                deleteAccount(account);
            }
        });
        RecyclerView recycler = findViewById(R.id.rv_accounts);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> addAccount());
        etAccount.setOnEditorActionListener((v, actionId, event) -> {
            addAccount();
            return true;
        });

        dao.observeAll().observe(this, list -> {
            if (list == null) return;
            adapter.setList(list);
            tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });

        dao.observeActive().observe(this, active -> {
            adapter.setActiveId(active != null ? active.id : -1);
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void addAccount() {
        String nickname = etAccount.getText() != null
                ? etAccount.getText().toString().trim() : "";
        if (nickname.isEmpty()) {
            Toast.makeText(this, "Enter a TikTok username", Toast.LENGTH_SHORT).show();
            return;
        }

        AppDatabase.runInBackground(() -> {
            if (dao.findByNickname(nickname) != null) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Account '" + nickname + "' already exists", Toast.LENGTH_SHORT).show());
                return;
            }

            long now = System.currentTimeMillis();
            Account account = new Account();
            account.nickname = nickname;
            account.createdAt = now;
            account.lastUsedAt = now;
            boolean isFirst = dao.getActiveNow() == null;
            long id = dao.insert(account);
            if (isFirst) {
                dao.setActive(id, now);
            }
        });

        etAccount.setText("");
    }

    private void showDeleteConfirm(Account account) {
        new AlertDialog.Builder(this)
                .setTitle("Delete account")
                .setMessage("Remove '" + account.nickname + "'?")
                .setPositiveButton("Delete", (d, w) -> {
                    AppDatabase.runInBackground(() -> dao.deleteById(account.id));
                    Toast.makeText(this, "Account deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    static class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.VH> {

        interface Listener {
            void onUse(Account account);

            void onDelete(Account account);
        }

        private final List<Account> items = new ArrayList<>();
        private long activeId = -1;
        private Listener listener;

        void setList(List<Account> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        void setActiveId(long id) {
            activeId = id;
            notifyDataSetChanged();
        }

        void setListener(Listener listener) {
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_account, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Account account = items.get(position);

            boolean active = account.id == activeId;
            holder.tvNickname.setText(account.nickname);
            holder.tvNickname.setTextColor(active
                    ? holder.itemView.getContext().getColor(R.color.primary)
                    : holder.itemView.getContext().getColor(R.color.text_primary));
            holder.tvStatus.setText(active
                    ? "Active"
                    : "Last used " + formatDate(account.lastUsedAt));

            holder.btnUse.setVisibility(active ? View.INVISIBLE : View.VISIBLE);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onUse(account);
            });
            holder.btnUse.setOnClickListener(v -> {
                if (listener != null) listener.onUse(account);
            });
            holder.btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDelete(account);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvNickname;
            final TextView tvStatus;
            final MaterialButton btnUse;
            final MaterialButton btnDelete;

            VH(@NonNull View itemView) {
                super(itemView);
                tvNickname = itemView.findViewById(R.id.tv_nickname);
                tvStatus = itemView.findViewById(R.id.tv_status);
                btnUse = itemView.findViewById(R.id.btn_use);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
    }

    private static String formatDate(long millis) {
        return new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    private void useAccount(Account account) {
        AppDatabase.runInBackground(() -> {
            long now = System.currentTimeMillis();
            dao.clearActive();
            dao.setActive(account.id, now);
        });
    }

    private void deleteAccount(Account account) {
        showDeleteConfirm(account);
    }
}
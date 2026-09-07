package com.tiktoksoundalert.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(tableName = "account_settings",
        primaryKeys = {"account_id", "key"},
        foreignKeys = @ForeignKey(entity = Account.class,
                parentColumns = "id",
                childColumns = "account_id",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("account_id")})
public class AccountSetting {

    @NonNull
    @ColumnInfo(name = "account_id")
    public long accountId;

    @NonNull
    @ColumnInfo(name = "key")
    public String key;

    @ColumnInfo(name = "value")
    public String value;

    public AccountSetting() {
    }

    public AccountSetting(long accountId, String key, String value) {
        this.accountId = accountId;
        this.key = key;
        this.value = value;
    }
}
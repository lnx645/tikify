package com.tiktoksoundalert.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "accounts", indices = {@Index(value = "nickname", unique = true)})
public class Account {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "nickname")
    public String nickname;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "last_used_at")
    public long lastUsedAt;

    @ColumnInfo(name = "is_active")
    public boolean isActive;
}
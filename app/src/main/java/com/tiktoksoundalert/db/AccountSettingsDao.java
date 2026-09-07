package com.tiktoksoundalert.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AccountSettingsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(AccountSetting setting);

    @Query("SELECT * FROM account_settings WHERE account_id = :accountId")
    List<AccountSetting> getAll(long accountId);

    @Query("SELECT * FROM account_settings WHERE account_id = :accountId AND key = :key LIMIT 1")
    AccountSetting get(long accountId, String key);

    @Query("DELETE FROM account_settings WHERE account_id = :accountId")
    void clearForAccount(long accountId);
}
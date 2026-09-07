package com.tiktoksoundalert.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY last_used_at DESC")
    LiveData<List<Account>> observeAll();

    @Query("SELECT * FROM accounts WHERE is_active = 1 LIMIT 1")
    LiveData<Account> observeActive();

    @Query("SELECT * FROM accounts WHERE is_active = 1 LIMIT 1")
    Account getActiveNow();

    @Query("SELECT * FROM accounts WHERE LOWER(nickname) = LOWER(:nickname) LIMIT 1")
    Account findByNickname(String nickname);

    @Insert
    long insert(Account account);

    @Query("UPDATE accounts SET is_active = 0")
    void clearActive();

    @Query("UPDATE accounts SET is_active = 1, last_used_at = :now WHERE id = :id")
    void setActive(long id, long now);

    @Query("UPDATE accounts SET avatar_url = :url WHERE id = :id")
    void updateAvatarUrl(long id, String url);

    @Query("DELETE FROM accounts WHERE id = :id")
    void deleteById(long id);
}
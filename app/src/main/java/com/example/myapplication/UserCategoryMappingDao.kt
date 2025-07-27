package com.example.myapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserCategoryMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: UserCategoryMapping)

    @Query("SELECT * FROM user_category_mappings WHERE :text LIKE '%' || pattern || '%' LIMIT 1")
    suspend fun getMappingForText(text: String): UserCategoryMapping?

    @Query("SELECT * FROM user_category_mappings")
    suspend fun getAllMappings(): List<UserCategoryMapping>
}
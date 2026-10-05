package com.owlcoders.chitti.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.owlcoders.chitti.db.entities.PersonalDocument
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalDocumentDao {
    @Query("SELECT * FROM personal_documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PersonalDocument>>

    @Query("SELECT * FROM personal_documents ORDER BY createdAt DESC")
    suspend fun getAll(): List<PersonalDocument>

    @Query("SELECT * FROM personal_documents WHERE id = :id")
    suspend fun getById(id: Long): PersonalDocument?

    @Insert
    suspend fun insert(document: PersonalDocument): Long

    @Delete
    suspend fun delete(document: PersonalDocument)

    @Query("DELETE FROM personal_documents")
    suspend fun deleteAll()
}

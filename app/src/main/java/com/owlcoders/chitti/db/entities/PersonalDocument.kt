package com.owlcoders.chitti.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A scanned or uploaded personal document. The file itself lives encrypted in the vault under
 * [fileName]; the fields read from it are stored here sealed ([fieldsSealed]), never in plain text.
 */
@Entity(tableName = "personal_documents")
data class PersonalDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val title: String,
    val mimeType: String,
    val fileName: String,
    val pageCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val fieldsSealed: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean = other is PersonalDocument && other.id == id && other.fileName == fileName
    override fun hashCode(): Int = (id xor (id ushr 32)).toInt() * 31 + fileName.hashCode()
}

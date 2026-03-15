package blackark.app.vr.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "jvr_performer_merge_rules",
    indices = [
        Index("targetPerformerId"),
        Index("isManual"),
    ],
)
data class JvrPerformerMergeRule(
    @PrimaryKey
    val sourcePerformerId: String,
    val targetPerformerId: String,
    val isManual: Boolean,
    val updatedAt: Long,
)

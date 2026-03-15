package blackark.app.vr.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "jvr_performer_aliases",
    foreignKeys = [
        ForeignKey(
            entity = JvrPerformer::class,
            parentColumns = ["performerId"],
            childColumns = ["canonicalPerformerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("canonicalPerformerId"),
        Index("aliasKind"),
        Index("source"),
    ],
)
data class JvrPerformerAlias(
    @PrimaryKey
    val aliasKey: String,
    val canonicalPerformerId: String,
    val aliasKind: String,
    val aliasValue: String,
    val source: String?,
    val updatedAt: Long,
)

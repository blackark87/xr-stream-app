package blackark.app.vr.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "jvr_performers",
    indices = [
        Index("englishName"),
        Index("japaneseName"),
    ],
)
data class JvrPerformer(
    @PrimaryKey
    val performerId: String,
    val englishName: String,
    val japaneseName: String?,
    val remoteProfileImageUrl: String?,
    val localProfileImageUrl: String?,
    val updatedAt: Long,
)

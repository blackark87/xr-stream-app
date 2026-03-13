package blackark.app.vr.data.database.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class VirtualGroupMetadataRecord(
    @Embedded
    val metadata: VirtualGroupMetadata,
    @Relation(
        parentColumn = "cacheKey",
        entityColumn = "cacheKey",
    )
    val genres: List<VirtualGroupMetadataGenre> = emptyList(),
    @Relation(
        parentColumn = "cacheKey",
        entityColumn = "cacheKey",
    )
    val performerRefs: List<VirtualGroupMetadataPerformerCrossRef> = emptyList(),
    @Relation(
        parentColumn = "cacheKey",
        entity = JvrPerformer::class,
        entityColumn = "performerId",
        associateBy = Junction(
            value = VirtualGroupMetadataPerformerCrossRef::class,
            parentColumn = "cacheKey",
            entityColumn = "performerId",
        ),
    )
    val performers: List<JvrPerformer> = emptyList(),
)

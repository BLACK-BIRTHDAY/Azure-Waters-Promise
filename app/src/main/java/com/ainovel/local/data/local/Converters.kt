package com.ainovel.local.data.local

import androidx.room.TypeConverter
import com.ainovel.local.data.model.CandidateGroupStatus
import com.ainovel.local.data.model.ContextNodeType
import com.ainovel.local.data.model.EntryScope
import com.ainovel.local.data.model.EntryType
import com.ainovel.local.data.model.ReasoningKind
import com.ainovel.local.data.model.ResponseStatus
import com.ainovel.local.data.model.RevisionType
import com.ainovel.local.data.model.SummaryDensity
import com.ainovel.local.data.model.SummaryStatus
import com.ainovel.local.data.model.TaskType

class Converters {
    @TypeConverter fun fromEntryType(value: EntryType) = value.name
    @TypeConverter fun toEntryType(value: String) = EntryType.valueOf(value)
    @TypeConverter fun fromEntryScope(value: EntryScope) = value.name
    @TypeConverter fun toEntryScope(value: String) = EntryScope.valueOf(value)
    @TypeConverter fun fromSummaryStatus(value: SummaryStatus) = value.name
    @TypeConverter fun toSummaryStatus(value: String) = SummaryStatus.valueOf(value)
    @TypeConverter fun fromSummaryDensity(value: SummaryDensity) = value.name
    @TypeConverter fun toSummaryDensity(value: String) = SummaryDensity.valueOf(value)
    @TypeConverter fun fromTaskType(value: TaskType) = value.name
    @TypeConverter fun toTaskType(value: String) = TaskType.valueOf(value)
    @TypeConverter fun fromCandidateGroupStatus(value: CandidateGroupStatus) = value.name
    @TypeConverter fun toCandidateGroupStatus(value: String) = CandidateGroupStatus.valueOf(value)
    @TypeConverter fun fromRevisionType(value: RevisionType) = value.name
    @TypeConverter fun toRevisionType(value: String) = RevisionType.valueOf(value)
    @TypeConverter fun fromContextNodeType(value: ContextNodeType) = value.name
    @TypeConverter fun toContextNodeType(value: String) = ContextNodeType.valueOf(value)
    @TypeConverter fun fromResponseStatus(value: ResponseStatus) = value.name
    @TypeConverter fun toResponseStatus(value: String) = ResponseStatus.valueOf(value)
    @TypeConverter fun fromReasoningKind(value: ReasoningKind) = value.name
    @TypeConverter fun toReasoningKind(value: String) = ReasoningKind.valueOf(value)
}

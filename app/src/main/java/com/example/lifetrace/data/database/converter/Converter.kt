package com.example.lifetrace.data.database.converter

import androidx.room.TypeConverter
import com.example.lifetrace.data.database.entity.TripStatus
import com.example.lifetrace.data.database.entity.AttachmentType

/**
 * Room 类型转换器
 * 用于 enum <-> String 转换
 */
class Converters {

    // ---------------- TripStatus ----------------

    @TypeConverter
    fun fromTripStatus(status: TripStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toTripStatus(value: String?): TripStatus? {
        return value?.let { TripStatus.valueOf(it) }
    }

    // ---------------- AttachmentType ----------------

    @TypeConverter
    fun fromAttachmentType(type: AttachmentType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toAttachmentType(value: String?): AttachmentType? {
        return value?.let { AttachmentType.valueOf(it) }
    }
}
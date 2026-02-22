package com.example.lifetrace.data.database.converter

import androidx.room.TypeConverter
import com.example.lifetrace.data.database.entity.MemoryType
import com.example.lifetrace.data.database.entity.TripStatus

//自定义类型，Room正常工作需做如下处理
class Converters {
    @TypeConverter
    fun fromTripStaus(status: TripStatus): String {
        return status.name
    }

    @TypeConverter
    fun toTripStatus(value: String): TripStatus {
        return TripStatus.valueOf(value)
    }

    @TypeConverter
    fun fromMemoryType(type: MemoryType): String {
        return type.name
    }

    @TypeConverter
    fun toMemoryType(value: String): MemoryType {
        return MemoryType.valueOf(value)
    }
}
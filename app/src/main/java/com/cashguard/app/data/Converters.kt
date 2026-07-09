package com.cashguard.app.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromTxType(value: TxType): String = value.name

    @TypeConverter
    fun toTxType(value: String): TxType = TxType.valueOf(value)
}

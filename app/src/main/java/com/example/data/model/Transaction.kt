package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val type: String, // "PEMASUKAN" or "PENGELUARAN"
    val category: String,
    val notes: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
    val accountEmail: String = "" // Under which google account this is stored (supports multi-device sync simulation)
)

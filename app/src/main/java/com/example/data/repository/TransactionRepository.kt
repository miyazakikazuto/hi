package com.example.data.repository

import com.example.data.local.TransactionDao
import com.example.data.model.Transaction
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()

    fun getTransactionsByEmail(email: String): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByEmail(email)
    }

    suspend fun insert(transaction: Transaction) {
        transactionDao.insertTransaction(transaction)
    }

    suspend fun deleteById(id: Int) {
        transactionDao.deleteTransactionById(id)
    }

    suspend fun clearAll() {
        transactionDao.clearAllTransactions()
    }
}

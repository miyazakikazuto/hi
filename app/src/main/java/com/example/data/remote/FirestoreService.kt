package com.example.data.remote

import android.util.Log
import com.example.data.model.Transaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore Service for "Pelacak Keuangan" application.
 *
 * ## FIRESTORE DATA SCHEMA DESIGN:
 *
 * Collection: `users_data`
 *  └── Document: `{user_email}` (e.g., "zaenalarifin6788@gmail.com")
 *       └── Fields:
 *            ├── email: String
 *            └── lastSyncedMillis: Long
 *       └── Subcollection: `transactions`
 *            └── Document: `{transaction_id}` (Room DB id mapper / Unique UUID)
 *                 └── Fields:
 *                      ├── id: Int (Local Room primary key equivalent)
 *                      ├── amount: Double
 *                      ├── type: String ("PEMASUKAN" | "PENGELUARAN")
 *                      ├── category: String
 *                      ├── notes: String
 *                      ├── dateMillis: Long
 *                      └── accountEmail: String
 */
class FirestoreService {

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    companion object {
        private const val TAG = "FirestoreService"
        private const val USERS_COLLECTION = "users_data"
        private const val TRANSACTIONS_SUB_COLLECTION = "transactions"
    }

    /**
     * CREATE / UPDATE: Uploads or updates a transaction in Firestore under a specific user account.
     * Uses [SetOptions.merge] to overwrite or merge data safely.
     *
     * @param email The authenticated Google user email.
     * @param transaction The [Transaction] object from the database.
     */
    suspend fun saveTransaction(email: String, transaction: Transaction): Boolean {
        if (email.isBlank()) return false
        return try {
            val userDocRef = db.collection(USERS_COLLECTION).document(email)
            
            // Touch user profile entry
            userDocRef.set(
                mapOf(
                    "email" to email,
                    "lastSyncedMillis" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()

            // Unique document ID using transaction ID
            val docId = transaction.id.toString()
            val transactionMap = mapOf(
                "id" to transaction.id,
                "amount" to transaction.amount,
                "type" to transaction.type,
                "category" to transaction.category,
                "notes" to transaction.notes,
                "dateMillis" to transaction.dateMillis,
                "accountEmail" to email
            )

            userDocRef.collection(TRANSACTIONS_SUB_COLLECTION)
                .document(docId)
                .set(transactionMap, SetOptions.merge())
                .await()

            Log.d(TAG, "Transaksi $docId berhasil disimpan di Firestore.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyimpan transaksi ke Firestore: ${e.localizedMessage}", e)
            false
        }
    }

    /**
     * READ: Fetches all transactions from Firestore for a specific user email.
     * Uses Coroutine Flow with snapshot listener to listen to real-time sync updates.
     *
     * @param email The authenticated Google user email.
     */
    fun streamTransactions(email: String): Flow<List<Transaction>> = callbackFlow {
        if (email.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection(USERS_COLLECTION)
            .document(email)
            .collection(TRANSACTIONS_SUB_COLLECTION)
            .orderBy("dateMillis")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error streaming transactions: ${error.localizedMessage}", error)
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            val id = doc.getLong("id")?.toInt() ?: 0
                            val amount = doc.getDouble("amount") ?: 0.0
                            val type = doc.getString("type") ?: "PENGELUARAN"
                            val category = doc.getString("category") ?: "Lain-lain"
                            val notes = doc.getString("notes") ?: ""
                            val dateMillis = doc.getLong("dateMillis") ?: System.currentTimeMillis()
                            val accountEmail = doc.getString("accountEmail") ?: email

                            Transaction(
                                id = id,
                                amount = amount,
                                type = type,
                                category = category,
                                notes = notes,
                                dateMillis = dateMillis,
                                accountEmail = accountEmail
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Gagal mengurai dokumen transaksi: ${e.localizedMessage}")
                            null
                        }
                    }
                    trySend(list)
                }
            }

        awaitClose {
            Log.d(TAG, "Menutup stream listener untuk $email")
            listener.remove()
        }
    }

    /**
     * READ ONCE: Fetches a snapshot of transactions one-time (non-streaming).
     */
    suspend fun fetchTransactionsOnce(email: String): List<Transaction> {
        if (email.isBlank()) return emptyList()
        return try {
            val snapshot = db.collection(USERS_COLLECTION)
                .document(email)
                .collection(TRANSACTIONS_SUB_COLLECTION)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                try {
                    val id = doc.getLong("id")?.toInt() ?: 0
                    val amount = doc.getDouble("amount") ?: 0.0
                    val type = doc.getString("type") ?: "PENGELUARAN"
                    val category = doc.getString("category") ?: "Lain-lain"
                    val notes = doc.getString("notes") ?: ""
                    val dateMillis = doc.getLong("dateMillis") ?: System.currentTimeMillis()
                    val accountEmail = doc.getString("accountEmail") ?: email

                    Transaction(
                        id = id,
                        amount = amount,
                        type = type,
                        category = category,
                        notes = notes,
                        dateMillis = dateMillis,
                        accountEmail = accountEmail
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Parsial gagal parsing dokumen: ${e.localizedMessage}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengambil data transaksi dari Firestore: ${e.localizedMessage}")
            emptyList()
        }
    }

    /**
     * DELETE: Deletes a specific transaction from Firestore.
     *
     * @param email The authenticated user email.
     * @param txId The local unique transaction ID.
     */
    suspend fun deleteTransaction(email: String, txId: Int): Boolean {
        if (email.isBlank()) return false
        return try {
            db.collection(USERS_COLLECTION)
                .document(email)
                .collection(TRANSACTIONS_SUB_COLLECTION)
                .document(txId.toString())
                .delete()
                .await()
            Log.d(TAG, "Transaksi $txId berhasil dihapus dari Firestore.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menghapus transaksi dari Firestore: ${e.localizedMessage}", e)
            false
        }
    }

    /**
     * CLEAR: Deletes all transactions for a user account in Firestore (mostly for resetting profile).
     */
    suspend fun clearAllUserTransactions(email: String): Boolean {
        if (email.isBlank()) return false
        return try {
            val collectionRef = db.collection(USERS_COLLECTION)
                .document(email)
                .collection(TRANSACTIONS_SUB_COLLECTION)

            val snapshot = collectionRef.get().await()
            for (doc in snapshot.documents) {
                doc.reference.delete().await()
            }
            Log.d(TAG, "Bersihkan transaksi Firestore selesai untuk $email")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membersihkan transaksi: ${e.localizedMessage}")
            false
        }
    }
}

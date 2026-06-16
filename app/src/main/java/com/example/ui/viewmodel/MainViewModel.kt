package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.Transaction
import com.example.data.repository.TransactionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(
    private val application: Application,
    private val repository: TransactionRepository
) : AndroidViewModel(application) {

    // Filter states
    private val _selectedMonth = MutableStateFlow<Int>(Calendar.getInstance().get(Calendar.MONTH) + 1) // 1-indexed (1 = Jan)
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()

    private val _selectedYear = MutableStateFlow<Int>(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String>("Semua")
    val selectedCategoryFilter: StateFlow<String> = _selectedCategoryFilter.asStateFlow()

    // Authentication States (Mock Google Sign-In with real-like properties)
    private val _isLoggedIn = MutableStateFlow<Boolean>(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userEmail = MutableStateFlow<String>("")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _userName = MutableStateFlow<String>("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    // Sync States
    private val _isSyncing = MutableStateFlow<Boolean>(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<String>("Belum pernah")
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    private val _isCloudSyncEnabled = MutableStateFlow<Boolean>(false)
    val isCloudSyncEnabled: StateFlow<Boolean> = _isCloudSyncEnabled.asStateFlow()

    // Raw transactions list from Room
    val allTransactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Filtered transaction lists
    val filteredTransactions: StateFlow<List<Transaction>> = combine(
        allTransactions,
        combine(_selectedMonth, _selectedYear, _selectedCategoryFilter) { m, y, c -> Triple(m, y, c) },
        combine(_isLoggedIn, _userEmail) { logged, email -> Pair(logged, email) }
    ) { txList, dateAndCategory, authInfo ->
        val (month, year, cat) = dateAndCategory
        val (isLoggedIn, email) = authInfo
        txList.filter { tx ->
            // Date filter
            val cal = Calendar.getInstance().apply { timeInMillis = tx.dateMillis }
            val txMonth = cal.get(Calendar.MONTH) + 1
            val txYear = cal.get(Calendar.YEAR)
            
            val matchesMonth = month == 0 || txMonth == month
            val matchesYear = txYear == year
            val matchesCat = cat == "Semua" || tx.category == cat
            
            // If logged in, show transactions bound to this google email or general guest.
            // If offline, show all guest/general transactions.
            val matchesEmail = if (isLoggedIn && email.isNotEmpty()) {
                tx.accountEmail == email || tx.accountEmail.isEmpty()
            } else {
                tx.accountEmail.isEmpty()
            }

            matchesMonth && matchesYear && matchesCat && matchesEmail
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Initial dummy data generator
    init {
        viewModelScope.launch {
            // Check if db is empty, if so, insert mock records
            allTransactions.first { true } // Wait for flow initialization
            delay(300)
            if (allTransactions.value.isEmpty()) {
                insertMockData()
            }
        }
    }

    private suspend fun insertMockData() {
        val now = Calendar.getInstance()
        
        val t1 = Transaction(
            amount = 12000000.0,
            type = "PEMASUKAN",
            category = "Gaji",
            notes = "Gaji Pokok Bulanan",
            dateMillis = now.apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        )
        val t2 = Transaction(
            amount = 1500000.0,
            type = "PENGELUARAN",
            category = "Belanja",
            notes = "Spurs & Kebutuhan Bulanan",
            dateMillis = now.apply { set(Calendar.DAY_OF_MONTH, 2) }.timeInMillis
        )
        val t3 = Transaction(
            amount = 180000.0,
            type = "PENGELUARAN",
            category = "Makanan",
            notes = "Makan siang bersama tim",
            dateMillis = now.apply { set(Calendar.DAY_OF_MONTH, 4) }.timeInMillis
        )
        val t4 = Transaction(
            amount = 250000.0,
            type = "PENGELUARAN",
            category = "Transportasi",
            notes = "Isi bensin Pertamax",
            dateMillis = now.apply { set(Calendar.DAY_OF_MONTH, 5) }.timeInMillis
        )
        val t5 = Transaction(
            amount = 3500000.0,
            type = "PEMASUKAN",
            category = "Sampingan",
            notes = "Desain Freelance UI/UX",
            dateMillis = now.apply { set(Calendar.DAY_OF_MONTH, 8) }.timeInMillis
        )
        val t6 = Transaction(
            amount = 450000.0,
            type = "PENGELUARAN",
            category = "Tagihan",
            notes = "Tagihan Listrik & WiFi",
            dateMillis = now.apply { set(Calendar.DAY_OF_MONTH, 10) }.timeInMillis
        )

        repository.insert(t1)
        repository.insert(t2)
        repository.insert(t3)
        repository.insert(t4)
        repository.insert(t5)
        repository.insert(t6)
    }

    // Setters for filters
    fun setMonth(month: Int) {
        _selectedMonth.value = month
    }

    fun setYear(year: Int) {
        _selectedYear.value = year
    }

    fun setCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    // Transaction CRUD
    fun addTransaction(amount: Double, type: String, category: String, notes: String, dateMillis: Long) {
        viewModelScope.launch {
            val email = if (_isLoggedIn.value) _userEmail.value else ""
            val transaction = Transaction(
                amount = amount,
                type = type,
                category = category,
                notes = notes,
                dateMillis = dateMillis,
                accountEmail = email
            )
            repository.insert(transaction)
            
            // If auto-sync is enabled, trigger sync response
            if (_isCloudSyncEnabled.value) {
                triggerSync()
            }
        }
    }

    fun deleteTransaction(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
            if (_isCloudSyncEnabled.value) {
                triggerSync()
            }
        }
    }

    // Google Authentication Mock login/logout
    fun loginWithGoogle(email: String, name: String) {
        viewModelScope.launch {
            _isLoggedIn.value = true
            _userEmail.value = email
            _userName.value = name
            _isCloudSyncEnabled.value = true
            triggerSync()
        }
    }

    fun logout() {
        viewModelScope.launch {
            _isLoggedIn.value = false
            _userEmail.value = ""
            _userName.value = ""
            _isCloudSyncEnabled.value = false
            _lastSyncTime.value = "Belum pernah"
        }
    }

    // Trigger Cloud Sync representation
    fun triggerSync() {
        viewModelScope.launch {
            if (!_isLoggedIn.value) return@launch
            _isSyncing.value = true
            delay(1500) // Simulate cloud transit delay
            _isSyncing.value = false
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("id", "ID"))
            _lastSyncTime.value = sdf.format(Date())
        }
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        _isCloudSyncEnabled.value = enabled
        if (enabled) {
            triggerSync()
        }
    }

    // Export PDF function
    fun exportToPdf() {
        viewModelScope.launch {
            val transactions = filteredTransactions.value
            if (transactions.isEmpty()) {
                Toast.makeText(application, "Tidak ada transaksi untuk diekspor!", Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                _isSyncing.value = true
                delay(800) // Visual progress simulation

                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size (595x842 pt)
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                val paint = Paint()
                val textPaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 12f
                    isAntiAlias = true
                }

                // Color schemes for PDF
                val primaryColor = Color.rgb(18, 117, 216)
                val accentColor = Color.rgb(22, 163, 74) // Green
                val warnColor = Color.rgb(220, 38, 38)   // Red
                val grayColor = Color.rgb(156, 163, 175)

                // Title
                paint.color = primaryColor
                canvas.drawRect(0f, 0f, 595f, 60f, paint)

                textPaint.apply {
                    color = Color.WHITE
                    textSize = 18f
                    isFakeBoldText = true
                }
                canvas.drawText("LAPORAN KEUANGAN PRIBADI", 20f, 38f, textPaint)

                // Subtitle
                textPaint.apply {
                    textSize = 10f
                    isFakeBoldText = false
                }
                val sdfDate = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
                canvas.drawText("Dicetak pada: ${sdfDate.format(Date())}", 420f, 35f, textPaint)

                // User details
                textPaint.apply {
                    color = Color.BLACK
                    textSize = 11f
                    isFakeBoldText = true
                }
                val owner = if (_isLoggedIn.value) _userName.value else "Guest User"
                canvas.drawText("Pemilik Akun: $owner", 25f, 90f, textPaint)
                canvas.drawText("Email: ${if (_isLoggedIn.value) _userEmail.value else "Offline (Tidak Masuk Google)"}", 25f, 105f, textPaint)

                // Total calculations
                var totIncome = 0.0
                var totExpense = 0.0
                for (tx in transactions) {
                    if (tx.type == "PEMASUKAN") totIncome += tx.amount else totExpense += tx.amount
                }
                val balance = totIncome - totExpense

                val numFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
                    maximumFractionDigits = 0
                }

                // Balance summary boxes
                paint.color = Color.rgb(243, 244, 246)
                canvas.drawRoundRect(25f, 130f, 190f, 180f, 8f, 8f, paint)
                canvas.drawRoundRect(210f, 130f, 375f, 180f, 8f, 8f, paint)
                canvas.drawRoundRect(395f, 130f, 570f, 180f, 8f, 8f, paint)

                // Summary labels
                textPaint.apply {
                    textSize = 9f
                    color = Color.DKGRAY
                    isFakeBoldText = false
                }
                canvas.drawText("TOTAL PEMASUKAN", 35f, 150f, textPaint)
                canvas.drawText("TOTAL PENGELUARAN", 220f, 150f, textPaint)
                canvas.drawText("SALDO AKHIR", 405f, 150f, textPaint)

                // Summary values
                textPaint.apply {
                    textSize = 11f
                    isFakeBoldText = true
                }
                textPaint.color = accentColor
                canvas.drawText(numFormat.format(totIncome), 35f, 170f, textPaint)
                textPaint.color = warnColor
                canvas.drawText(numFormat.format(totExpense), 220f, 170f, textPaint)
                textPaint.color = if (balance >= 0) primaryColor else warnColor
                canvas.drawText(numFormat.format(balance), 405f, 170f, textPaint)

                // Table Header
                textPaint.color = Color.BLACK
                textPaint.textSize = 12f
                textPaint.isFakeBoldText = true
                canvas.drawText("Daftar Transaksi", 25f, 215f, textPaint)

                paint.color = primaryColor
                canvas.drawRect(25f, 225f, 570f, 245f, paint)

                textPaint.apply {
                    color = Color.WHITE
                    textSize = 10f
                    isFakeBoldText = true
                }
                canvas.drawText("Tanggal", 30f, 239f, textPaint)
                canvas.drawText("Kategori", 110f, 239f, textPaint)
                canvas.drawText("Tipe", 200f, 239f, textPaint)
                canvas.drawText("Catatan", 290f, 239f, textPaint)
                canvas.drawText("Jumlah", 470f, 239f, textPaint)

                // Draw Table Rows
                textPaint.color = Color.BLACK
                textPaint.isFakeBoldText = false
                var yOffset = 265f
                val rowHeight = 22f
                val sdfRow = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID"))

                for (idx in transactions.indices) {
                    val tx = transactions[idx]
                    // Zebra detailing
                    if (idx % 2 == 1) {
                        paint.color = Color.rgb(249, 250, 251)
                        canvas.drawRect(25f, yOffset - 16f, 570f, yOffset + 6f, paint)
                    }

                    textPaint.color = Color.BLACK
                    canvas.drawText(sdfRow.format(Date(tx.dateMillis)), 30f, yOffset, textPaint)
                    canvas.drawText(tx.category, 110f, yOffset, textPaint)

                    if (tx.type == "PEMASUKAN") {
                        textPaint.color = accentColor
                        canvas.drawText("MASUK", 200f, yOffset, textPaint)
                    } else {
                        textPaint.color = warnColor
                        canvas.drawText("KELUAR", 200f, yOffset, textPaint)
                    }

                    textPaint.color = Color.DKGRAY
                    val noteStr = if (tx.notes.length > 22) tx.notes.take(20) + ".." else tx.notes
                    canvas.drawText(noteStr, 290f, yOffset, textPaint)

                    textPaint.color = if (tx.type == "PEMASUKAN") accentColor else warnColor
                    canvas.drawText(numFormat.format(tx.amount), 470f, yOffset, textPaint)

                    yOffset += rowHeight
                    
                    // Prevent page overflow for simple generation
                    if (yOffset > 800f) {
                        textPaint.color = grayColor
                        textPaint.textSize = 8f
                        canvas.drawText("... Laporan terpotong karena melebihi batas halaman A4 ...", 25f, 815f, textPaint)
                        break
                    }
                }

                // Footer signature
                paint.color = grayColor
                canvas.drawRect(25f, 820f, 570f, 821f, paint)

                textPaint.apply {
                    color = Color.GRAY
                    textSize = 8f
                    isFakeBoldText = false
                }
                canvas.drawText("Aplikasi Pelacak Keuangan - Sinkronisasi Multiplatform Terintegrasi (Android / iOS)", 25f, 833f, textPaint)

                pdfDocument.finishPage(page)

                // Save file to internal cache/external files
                val pdfDir = File(application.cacheDir, "laporan")
                if (!pdfDir.exists()) pdfDir.mkdirs()
                
                val pdfFile = File(pdfDir, "Laporan_Keuangan_${System.currentTimeMillis()}.pdf")
                val fos = FileOutputStream(pdfFile)
                pdfDocument.writeTo(fos)
                pdfDocument.close()
                fos.close()

                _isSyncing.value = false
                
                // Intent to view standard PDF share
                val pdfUri: Uri = FileProvider.getUriForFile(
                    application,
                    "${application.packageName}.fileprovider",
                    pdfFile
                )
                
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, pdfUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                val chooserIntent = Intent.createChooser(intent, "Ekspor PDF Laporan").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                application.startActivity(chooserIntent)
                Toast.makeText(application, "Laporan PDF berhasil dibuat!", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                e.printStackTrace()
                _isSyncing.value = false
                Toast.makeText(application, "Gagal membuat PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class ViewModelFactory(
    private val application: Application,
    private val repository: TransactionRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

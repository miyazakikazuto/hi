package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppDatabase
import com.example.data.model.Transaction
import com.example.data.repository.TransactionRepository
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.ViewModelFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val db = AppDatabase.getDatabase(applicationContext)
                val repository = TransactionRepository(db.transactionDao())
                val vm: MainViewModel = viewModel(
                    factory = ViewModelFactory(application, repository),
                )
                MainScreen(viewModel = vm)
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var activeTab by remember { mutableIntStateOf(0) } // 0: Transaksi, 1: Analisis, 2: Sinkronisasi & Akun
    var showAddDialog by remember { mutableStateOf(value = false) }

    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Pelacak Keuangan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isLoggedIn) "Akun: $userName" else "Versi Offline (Simulasi Google Sync)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (isLoggedIn) {
                        IconButton(onClick = { viewModel.triggerSync() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sinkronisasi Manual",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Transaksi") },
                    label = { Text("Transaksi", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_transaksi")
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Analisis") },
                    label = { Text("Analisis", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_analisis")
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = "Akun & Sync") },
                    label = { Text("Sinkronisasi", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_akun")
                )
            }
        },
        floatingActionButton = {
            if (activeTab == 0) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("tambah_transaksi_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah Transaksi")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                0 -> TransaksiTab(viewModel = viewModel)
                1 -> AnalisisTab(viewModel = viewModel)
                2 -> AkunTab(viewModel = viewModel)
            }

            if (showAddDialog) {
                AddTransactionDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { amount, type, category, notes, dateMillis ->
                        viewModel.addTransaction(amount, type, category, notes, dateMillis)
                        showAddDialog = false
                    }
                )
            }
        }
    }
}

// ---------------------- TAB 1: TRANSAKSI ----------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransaksiTab(viewModel: MainViewModel) {
    val transactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()

    // Aggregate values
    var totalMundurMasukan = 0.0
    var totalMundurKeluaran = 0.0
    transactions.forEach {
        if (it.type == "PEMASUKAN") totalMundurMasukan += it.amount else totalMundurKeluaran += it.amount
    }
    val totalSaldo = totalMundurMasukan - totalMundurKeluaran

    val idLocale = Locale.forLanguageTag("id-ID")
    val rupiahFormatter = NumberFormat.getCurrencyInstance(idLocale).apply {
        maximumFractionDigits = 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Summary Cards Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Total Saldo Bersih",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = rupiahFormatter.format(totalSaldo),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (totalSaldo >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Pemasukan
                    Column(horizontalAlignment = Alignment.Start) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2E7D32))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Pemasukan",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Text(
                            text = rupiahFormatter.format(totalMundurMasukan),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }

                    // Pengeluaran
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFC62828))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Pengeluaran",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Text(
                            text = rupiahFormatter.format(totalMundurKeluaran),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828)
                        )
                    }
                }
            }
        }

        // Quick Date & Category Filters Grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Month selector dropdown triggers
                var showMonthMenu by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { showMonthMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(getMonthName(selectedMonth), overflow = TextOverflow.Ellipsis, maxLines = 1)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showMonthMenu,
                        onDismissRequest = { showMonthMenu = false }
                    ) {
                        (1..12).forEach { m ->
                            DropdownMenuItem(
                                text = { Text(getMonthName(m)) },
                                onClick = {
                                    viewModel.setMonth(m)
                                    showMonthMenu = false
                                }
                            )
                        }
                    }
                }

                // Year selector
                var showYearMenu by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(0.8f)) {
                    OutlinedButton(
                        onClick = { showYearMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(selectedYear.toString())
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showYearMenu,
                        onDismissRequest = { showYearMenu = false }
                    ) {
                        listOf(2025, 2026, 2027).forEach { y ->
                            DropdownMenuItem(
                                text = { Text(y.toString()) },
                                onClick = {
                                    viewModel.setYear(y)
                                    showYearMenu = false
                                }
                            )
                        }
                    }
                }

                // Category filter
                var showCatMenu by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1.2f)) {
                    OutlinedButton(
                        onClick = { showCatMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(categoryFilter, overflow = TextOverflow.Ellipsis, maxLines = 1)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showCatMenu,
                        onDismissRequest = { showCatMenu = false }
                    ) {
                        listOf("Semua", "Gaji", "Sampingan", "Investasi", "Hadiah", "Makanan", "Transportasi", "Belanja", "Tagihan", "Kesehatan", "Lain-lain").forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    viewModel.setCategoryFilter(cat)
                                    showCatMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Transaction List
        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Belum ada transaksi di bulan ini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Klik '+' untuk menambahkan transaksi baru.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions, key = { it.id }) { tx ->
                    TransactionItem(
                        transaction = tx,
                        onDelete = { viewModel.deleteTransaction(tx.id) },
                        rupiahFormatter = rupiahFormatter
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    onDelete: () -> Unit,
    rupiahFormatter: NumberFormat
) {
    val isPemasukan = transaction.type == "PEMASUKAN"
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("id-ID"))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Amount badge visualizer
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isPemasukan) Color(0xFFC8E6C9) else Color(0xFFFFCDD2)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPemasukan) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isPemasukan) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transaction.category,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isPemasukan) "+" + rupiahFormatter.format(transaction.amount) 
                               else "-" + rupiahFormatter.format(transaction.amount),
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isPemasukan) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transaction.notes.ifEmpty { "Tanpa catatan" },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = sdf.format(Date(transaction.dateMillis)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Delete action
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Hapus",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}


// ---------------------- TAB 2: ANALISIS ----------------------

@Composable
fun AnalisisTab(viewModel: MainViewModel) {
    val transactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val idLocale = Locale("id", "ID")
    val rupiahFormatter = NumberFormat.getCurrencyInstance(idLocale).apply {
        maximumFractionDigits = 0
    }

    var totalMasuk = 0.0
    var totalKeluar = 0.0
    val expenseCategoryMap = mutableMapOf<String, Double>()

    transactions.forEach { tx ->
        if (tx.type == "PEMASUKAN") {
            totalMasuk += tx.amount
        } else {
            totalKeluar += tx.amount
            expenseCategoryMap[tx.category] = (expenseCategoryMap[tx.category] ?: 0.0) + tx.amount
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Grafik Analisis Bulanan",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Comparison Bar Chart Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Arus Kas: Masuk vs Keluar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Custom Bar Chart using Canvas
                val maxVal = maxOf(totalMasuk, totalKeluar, 1.0)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    val barWidth = 60.dp.toPx()
                    val gap = 50.dp.toPx()
                    val horizontalCenter = size.width / 2

                    // Grid lines (3 horizontal helper lines)
                    val linesCount = 3
                    for (i in 1..linesCount) {
                        val y = size.height * (i.toFloat() / (linesCount + 1))
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.4f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Bar 1: Pemasukan
                    val bar1Height = (totalMasuk / maxVal * (size.height - 20.dp.toPx())).toFloat()
                    drawRect(
                        color = Color(0xFF4CAF50),
                        topLeft = Offset(horizontalCenter - barWidth - gap / 2, size.height - bar1Height),
                        size = Size(barWidth, bar1Height)
                    )

                    // Bar 2: Pengeluaran
                    val bar2Height = (totalKeluar / maxVal * (size.height - 20.dp.toPx())).toFloat()
                    drawRect(
                        color = Color(0xFFE53935),
                        topLeft = Offset(horizontalCenter + gap / 2, size.height - bar2Height),
                        size = Size(barWidth, bar2Height)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Masuk", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(rupiahFormatter.format(totalMasuk), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Keluar", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(rupiahFormatter.format(totalKeluar), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                    }
                }
            }
        }

        // Category Expenditure Distribution (Donut / Pie Chart)
        if (totalKeluar > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Proporsi Pengeluaran Kategori",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // Draw custom Ring Segmented Donut Chart
                    val colors = listOf(
                        Color(0xFFE53935), // Red
                        Color(0xFF1E88E5), // Blue
                        Color(0xFFFFB300), // Amber
                        Color(0xFF8E24AA), // Purple
                        Color(0xFF00ACC1), // Cyan
                        Color(0xFF43A047), // Green
                        Color(0xFFF4511E)  // Orange
                    )

                    val categoriesList = expenseCategoryMap.keys.toList()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Canvas Donut Chart
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(130.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(110.dp)) {
                                var currentAngle = -90f
                                categoriesList.forEachIndexed { i, category ->
                                    val amount = expenseCategoryMap[category] ?: 0.0
                                    val sweepAngle = ((amount / totalKeluar) * 360f).toFloat()
                                    val colorIndex = i % colors.size

                                    drawArc(
                                        color = colors[colorIndex],
                                        startAngle = currentAngle,
                                        sweepAngle = sweepAngle,
                                        useCenter = false,
                                        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                    currentAngle += sweepAngle
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Pengeluaran", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = rupiahFormatter.format(totalKeluar),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Category Legends Layout
                        Column(modifier = Modifier.weight(1.2f)) {
                            categoriesList.forEachIndexed { i, category ->
                                val amount = expenseCategoryMap[category] ?: 0.0
                                val percent = (amount / totalKeluar) * 100
                                val colorIndex = i % colors.size

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(colors[colorIndex])
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val locale = LocalConfiguration.current.locales[0]
                                    Text(
                                        text = "$category (${String.format(locale, "%.1f", percent)}%)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Grafik pengeluaran kategori kosong karena belum ada pengeluaran terdaftar untuk filter saat ini.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}


// ---------------------- TAB 3: AKUN & SYNC ----------------------

@Composable
fun AkunTab(viewModel: MainViewModel) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
    val isCloudSyncEnabled by viewModel.isCloudSyncEnabled.collectAsStateWithLifecycle()

    var showLoginSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Sinkronisasi Cloud",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Device Multiplatform Support Banner Info
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            )
        ) {
            Row(modifier = Modifier.padding(14.dp)) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Tersedia di iOS (iPhone) & Android",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Gunakan Akun Google yang sama untuk menyinkronkan seluruh catatan transaksi Anda secara instan dan aman antar perangkat.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Account management Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (isLoggedIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userName.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(text = userName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(text = userEmail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Cloud Sync options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Sinkronisasi Otomatis", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Unggah otomatis saat transaksi berubah", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isCloudSyncEnabled,
                            onCheckedChange = { viewModel.setCloudSyncEnabled(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Terakhir Sinkron", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(lastSyncTime, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        OutlinedButton(
                            onClick = { viewModel.triggerSync() },
                            enabled = !isSyncing
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Keluar dari Akun")
                    }

                } else {
                    // Signed out screen
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Belum Terhubung dengan Akun",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "Masuk dengan Google untuk mengaktifkan pemulihan data dan sinkronisasi di iOS / Android / Web.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Custom Google Login Button
                        Button(
                            onClick = { showLoginSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("google_login_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Masuk dengan Google")
                            }
                        }
                    }
                }
            }
        }

        // Action reports Card (PDF Export)
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Laporan Keuangan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ekspor rangkuman pemasukan dan pengeluaran Anda ke file PDF standar yang dapat disimpan atau dibagikan.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.exportToPdf() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_pdf_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ekspor dan Bagikan PDF")
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))

        // Simulated Login Sheet/Dialog
        if (showLoginSheet) {
            GoogleMockLoginDialog(
                onDismiss = { showLoginSheet = false },
                onLogin = { email, name ->
                    viewModel.loginWithGoogle(email, name)
                    showLoginSheet = false
                }
            )
        }
    }
}

@Composable
fun GoogleMockLoginDialog(onDismiss: () -> Unit, onLogin: (String, String) -> Unit) {
    // Custom tailored default data based on user metadata
    val defaultEmail = "zaenalarifin6788@gmail.com"
    var nameField by remember { mutableStateOf("Zaenal Arifin") }
    var emailField by remember { mutableStateOf(defaultEmail) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color.LightGray, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Layanan Akun Google", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Pilih atau ubah detail profil sinkronisasi Anda:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = nameField,
                    onValueChange = { nameField = it },
                    label = { Text("Nama Lengkap") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = emailField,
                    onValueChange = { emailField = it },
                    label = { Text("Email Google") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Button(
                        onClick = {
                            if (emailField.isNotEmpty() && nameField.isNotEmpty()) {
                                onLogin(emailField, nameField)
                            }
                        }
                    ) {
                        Text("Konfirmasi Masuk")
                    }
                }
            }
        }
    }
}


// ---------------------- DIALOGS & HELPERS ----------------------

@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String, String, Long) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("PEMASUKAN") } // Default to "PEMASUKAN"
    
    val currentIncomes = listOf("Gaji", "Sampingan", "Investasi", "Hadiah", "Lain-lain")
    val currentExpenses = listOf("Makanan", "Transportasi", "Belanja", "Tagihan", "Kesehatan", "Lain-lain")
    
    var category by remember { mutableStateOf("Makanan") }
    var notes by remember { mutableStateOf("") }

    // Automatically reset active category with appropriate type defaults
    LaunchedEffect(type) {
        category = if (type == "PEMASUKAN") currentIncomes[0] else currentExpenses[0]
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Tambah Transaksi Baru",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Select Type of Transaction (TAB Segment)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { type = "PEMASUKAN" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "PEMASUKAN") Color(0xFF2E7D32) else Color.Transparent,
                            contentColor = if (type == "PEMASUKAN") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("type_pemasukan_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Pemasukan")
                    }
                    Button(
                        onClick = { type = "PENGELUARAN" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "PENGELUARAN") MaterialTheme.colorScheme.error else Color.Transparent,
                            contentColor = if (type == "PENGELUARAN") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("type_pengeluaran_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Pengeluaran")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input fields
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Jumlah (Rp)") },
                    prefix = { Text("Rp ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("amount_field")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category selector
                var showCatList by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showCatList = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Kategori: $category")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showCatList,
                        onDismissRequest = { showCatList = false }
                    ) {
                        val activeCategories = if (type == "PEMASUKAN") currentIncomes else currentExpenses
                        activeCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    showCatList = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan / Keterangan") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notes_field")
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amount = amountStr.toDoubleOrNull()
                            if ((amount != null) && (amount > 0)) {
                                onConfirm(amount, type, category, notes, System.currentTimeMillis())
                            }
                        },
                        enabled = amountStr.toDoubleOrNull() != null,
                        modifier = Modifier.testTag("simpan_transaksi_btn")
                    ) {
                        Text("Simpan")
                    }
                }
            }
        }
    }
}

fun getMonthName(month: Int): String {
    return when (month) {
        1 -> "Januari"
        2 -> "Februari"
        3 -> "Maret"
        4 -> "April"
        5 -> "Mei"
        6 -> "Juni"
        7 -> "Juli"
        8 -> "Agustus"
        9 -> "September"
        10 -> "Oktober"
        11 -> "November"
        12 -> "Desember"
        else -> "Semua"
    }
}

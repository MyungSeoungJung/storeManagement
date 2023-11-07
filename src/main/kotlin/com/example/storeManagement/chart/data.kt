package com.example.storeManagement.chart

data class MonthlyChartData(
    val name: String,
    val data: List<Int>,
)

data class MonthlySales(val thisMonth: Int, val lastMonth: Int)



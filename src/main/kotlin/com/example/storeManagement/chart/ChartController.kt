package com.example.storeManagement.chart

import com.example.storeManagement.auth.Auth
import com.example.storeManagement.auth.AuthProfile
import com.example.storeManagement.order.OrderTable
import com.example.storeManagement.product.Product
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.javatime.year
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Month
@RestController
@RequestMapping("/chart")
class ChartController {

    @Auth
    @GetMapping("salesChart")
    fun salesChart(@RequestAttribute authProfile: AuthProfile,
                   @RequestParam year : String): List<MonthlyChartData> {
        return transaction {
            val o = OrderTable
            val p = Product
            println("year-----------$year")
            val sellerProduct = p.select { p.brand_id eq authProfile.id }.map {
                it[p.id]
            }

            val innerJoinOrderAndProduct = (o innerJoin p)

            val categoryTotalMap = mutableMapOf<String, MutableList<Int>>()

            sellerProduct.forEach { productId ->
                val orders = innerJoinOrderAndProduct
                    .select { o.productId eq productId }
                    .andWhere { o.orderDate.year() eq year.toInt() }
                    .map { it }

                orders.forEach { order ->
                    val productCategory = innerJoinOrderAndProduct
                        .select { p.id eq order[o.productId] }
                        .map { it[p.category] }
                        .firstOrNull()

                    if (productCategory != null) {
                        val orderDate = order[o.orderDate]
                        val month = orderDate.monthValue
                        val orderQuantity = order[o.quantity]

                        categoryTotalMap
                            .getOrPut(productCategory) { MutableList(12) { 0 } }
                            .set(month - 1, categoryTotalMap[productCategory]!![month - 1] + orderQuantity)
                    }
                }
            }

            val monthlyChartDataList = categoryTotalMap.map { (category, data) ->
                MonthlyChartData(name = category, data = data)
            }

            monthlyChartDataList
        }
    }
}

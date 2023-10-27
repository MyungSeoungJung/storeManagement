package com.example.storeManagement.chart

import com.example.storeManagement.auth.Auth
import com.example.storeManagement.auth.AuthProfile
import com.example.storeManagement.order.OrderTable
import com.example.storeManagement.product.Product
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Month
@RestController
@RequestMapping("/chart")
class ChartController {

    @Auth
    @GetMapping("salesChart")
    fun salesChart(@RequestAttribute authProfile: AuthProfile): List<MonthlyChartData> {
        return transaction {
            val o = OrderTable
            val p = Product

            val sellerProduct = p.select { p.brand_id eq authProfile.id }.map {  // 로그인한 유저의 제품만 추리기
                it[p.id]
            }

            val innerJoinOrderAndProduct = (o innerJoin p)

            val categoryDataList = sellerProduct.map { productId ->
                val categoryTotalMap = mutableMapOf<String, MutableList<Int>>()

                val orders = innerJoinOrderAndProduct  // 로그인한 유저의 주문만 추리기
                        .select { o.productId eq productId }
                        .map { it }

                orders.forEach { order ->
                    val productCategory = innerJoinOrderAndProduct
                            .select { p.id eq order[o.productId] }
                            .map { it[p.category] }
                            .firstOrNull()

                    if (productCategory != null) {  //각각 카테고리
                        val orderDate = order[o.orderDate]
                        val month = orderDate.monthValue
                        val orderQuantity = order[o.quantity]
                        categoryTotalMap
                                .getOrPut(productCategory) { MutableList(12) { 0 } }
                                .set(month - 1, categoryTotalMap[productCategory]!![month - 1] + orderQuantity)
                    }
                }

                categoryTotalMap.map { (category, data) ->
                    MonthlyChartData(name = category, data = data)
                }
            }.flatten()

            categoryDataList
        }
    }
}

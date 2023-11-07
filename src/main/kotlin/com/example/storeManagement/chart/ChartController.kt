package com.example.storeManagement.chart

import com.example.storeManagement.auth.Auth
import com.example.storeManagement.auth.AuthProfile
import com.example.storeManagement.order.OrderTable
import com.example.storeManagement.product.Product
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.javatime.month
import org.jetbrains.exposed.sql.javatime.year
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth

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
                    .andWhere { o.orderStatus eq true }  //추가
                    .map { it }

                orders.forEach { order ->
                    val productCategory = innerJoinOrderAndProduct
                        .select { p.id eq order[o.productId] }
                        .map { it[p.category] } // 순회중인 주문의 카테고리를 추출
                        .firstOrNull()

                    if (productCategory != null) {
                        val orderDate = order[o.orderDate]  //순회중인 주문의 orderDate 
                        val month = orderDate.monthValue   // 순회중인 주문의 월
                        val orderQuantity = order[o.quantity] // 순회중인 주문의 수량

                        categoryTotalMap  //순회중인 카테고리 ex 텐트면 텐트에 orderQuantity 추가
                            .getOrPut(productCategory) { MutableList(12) { 0 } }
                     // set 사용해서 내가 원하는 인덱스에 값 설정  ex : month 값이 11월이라면 -1 해서 10번째 인덱스에 값 설정
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

    @Auth
    @GetMapping("/salesGraphBlueGraph")
    fun getSalesGraph(@RequestAttribute authProfile: AuthProfile,
                      @RequestParam year : String) = transaction{
        println("blue ------------$year")
        val p = Product
        val o = OrderTable
        val monthSales = mutableMapOf<Int,MutableList<Int>>()

       val findUserProduct = p.select { p.brand_id eq authProfile.id }.map { it[p.id] }
       val productJoinOrder = (p innerJoin o)

        findUserProduct.forEach { productId ->
         val orderProductQuantity = productJoinOrder.select { o.productId eq productId }
             .andWhere { o.orderDate.year() eq year.toInt() }
             .map { it }  //주문 정보 뽑기

            orderProductQuantity.forEach {order ->
              val productPrice =  productJoinOrder.select { p.id eq order[o.productId] }//주문,제품id일치하는 애들만 select
                  .map { it[p.productPrice].toInt() }
                  .firstOrNull()

                if (productPrice != null) {
                    val orderDate = order[o.orderDate]
                    val year = orderDate.year
                    val month = orderDate.monthValue
                    val orderQuantity = order[o.quantity]

                    monthSales.getOrPut(year) { MutableList(12) { 0 } }[month - 1] += (productPrice * orderQuantity)

                }
            }
       }


        // 응답 데이터 생성
        val yearData = year.toInt()
        val monthData = monthSales[yearData] ?: List(12) { 0 }

        val response = mapOf("name" to year, "data" to monthData)

        return@transaction response
    }

    @Auth
    @GetMapping("/salesGraphGreenGraph")
    fun getSalesGraph2(@RequestAttribute authProfile: AuthProfile,
                      @RequestParam year : String) = transaction{
        println("green ------------$year")
        val p = Product
        val o = OrderTable
        val monthSales = mutableMapOf<Int,MutableList<Int>>()

        val findUserProduct = p.select { p.brand_id eq authProfile.id }.map { it[p.id] }
        val productJoinOrder = (p innerJoin o)

        findUserProduct.forEach { productId ->
            val orderProductQuantity = productJoinOrder.select { o.productId eq productId }
                .andWhere { o.orderDate.year() eq year.toInt() }
                .map { it }  //주문 정보 뽑기

            orderProductQuantity.forEach {order ->
                val productPrice =  productJoinOrder.select { p.id eq order[o.productId] }//주문,제품id일치하는 애들만 select
                    .map { it[p.productPrice].toInt() }
                    .firstOrNull()

                if (productPrice != null) {
                    val orderDate = order[o.orderDate]
                    val year = orderDate.year
                    val month = orderDate.monthValue
                    val orderQuantity = order[o.quantity]

                    monthSales.getOrPut(year) { MutableList(12) { 0 } }[month - 1] += (productPrice * orderQuantity)

                }
            }
        }


        // 응답 데이터 생성
        val yearData = year.toInt()
        val monthData = monthSales[yearData] ?: List(12) { 0 }

        val response = mapOf("name" to year, "data" to monthData)

        return@transaction response
    }


    @Auth
    @GetMapping("/settlementMoney")
    fun getMonthlySales(@RequestAttribute authProfile: AuthProfile): MonthlySales = transaction {
        val p = Product
        val o = OrderTable
        val currentYearMonth = YearMonth.now() // 현재 연도와 월
        val lastMonthYearMonth = currentYearMonth.minusMonths(1) // 이전 달 연도와 월
        val thisMonthSales = mutableListOf<Int>()
        val lastMonthSales = mutableListOf<Int>()

        val findUserProduct = p.select { p.brand_id eq authProfile.id }.map { it[p.id] }
        val productJoinOrder = (p innerJoin o)

        findUserProduct.forEach { productId ->
            val thisMonthOrderProductQuantity = productJoinOrder.select { o.productId eq productId }
                .andWhere { o.orderDate.year() eq currentYearMonth.year }
                .andWhere { o.orderDate.month() eq currentYearMonth.monthValue }
                .map { it }  // 이번 달 주문 정보 뽑기

            val lastMonthOrderProductQuantity = productJoinOrder.select { o.productId eq productId }
                .andWhere { o.orderDate.year() eq lastMonthYearMonth.year }
                .andWhere { o.orderDate.month() eq lastMonthYearMonth.monthValue }
                .map { it }  // 저번 달 주문 정보 뽑기

            thisMonthOrderProductQuantity.forEach { order ->
                val productPrice = productJoinOrder.select { p.id eq order[o.productId] }
                    .map { it[p.productPrice].toInt() }
                    .firstOrNull()

                if (productPrice != null) {
                    val orderQuantity = order[o.quantity]
                    // 이번 달 매출 정보를 누적
                    thisMonthSales.add(productPrice * orderQuantity)
                }
            }

            lastMonthOrderProductQuantity.forEach { order ->
                val productPrice = productJoinOrder.select { p.id eq order[o.productId] }
                    .map { it[p.productPrice].toInt() }
                    .firstOrNull()

                if (productPrice != null) {
                    val orderQuantity = order[o.quantity]
                    // 저번 달 매출 정보를 누적
                    lastMonthSales.add(productPrice * orderQuantity)
                }
            }
        }

        val thisMonthTotalSales = thisMonthSales.sum()
        val lastMonthTotalSales = lastMonthSales.sum()

        return@transaction MonthlySales(thisMonthTotalSales, lastMonthTotalSales)
    }
} // 끝

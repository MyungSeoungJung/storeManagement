package com.example.storeManagement.order

import OrderDetailsResponse
import OrderStateAndInfo
import ProductInfoAndFile
import com.example.storeManagement.auth.Auth
import com.example.storeManagement.auth.AuthProfile
import com.example.storeManagement.product.Product
import com.example.storeManagement.product.ProductFiles
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.month
import org.jetbrains.exposed.sql.javatime.year
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.sql.Connection
import java.time.YearMonth


@RestController
@RequestMapping("/order")
class OrderController (private val orderService: OrderService) {

    @Auth
    @GetMapping("/orderDetail")  //판매자 id에 해당하는 주문 정보만 띄우기 위해서
    fun getOrderDetail(@RequestAttribute authProfile: AuthProfile,
                       @RequestParam state : String,
                       @RequestParam(required = false) keyword: Long?,
                       @RequestParam size: Int,
                       @RequestParam page: Int
    )
    = transaction(
        Connection.TRANSACTION_READ_UNCOMMITTED, readOnly = true
    ) {
        val o = OrderTable
//        로그인한 id랑 일치하는 제품들만 추리기
        val findProductBrand = Product.select { Product.brand_id eq authProfile.id }.map { it[Product.id] }

        val orderAndProduct = (o innerJoin Product)
// ------------------------페이징 부분-------------------------------------
        // state 맞춰서 필터링
        println("state--------------------------$state")
        val stateQuery = when (state) {
            "true" -> orderAndProduct.select { (o.orderStatus eq true) }
            "false" -> orderAndProduct.select { (o.orderStatus eq false) }
            else -> orderAndProduct.selectAll()
        }
        // 주문번호 검색
        val searchOrderId = if (keyword == null) {
            stateQuery
        } else {
            stateQuery.andWhere { OrderTable.id eq keyword }
        }
        println("keyword----------------------$keyword")
        val totalCount = searchOrderId.count()
//        ---------------------------------------------------------------------
        //mapNotNull => null 값 제외
        val orderDetails = findProductBrand.map { productId ->
            //주문 정보 합치기
            println("stateQuery$stateQuery")

            val result = stateQuery
                .orderBy(OrderTable.id, SortOrder.DESC)
                .limit(size, offset = (size * page).toLong())
                .map { r ->
                    val orderStateAndInfo = o.select { (o.productId eq productId) }.map { r ->
                        OrderStateAndInfo(
                            orderId = r[o.orderId],
                            orderStatus = r[o.orderStatus],
                            quantity = r[o.quantity],
                            orderDate = r[o.orderDate].toString()
                        )
                    }

                    // 검색
                    // 제품 + 제품 사진 합치기
                    val productInfoAndFile =
                        (Product innerJoin ProductFiles).select { (Product.id eq r[o.productId]) }
                            .map {//로그인한 유저가 등록한 제품만 map
                                ProductInfoAndFile(
                                    it[Product.id],
                                    it[Product.productName],
                                    it[ProductFiles.uuidFileName],
                                    it[ProductFiles.originalFileName],
                                    it[ProductFiles.contentType],
                                )
                            }


                    //내보낼 응답 필터링

                    OrderDetailsResponse(
                            orderId = r[o.id],
                            quantity = r[o.quantity],
                            orderDate = r[o.orderDate].toString(),
                            orderState = r[o.orderStatus],
                            productInfo = productInfoAndFile
                        )

                }
            return@transaction PageImpl(result, PageRequest.of(page, size), totalCount)
        }
    }


    @GetMapping("/notifications")
    fun streamNotification(): SseEmitter {
        return orderService.createEmitter()
    }
    @Auth
    @GetMapping("/orderProcessingStatus")
    fun orderProcessingStaus (@RequestAttribute authProfile: AuthProfile) = transaction {
        val p = Product
        val o = OrderTable

        // 이번 달의 시작과 끝 날짜 계산
        val currentYearMonth = YearMonth.now()

        // 해당 브랜드의 모든 제품 ID 가져오기
        val productIds = p.select { p.brand_id eq authProfile.id }.map { it[p.id] }

        // 주문 성공 및 주문 실패 갯수 계산
        val successOrderCount = o.select {
            (o.productId inList productIds) and
                    (o.orderDate.year() eq currentYearMonth.year) and
                    (o.orderDate.month() eq currentYearMonth.monthValue) and
                    (o.orderStatus eq true)
        }.count()

        val failureOrderCount = o.select {
            (o.productId inList productIds) and
                    (o.orderDate.year() eq currentYearMonth.year) and
                    (o.orderDate.month() eq currentYearMonth.monthValue) and
                    (o.orderStatus eq false)
        }.count()
        // 응답 구성

        return@transaction mapOf<String, Long>(
            "successOrderCount" to successOrderCount,
            "failureOrderCount" to failureOrderCount
        )
    }
}
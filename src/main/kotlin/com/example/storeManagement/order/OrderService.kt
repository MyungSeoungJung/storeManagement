package com.example.storeManagement.order

import OrderRequest
import OrderResultResponse
import com.example.storeManagement.auth.Auth
import com.example.storeManagement.auth.AuthProfile
import com.example.storeManagement.product.Product
import com.example.storeManagement.product.ProductInventory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.relational.core.sql.Update
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.LocalDateTime

@Service
class OrderService(private val rabbitTemplate: RabbitTemplate) {
    private val mapper = jacksonObjectMapper()

    private val emitters = mutableListOf<SseEmitter>()
    @Auth
    @RabbitListener(queues = ["product-payment"]) // queue일고 주문내역 테이블에 저장
    fun receiveOrder(message : String){
        val orderRequest : OrderRequest = mapper.readValue(message)

        val deadEmitters : MutableList<SseEmitter> = ArrayList()

        for (emitter in emitters) {
            try {
            emitter.send(message)
            }catch (e: IOException){
                deadEmitters.add(emitter)
            }
        }
        emitters.removeAll(deadEmitters)
        println("주문 ------------$orderRequest-------------------------")
        val o = OrderTable
//        val od = OrderState   //주문상태
//        val pQuantity = ProductOrderQuantity   //차트 위한 총 갯수?

           val result = transaction {
               val insertOrder = o.insert {
                   it[this.orderId] = orderRequest.orderId
                   it[this.userId] = orderRequest.userId
                   it[this.productId] = orderRequest.productId
                   it[this.quantity] = orderRequest.quantity
                   it[this.address] = orderRequest.address
                   it[this.orderDate] = LocalDateTime.now()
               }
//               val insertOrderState = od.insert {
//                   it[this.orderId] = orderRequest.orderId
//                   it[this.orderStatus] = "대기"
//               }
//               val productOrderQuantity = pQuantity.insert {
//                   it[this.productId] = o.productId
//                   it[this.totalQuantity] = o.quantity //주문온 수량만큼 +되게끔
//               }

               //    order.quantity랑 비교해서 0이 아니라면 성공 메세지 보내기
               println(orderRequest)
               val pi = ProductInventory
               val os = OrderState
               // 주문제품 id랑 재고 제품 id 비교
               val findProduct = pi.select { pi.productId eq orderRequest.productId}
               val currentQuantity = findProduct.single()[pi.quantity]

               if (!findProduct.empty() && currentQuantity >= orderRequest.quantity) { //레코드 반환이라 null이 아니라 empty로 체크
                    // 재고수량 - 주문 수량 빼기 DB업데이트문
                   val newQuantity = currentQuantity - orderRequest.quantity  // 현재 수량 - 주문 수량

                   pi.update ({pi.productId eq orderRequest.productId}) {
                       it[quantity] = newQuantity.toInt()
                   }
                   os.insert {
                       it[os.orderId] = orderRequest.orderId
                       it[os.orderStatus] = "처리 완료"
                   }
                   // 성공 메세지 보내기
                   val successOrderRequest = OrderResultResponse(
                       orderId = orderRequest.orderId,
                       isPermission = "true"
                   )
                   sendResultMessage(successOrderRequest)

               } else if (findProduct.empty() || currentQuantity == 0 || orderRequest.quantity == 0){  //추가
                val falseOrderRequest = OrderResultResponse (
                    orderId = orderRequest.orderId,
                    isPermission = "false"
                )
                   os.insert {
                       it[os.orderId] = orderRequest.orderId
                       it[os.orderStatus] = "처리 실패"
                   }
                   sendResultMessage(falseOrderRequest)

               }

           }

    }

    fun sendResultMessage(orderResultResponse: OrderResultResponse){
        rabbitTemplate.convertAndSend("product-payment-result",mapper.writeValueAsString(orderResultResponse))
    }
}
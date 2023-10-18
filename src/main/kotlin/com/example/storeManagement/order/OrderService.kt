package com.example.storeManagement.order

import OrderRequest
import OrderResultResponse
import com.example.storeManagement.auth.Auth
import com.example.storeManagement.product.Product
import com.example.storeManagement.product.ProductInventory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.LocalDateTime

@Auth
@Service
class OrderService(private val rabbitTemplate: RabbitTemplate) {
    private val mapper = jacksonObjectMapper()

    private val emitters = mutableListOf<SseEmitter>()
    
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
        println("주문 ------------$orderRequest")
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
               val p = Product
               val findProduct = (p innerJoin ProductInventory).
               select { p.id eq orderRequest.productId }.map {
                   it[ProductInventory.quantity]
               }
               val isQuantityComparison = findProduct.isNotEmpty() && findProduct[0] >= orderRequest.quantity
               if (isQuantityComparison) {
                   val successOrderRequest = OrderResultResponse(
                       orderId = orderRequest.orderId,
                       isPermission = "ture"
                   )
                   sendResultMessage(successOrderRequest)
               } else {
                val falseOrderRequest = OrderResultResponse (
                    orderId = orderRequest.orderId,
                    isPermission = "false"
                )
                   sendResultMessage(falseOrderRequest)

               }
//
           }
    }

    fun sendResultMessage(orderResultResponse: OrderResultResponse){
        rabbitTemplate.convertAndSend("product-payment-result",mapper.writeValueAsString(orderResultResponse))
    }
}
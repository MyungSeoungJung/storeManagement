package com.example.storeManagement.product

import ProductInfo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service

@Service
class ProductService (private val rabbitTemplate: RabbitTemplate) {
    private val mapper = jacksonObjectMapper()

    fun createProductMessage(registrationRequest : ProductInfo){
        registrationRequest.id = 1
        for ((index,item) in registrationRequest.product.withIndex()) {
            item.id = (index + 1).toLong()

            sendMessage(registrationRequest)
        }
    }


    fun sendMessage(registrationRequest: ProductInfo){
        rabbitTemplate.convertAndSend("product-register", mapper.writeValueAsString(registrationRequest))
//        rabbitTemplate클래스의 convertAndSend()로 메세지전송
    }


}
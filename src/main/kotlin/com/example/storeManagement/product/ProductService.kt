package com.example.storeManagement.product

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service

@Service
class ProductService (private val rabbitTemplate: RabbitTemplate) {
    private val mapper = jacksonObjectMapper()

    fun createProductMessage(productMessageRequest: ProductMessageRequest) {
        sendMessage(productMessageRequest)
    }
    fun sendMessage(productMessageRequest: ProductMessageRequest) {
        rabbitTemplate.convertAndSend("product-register", mapper.writeValueAsString(productMessageRequest))
    }

}




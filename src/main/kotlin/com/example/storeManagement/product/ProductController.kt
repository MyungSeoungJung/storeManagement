package com.example.storeManagement.product

import ProductInfo
import com.example.storeManagement.product.Product.productName
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/product")
class ProductController(private val productService : ProductService) {

    @PostMapping ("/register")
    fun productRegisterService(@RequestBody registrationRequest : ProductInfo ){
        transaction {
           Product.insert {
                it[productName] = registrationRequest.product[0].productName
                it[productCode] = registrationRequest.product[0].productCode
                it[productDescription] = registrationRequest.product[0].productDescription
                it[productPrice] = registrationRequest.product[0].productPrice
                it[category] = registrationRequest.product[0].category
            }
            ProductFiles.insert {
                it[productId] = registrationRequest.product[0].file[0].productId
                it[originalFileName] = registrationRequest.product[0].file[0].originalFileName
                it[uuidFileName] = registrationRequest.product[0].file[0].uuidFileName
                it[contentType]= registrationRequest.product[0].file[0].contentType
            }
        }
        productService.createProductMessage(registrationRequest)
    }
}
package com.example.storeManagement.product

import ProductMessageRequest
import com.example.storeManagement.product.Product.productName
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

@RestController
@RequestMapping("/product")
class ProductController(private val productService : ProductService) {
    private val POST_FILE_PATH = "files/productImg";

    @PostMapping("/saveProduct")
    fun saveProduct(
        @RequestParam productBrand : String,
        @RequestParam productName : String,
        @RequestParam productPrice : Long,
        @RequestParam productCode : String,
        @RequestParam category : String,
        @RequestParam productDescription : String,
        @RequestParam files: Array<MultipartFile>,
    ) {

        println(files)
            val dirPath = Paths.get(POST_FILE_PATH)  //경로 생성
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath)
            }   //없으면 파일 생성

            val fileList = mutableListOf<Map<String,String?>>() //변경 가능한 리스트 생성
            val imageByteArrayList = mutableListOf<ByteArray>()

            runBlocking { // 코루틴
                files.forEach { // 각각 파일 병렬
                    launch {
                        println("filename: ${it.originalFilename}")

                        val uuidFileName = buildString {
                            append(UUID.randomUUID().toString())
                            append(".")
                            append(it.originalFilename)!!.split(".").last()
                        }

                        val filePath = dirPath.resolve(uuidFileName)

                        it.inputStream.use { inputStream ->
                            val bytes = inputStream.readBytes()
                            imageByteArrayList.add(bytes)
                            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING)
                        }

                        fileList.add(mapOf("uuidFileName" to uuidFileName,
                            "contentType" to it.contentType,
                            "originalFileName" to it.originalFilename))
                    }
                }
            }
        val result = transaction {
            val p = Product
            val pf = ProductFiles

            val insertProduct = p.insert {
            it[this.productBrand] = productBrand
            it[this.productName] = productName
            it[this.productPrice] = productPrice
            it[this.productCode] = productCode
            it[this.category] = category
            it[this.productDescription] = productDescription
            }
            pf.batchInsert(fileList) {
            this[pf.productId] = insertProduct[p.id]
            this[pf.originalFileName] = it["originalFileName"] as String
            this[pf.uuidFileName] = it["uuidFileName"] as String
            this[pf.contentType] = it["contentType"] as String
            }

            val productMessageRequest = ProductMessageRequest(
                id = 1,
                productBrand = productBrand,
                productName = productName,
                productPrice = productPrice.toString(),
                productCode = productCode,
                category = category,
                productDescription = productDescription,
                imageByteArrayList = imageByteArrayList
            )
        productService.createProductMessage(productMessageRequest)
        }  // transction

    }

//    @PostMapping ("/register")
//    fun productRegisterService(@RequestBody registrationRequest : ProductInfo ){
//        productService.createProductMessage(registrationRequest)
//    }
}
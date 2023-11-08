package com.example.storeManagement.product

import com.example.storeManagement.auth.Auth
import com.example.storeManagement.auth.AuthProfile
import com.example.storeManagement.order.OrderTable
import com.example.storeManagement.product.Product.category
import com.example.storeManagement.product.Product.maximumPurchaseQuantity
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.core.io.ResourceLoader
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.ArrayList

@RestController
@RequestMapping("/product")
class ProductController(private val productService : ProductService,
                        private val resourceLoader: ResourceLoader) {
    private val POST_FILE_PATH = "files/productImg";

    @Auth
    @PostMapping("/registerProduct")   //상품 등록
    fun saveProduct(
        @RequestAttribute authProfile: AuthProfile,
        @RequestParam productBrand: String,
        @RequestParam productName: String,
        @RequestParam productPrice: Long,
        @RequestParam category: String,
        @RequestParam isActive: Boolean,
        @RequestParam productDescription: String,
        @RequestParam maximumPurchaseQuantity: Int,
        @RequestParam discountRate: Int,
        @RequestParam files: Array<MultipartFile>,
        @RequestParam mainFile: MultipartFile,
    ): ResponseEntity<RegisterResponse> {

        println(authProfile.id)

        println("이미지 파일: $mainFile")
        val dirPath = Paths.get(POST_FILE_PATH)  //경로 생성
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath)
        }   //없으면 파일 생성
        val fileList = mutableListOf<Map<String, String?>>() //변경 가능한 리스트 생성
        val uuidList = mutableListOf<String>()

//        메인 이미지 uuid처리 ------------------------------------
        val mainUuidList = mutableListOf<String>()
        val mainFileList = mutableListOf<Map<String, String?>>()

        val mainImgUuid = buildString {
            append(UUID.randomUUID().toString())  // UUID 생성
            append(".")
            append(mainFile.originalFilename).split(".").last()
        }
        mainUuidList.add(mainImgUuid!!) // 큐로 보낼 보낼 메인 uuid
        val mainFilePath = dirPath.resolve(mainImgUuid)

        mainFile.inputStream.use { inputStream ->
            Files.copy(inputStream, mainFilePath, StandardCopyOption.REPLACE_EXISTING)
        }
        mainFileList.add(
            mapOf(
                "uuidFileName" to mainImgUuid,
                "contentType" to mainFile.contentType,
                "originalFileName" to mainFile.originalFilename
            )
        )
//        --------------------------------------------------------------------
        //file처리 로직
        runBlocking { // 코루틴
            files.forEach { // 각각 파일 병렬
                launch {
                    println("filename: ${it.originalFilename}")

                    val uuidFileName = buildString {
                        append(UUID.randomUUID().toString())  //"Universally Unique Identifier" 중복방지,고유한 식별자 필요성 , 보안
                        append(".")
                        append(it.originalFilename)!!.split(".").last()
                    }

                    val filePath = dirPath.resolve(uuidFileName)

                    uuidList.add(uuidFileName!!) //보낼 uuid


                    it.inputStream.use { inputStream ->
                        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING)
                    }

                    fileList.add(
                        mapOf(
                            "uuidFileName" to uuidFileName,
                            "contentType" to it.contentType,
                            "originalFileName" to it.originalFilename
                        )
                    )
                }
            }
        }
        val combinedList = fileList + mainFileList
//       데이터베이스 처리 로직
        val result = transaction {
            val p = Product   //인터셉터해서 brand_id insert하기
            val pf = ProductFiles
            val pi = ProductInventory

            val insertProduct = p.insert {
                it[this.productName] = productName
                it[this.brand_id] = authProfile.id  //로그인한 판매자id 삽입
                it[this.productBrand] = productBrand
                it[this.productPrice] = productPrice
                it[this.category] = category
                it[this.isActive] = isActive
                it[this.maximumPurchaseQuantity] = maximumPurchaseQuantity
                it[this.discountRate] = discountRate
                it[this.productDescription] = productDescription
                it[this.mainImageUuidName] = mainImgUuid
            }
            pf.batchInsert(combinedList) {
                this[pf.productId] = insertProduct[p.id]
                this[pf.originalFileName] = it["originalFileName"] as String
                this[pf.uuidFileName] = it["uuidFileName"] as String
                this[pf.contentType] = it["contentType"] as String
            }
            pi.insert {
                it[productId] = insertProduct[p.id]
                it[quantity] = 10  //초기 수량 -> 재고등록에서 수량 등록하게끔 / 재고 미등록 상품 띄우기
                it[lastUpdated] = LocalDateTime.now()
            }

            // queue 전송 로직
            val productMessageRequest = ProductMessageRequest(
                id = insertProduct[p.id],
                productBrand = productBrand,
                productName = productName,
                productPrice = productPrice.toString(),
                isActive = isActive,
                category = category,
                maximumPurchaseQuantity = maximumPurchaseQuantity,
                discountRate = discountRate,
                productDescription = productDescription,
                mainImageUuidName = mainImgUuid,
                imageUuidName = uuidList
            )

            //상품등록 queue 전송
            println("확인----------------------$productMessageRequest")
            productService.createProductMessage(productMessageRequest)

            return@transaction RegisterResponse(
                productName = productName,
                productPrice = productPrice
            )

        }  // transction

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }


    //    @GetMapping("/inventory")
//    fun inventoryManagement(){
//        val productList = mutableListOf<Map<String,String>>()
//
//    }
    @Auth
    @GetMapping("/inventory")   //전체 재고
    fun getInventory(
        @RequestAttribute authProfile: AuthProfile,
        @RequestParam state: String,
        @RequestParam(required = false) keyword: String?,
        @RequestParam size: Int,
        @RequestParam page: Int
    ) = transaction {
        val p = Product
        val pf = ProductFiles
        val inven = ProductInventory
        println("할인")
        // 전체 제품
        val stateProducts = when (state) {
            "true" -> p.select { (p.brand_id eq authProfile.id) and (p.isActive eq true) }
            "false" -> p.select { (p.brand_id eq authProfile.id) and (p.isActive eq false) }
            "할인" -> p.select { (p.brand_id eq authProfile.id) and (p.discountRate greater 0) }
            else -> p.select { p.brand_id eq authProfile.id }
        }
        val searchProduct = if (keyword.isNullOrBlank()) {
            stateProducts
        } else {
            stateProducts.andWhere { p.productName like "%$keyword%" }
        }

        val totalCount = searchProduct.count()
//   반환할 제품 전체 데이터
        val productResponse = stateProducts
            .orderBy(p.id, SortOrder.DESC)
            .limit(size, offset = (size * page).toLong())
            .map { r ->

                val productId = r[p.id]

                // 제품 이미지 DB에서 productID와 일치하는 애들만 추리기
                val productFiles = (p innerJoin pf).select { (pf.productId eq productId) and (pf.uuidFileName eq p.mainImageUuidName) }.map { r ->
                    ProductFileResponse(
                        id = r[pf.id].value,
                        productId = productId,
                        uuidFileName = r[pf.uuidFileName],
                        originalFileName = r[pf.originalFileName],
                        contentType = r[pf.contentType]
                    )
                }
                val productInfo = inven.select { inven.productId eq productId }.map { r ->
                    ProductInventoryResponse(
                        quantity = r[inven.quantity],
                        lastUpdated = r[inven.lastUpdated].toString(),
                    )
                }
                InventoryResponse(
                    id = r[p.id],
                    productBrand = r[p.productBrand],
                    productName = r[p.productName],
                    productPrice = r[p.productPrice].toString(),
                    isActive = r[p.isActive],
                    category = r[p.category],
                    maximumPurchaseQuantity = r[p.maximumPurchaseQuantity],
                    productDescription = r[p.productDescription],
                    discountRate = r[p.discountRate],
                    files = productFiles,
                    productInfo = productInfo
                )

            }
        val response = PageImpl(productResponse, PageRequest.of(page, size), totalCount)
        return@transaction response
    }


    @GetMapping("/files/{uuidFilename}")  //이미지/동영상 요청
    fun downloadFile(@PathVariable uuidFilename: String): ResponseEntity<Any> {
        val file = Paths.get("$POST_FILE_PATH/$uuidFilename").toFile()
        if (!file.exists()) {
            return ResponseEntity.notFound().build()
        }

        val mimeType = Files.probeContentType(file.toPath())
        val mediaType = MediaType.parseMediaType(mimeType)

        val resource = resourceLoader.getResource("file:$file")
        return ResponseEntity.ok()
            .contentType(mediaType) // video/mp4, image/png, image/jpeg
            .body(resource)
    }

    @Auth
    @PutMapping("/modifyProduct")
    fun modifyInventory(
        @RequestParam id: Long,
        @RequestBody req: ModifyProduct
    ) {
        println(id)
        println(req)
        val p = Product
        val pi = ProductInventory

        transaction {
            p.update({ p.id eq id }) {
                it[p.productName] = req.productName
                it[p.isActive] = req.isActive.toBoolean()
                it[p.productPrice] = req.productPrice.toLong()
                it[p.maximumPurchaseQuantity] = req.maximumPurchaseQuantity.toInt()
                it[p.discountRate] = req.discountRate.toInt()
                it[p.category] = req.category
            }

            pi.update({ pi.productId eq id }) {
                it[pi.quantity] = req.quantity.toInt()
            }
        val productModifyMessageRequest = ProductMessageRequest (
            id = id,
            productBrand = "",
            productName = req.productName,
            productPrice = req.productPrice,
            isActive = req.isActive.toBoolean(),
            category = req.category,
            maximumPurchaseQuantity =  req.maximumPurchaseQuantity.toInt(),
            discountRate = req.discountRate.toInt(),
            productDescription = "",
            mainImageUuidName = "",
            imageUuidName = listOf(),
        )

        productService.createProductMessage(productModifyMessageRequest)
        }
    }

    @GetMapping("/topFiveProduct")
    fun getTopFiveProduct() = transaction {
        val pto = ProductTotalOrder

        val topFiveByCategoryList = mutableListOf<TopFavoriteProduct>()

        val pInnerJoinOrder = (Product innerJoin OrderTable)

        val topFiveProduct = pto.selectAll()
            .groupBy { it[pto.category] }
            .map { (category, categoryOrders) ->
                val topOrders = categoryOrders.sortedByDescending { it[pto.totalOrder] }
                    .take(5)
                    .map { it[pto.productId].toLong() }
                topFiveByCategoryList.add(TopFavoriteProduct(ids = topOrders, category = category))
            }
        println(topFiveProduct)
        return@transaction topFiveByCategoryList
    }


    @Auth
    @GetMapping("lessQuantity")
    fun getLessQuantity(@RequestAttribute authProfile: AuthProfile): List<Pair<String, Int>> = transaction{
        val p = Product
        val pi = ProductInventory
        val findProduct = p.select { p.brand_id eq authProfile.id }.map { it[p.id] }

        val lessQuantityProducts = findProduct.flatMap { productId ->
            pi.select { (pi.productId eq productId) and (pi.quantity less 4) }.map {
                Pair(
                    p.select { p.id eq productId }.single()[p.productName],
                    it[pi.quantity]
                )
            }
        }

        println("check-----------------------$lessQuantityProducts")
        return@transaction lessQuantityProducts
    }


}  // 끝

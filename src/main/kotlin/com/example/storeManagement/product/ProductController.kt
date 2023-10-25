package com.example.storeManagement.product

import com.example.storeManagement.auth.Auth
import com.example.storeManagement.auth.AuthProfile
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

@RestController
@RequestMapping("/product")
class ProductController(private val productService : ProductService,
                        private val resourceLoader: ResourceLoader) {
    private val POST_FILE_PATH = "files/productImg";

    @Auth
    @PostMapping("/registerProduct")   //상품 등록
    fun saveProduct(
            @RequestAttribute authProfile: AuthProfile,
            @RequestParam productBrand : String,
            @RequestParam productName : String,
            @RequestParam productPrice : Long,
            @RequestParam category : String,
            @RequestParam isActive : Boolean,
            @RequestParam productDescription : String,
            @RequestParam files: Array<MultipartFile>,
    ):ResponseEntity<RegisterResponse> {

        println(authProfile.id)
        println("productBrand :$productBrand")
        println("productName : $productName")
        println("productPrice : $productPrice")
        println("category : $category")
        println("isActive : $isActive")
        println("productDescription : $productDescription")
        println("이미지 파일: $files")
            val dirPath = Paths.get(POST_FILE_PATH)  //경로 생성
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath)
            }   //없으면 파일 생성

            val fileList = mutableListOf<Map<String,String?>>() //변경 가능한 리스트 생성
            val uuidList = mutableListOf<String>()
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

                        fileList.add(mapOf("uuidFileName" to uuidFileName,
                            "contentType" to it.contentType,
                            "originalFileName" to it.originalFilename))
                    }
                }
            }

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
            it[this.productDescription] = productDescription
            }
            pf.batchInsert(fileList) {
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
                productDescription = productDescription,
                imageUuidName = uuidList
            )

            //상품등록 queue 전송
            println("확인----------------------$productMessageRequest" )
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
fun getInventory(@RequestAttribute authProfile: AuthProfile,
                 @RequestParam state : String,
                 @RequestParam(required = false) keyword: String?,
                 @RequestParam size: Int,
                 @RequestParam page: Int
) = transaction {
    val p = Product
    val pf = ProductFiles
    val inven = ProductInventory

    // 전체 제품
    val stateProducts = when (state) {
        "true" -> p.select { (p.brand_id eq authProfile.id) and (p.isActive eq true) }
        "false" -> p.select { (p.brand_id eq authProfile.id) and (p.isActive eq false) }
        else -> p.select { p.brand_id eq authProfile.id }
    }
    val searchProduct = if (keyword.isNullOrBlank()){
        stateProducts
    }else{
        stateProducts.andWhere { p.productName like "%$keyword%" }
    }

    val totalCount = searchProduct.count()
//   반환할 제품 전체 데이터
    val productResponse = stateProducts
        .orderBy(p.id,SortOrder.DESC)
        .limit(size, offset= (size * page).toLong())
        .map { r ->

            val productId = r[p.id]

            // 제품 이미지 DB에서 productID와 일치하는 애들만 추리기
            val productFiles = pf.select { pf.productId eq productId }.map { r ->
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
                productDescription = r[p.productDescription],
                files = productFiles,
                productInfo = productInfo
            )

        }
    val response = PageImpl(productResponse, PageRequest.of(page, size), totalCount)
    return@transaction response
}
    @GetMapping("/files/{uuidFilename}")  //이미지/동영상 요청
    fun downloadFile(@PathVariable uuidFilename : String) : ResponseEntity<Any> {
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

    @PutMapping("/modifyProduct")
    fun modifyInventory(@RequestParam id : Long,
            @RequestBody req : ModifyProduct){
        println(id)
        println(req)
        val p = Product
        val pi = ProductInventory
        transaction {
            p.update({ p.id eq id }) {
                it[p.productName] = req.productName
                it[p.isActive] = req.isActive.toBoolean()
                it[p.productPrice] = req.productPrice.toLong()
            }
        }


    }
}  // 끝

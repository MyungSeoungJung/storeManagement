package com.example.storeManagement.product

import com.example.storeManagement.auth.Auth
import com.example.storeManagement.auth.AuthProfile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.core.io.ResourceLoader
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
            val imageByteArrayList = mutableListOf<ByteArray>()
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

                        it.inputStream.use { inputStream ->
                            val bytes = inputStream.readBytes()

                            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING)
                            imageByteArrayList.add(bytes) // 이미지 파일의 바이트 배열을 리스트에 추가
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
                id = 1,   //인터셉터 들어오면 인터셉터된 profile의 id 삽입
                productBrand = productBrand,
                productName = productName,
                productPrice = productPrice.toString(),
                isActive = isActive,
                category = category,
                productDescription = productDescription,
                imageByteArrayList = imageByteArrayList
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
    fun getInventory(@RequestAttribute authProfile: AuthProfile) = transaction {
    val p = Product
    val pf = ProductFiles
    val inven = ProductInventory

    // 일치하는 제품을 선택
    val selectedProducts = p.select { p.brand_id eq authProfile.id }.toList()
    
//   반환할 제품 전체 데이터
    val productResponse = selectedProducts.map { r ->
        val productId = r[p.id]

        // 제품 이미지 DB에서 productID와 일치하는 애들만 추리기
        val productFiles = pf.select { pf.productId eq productId }.map { fileRow ->
            ProductFileResponse(
                    id = fileRow[pf.id].value,
                    postId = productId,
                    uuidFileName = fileRow[pf.uuidFileName],
                    originalFileName = fileRow[pf.originalFileName],
                    contentType = fileRow[pf.contentType]
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

    return@transaction productResponse
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



}  // 끝



//data class ProductInfo(
//        var id : Long,
//        var brand : String,
//        var product: List<ProductWithFiles>,
//)

//data class Product(
//        var id : Long,
//        var productName : String,
//        var productPrice : Long,
//        var productCode : String,
//        var majorCategory : String,
//        var subCategory : String,
//        var productDescription : String,
//)

//data class ProductFile(
//        val id : Long,
//        val productId : Long,
//        var uuidFileName : String,
//        val originalFileName : String,
//        val contentType: String,
//)
//
//data class ProductWithFiles(
//        var id : Long,
//        var productName : String,
//        var productPrice : Long,
//        var productCode : String,
//        var category : String,
//        var productDescription : String,
//        val file: List<ProductFile>
//)
data class ProductMessageRequest(
        val id: Long,
        val productBrand: String,
        val productName: String,
        val productPrice: String,
        val isActive : Boolean,
        val category: String,
        val productDescription: String,
        val imageByteArrayList: MutableList<ByteArray>
)
data class RegisterResponse(
        val productName : String,
        val productPrice : Long,
)


//data class ImageInfo(
//        val id: Long,
//        val imageData: ByteArray
//)

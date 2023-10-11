
data class ProductInfo(
        var id : Long,
        var brand : String,
        var product: List<ProductWithFiles>,
)

//data class Product(
//        var id : Long,
//        var productName : String,
//        var productPrice : Long,
//        var productCode : String,
//        var majorCategory : String,
//        var subCategory : String,
//        var productDescription : String,
//)

data class ProductFile(
        val id : Long,
        val productId : Long,
        var uuidFileName : String,
        val originalFileName : String,
        val contentType: String,
)

data class ProductWithFiles(
        var id : Long,
        var productName : String,
        var productPrice : Long,
        var productCode : String,
        var category : String,
        var productDescription : String,
        val file: List<ProductFile>
)
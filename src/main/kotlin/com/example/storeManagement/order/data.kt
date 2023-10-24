data class OrderRequest (
    val userId : Long,
    val orderId: Long,
    val productId: Long,
    val quantity: Int,
    val address:String,
)

data class OrderResultResponse (
    val orderId : Long,
    val isPermission : String,
)
// 상품 데이터 클래스 ------------------------------------------------------------------------
data class ProductInfo (
    val productId: Long,
    val productName : String,
)
data class ProductFile(
    var uuidFileName : String,
    val originalFileName : String,
    val contentType: String,
)
data class ProductInfoAndFile(
    val productId: Long,
    val productName : String,
    var uuidFileName : String,
    val originalFileName : String,
    val contentType: String,
)

// 주문 데이터 클래스 ---------------------------------------------------------------------
data class OrderInfo (
    val orderId : Long,
    val quantity : Int,
    val orderDate : String
)
data class OrderCondition (   // orderState
    val orderState : Boolean
)

data class OrderDetailsResponse  (
    val orderInfo: List<OrderStateAndInfo>,
    val productInfo : List<ProductInfoAndFile>?,

)
data class OrderStateAndInfo(  // orderState랑 orderTable 조인 데이터 클래스
    val orderId: Long,
    val orderStatus: Boolean,
    val quantity: Int,
    val orderDate: String
)
// 주문 데이터 클래스 ---------------------------------------------------------------------

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

data class ProductInfo (
    val productId: Long,
    val productName : String,
)
data class OrderStateAndTable(
    val orderId: Long,
    val orderStatus: String, // 실제 데이터 유형과 일치하도록 조정
    val quantity: Int, // 데이터에 따라 유형을 조정
    val orderDate: String // 데이터에 따라 유형을 조정
)
data class OrderInfo (
    val orderId : Long,
    val quantity : Int,
    val orderDate : String
)
data class OrderCondition (
    val orderState : String
)

data class OrderDetails  (
    val productInfo: List<ProductInfo>,
    val orderInfo: List<OrderInfo>,
    val orderState : List<OrderCondition>,
)


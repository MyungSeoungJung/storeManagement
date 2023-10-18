data class OrderRequest (
    val userId : Long,
    val orderId: Long,
    val productId: Long,
    val quantity: Long,
    val address:String,
)

data class OrderResultResponse (
    val orderId : Long,
    val isPermission : String,
)
package com.example.storeManagement.product

// 인벤토리
data class InventoryResponse (
        val id : Long,
        val productBrand: String,
        val productName: String,
        val productPrice: String,
        val isActive : Boolean,
        val category: String,
        val productDescription: String,
        val maximumPurchaseQuantity : Int,
        val discountRate : Int,
        val files: List<ProductFileResponse>,
        val productInfo : List<ProductInventoryResponse>
)
data class ProductFileResponse(
        val id : Long,
        val productId : Long,
        var uuidFileName : String,
        val originalFileName : String,
        val contentType: String,
)
data class ProductInventoryResponse(
        val quantity : Int,
        val lastUpdated : String,
)

// 제품
data class ProductMessageRequest(
        val id: Long,
        val productBrand: String,
        val productName: String,
        val productPrice: String,
        val isActive : Boolean,
        val category: String,
        val maximumPurchaseQuantity : Int,
        val discountRate : Int,
        val productDescription: String,
        val mainImageUuidName : String,
        val imageUuidName : List<String>
)
data class RegisterResponse(
        val productName : String,
        val productPrice : Long,
)
data class ModifyProduct (
        val isActive : String,
        val productName : String,
        val quantity : String,
        val productPrice : String,
        val maximumPurchaseQuantity : String,
        val discountRate : String,
        val category: String,
)
data class TopFavoriteProduct(
        val ids:List<Long>,
        val category:String
)




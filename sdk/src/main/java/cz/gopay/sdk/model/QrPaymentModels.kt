package cz.gopay.sdk.model

import com.squareup.moshi.Json

enum class QrCodeFormat {
    @Json(name = "png")
    PNG,
    @Json(name = "svg")
    SVG
}

data class BankAccountLocalDetails(
    val prefix: String,
    @Json(name = "account_number")
    val accountNumber: String,
    @Json(name = "bank_code")
    val bankCode: String,
    @Json(name = "variable_symbol")
    val variableSymbol: String
)

data class BankAccountInternationalDetails(
    val bic: String? = null,
    val iban: String? = null,
    val reference: String? = null
)

data class RecipientBankAccount(
    val local: BankAccountLocalDetails? = null,
    val international: BankAccountInternationalDetails? = null
)

data class RecipientAddress(
    val street: String? = null,
    val city: String? = null,
    @Json(name = "zip_code")
    val zipCode: String? = null,
    val country: String? = null
)

data class BankTransferRecipient(
    val name: String? = null,
    @Json(name = "bank_account")
    val bankAccount: RecipientBankAccount? = null,
    val address: RecipientAddress? = null
)

data class QrCodeList(
    val spayd: String? = null,
    val paybysquare: String? = null,
    val sepa: String? = null,
    @Json(name = "mnb_qr")
    val mnbQr: String? = null
)

data class QrPaymentDetails(
    val amount: Long,
    val currency: Currency,
    val recipient: BankTransferRecipient,
    @Json(name = "qr_code")
    val qrCode: QrCodeList
)

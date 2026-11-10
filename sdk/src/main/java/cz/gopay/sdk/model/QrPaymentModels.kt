package cz.gopay.sdk.model

import com.squareup.moshi.Json

enum class QrCodeFormat {
    @Json(name = "png")
    PNG,
    @Json(name = "svg")
    SVG
}

/**
 * The recipient's account as the local banks write it. Every field is always present.
 * `QrLocalBankAccount` on iOS too; 2.x called it `BankAccountLocalDetails` here.
 */
data class QrLocalBankAccount(
    val prefix: String,
    @Json(name = "account_number")
    val accountNumber: String,
    @Json(name = "bank_code")
    val bankCode: String,
    @Json(name = "variable_symbol")
    val variableSymbol: String
)

/**
 * The recipient's account in its international form. `QrInternationalBankAccount` on iOS too; 2.x
 * called it `BankAccountInternationalDetails` here.
 */
data class QrInternationalBankAccount(
    val bic: String? = null,
    val iban: String? = null,
    val reference: String? = null
)

/**
 * The account of a QR payment's recipient, local and international. `QrRecipientBankAccount` on
 * iOS too; 2.x called it `RecipientBankAccount` here.
 */
data class QrRecipientBankAccount(
    val local: QrLocalBankAccount? = null,
    val international: QrInternationalBankAccount? = null
)

/**
 * The postal address of a QR payment's recipient. `QrRecipientAddress` on iOS too; 2.x called it
 * `RecipientAddress` here.
 */
data class QrRecipientAddress(
    val street: String? = null,
    val city: String? = null,
    @Json(name = "zip_code")
    val zipCode: String? = null,
    val country: String? = null
)

/**
 * Who a QR payment, a bank transfer, is paid to. `QrRecipient` on iOS too; 2.x called it
 * `BankTransferRecipient` here.
 */
data class QrRecipient(
    val name: String? = null,
    @Json(name = "bank_account")
    val bankAccount: QrRecipientBankAccount? = null,
    val address: QrRecipientAddress? = null
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
    val recipient: QrRecipient,
    @Json(name = "qr_code")
    val qrCode: QrCodeList
)

/** The 2.x name of [QrLocalBankAccount]. */
@Deprecated("Renamed to QrLocalBankAccount, the name the iOS SDK uses", ReplaceWith("QrLocalBankAccount"))
typealias BankAccountLocalDetails = QrLocalBankAccount

/** The 2.x name of [QrInternationalBankAccount]. */
@Deprecated("Renamed to QrInternationalBankAccount, the name the iOS SDK uses", ReplaceWith("QrInternationalBankAccount"))
typealias BankAccountInternationalDetails = QrInternationalBankAccount

/** The 2.x name of [QrRecipientBankAccount]. */
@Deprecated("Renamed to QrRecipientBankAccount, the name the iOS SDK uses", ReplaceWith("QrRecipientBankAccount"))
typealias RecipientBankAccount = QrRecipientBankAccount

/** The 2.x name of [QrRecipientAddress]. */
@Deprecated("Renamed to QrRecipientAddress, the name the iOS SDK uses", ReplaceWith("QrRecipientAddress"))
typealias RecipientAddress = QrRecipientAddress

/** The 2.x name of [QrRecipient]. */
@Deprecated("Renamed to QrRecipient, the name the iOS SDK uses", ReplaceWith("QrRecipient"))
typealias BankTransferRecipient = QrRecipient

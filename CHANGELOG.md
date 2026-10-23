## 2.2.1 (2026-10-22)


### Bug Fixes

* keep the demo console clear of the system bars GPMOB-143 ac38e6f
* keep the demo's pay buttons above the keyboard GPMOB-143 9a56e15
* name the released version in the README install GPMOB-143 543c694

# 2.2.0 (2026-10-21)


### Bug Fixes

* clear the PR's SonarCloud findings GPMOB-160 2ff25e2
* report each SDK error to errorCallback once GPMOB-160 904d131
* require browserData in the card helpers without an activity GPMOB-160 670a56a
* spell out the nullable types Sonar misreads GPMOB-160 04d1445
* throw the shared error codes instead of platform exceptions GPMOB-160 4fca36a


### Features

* add the card, wallet and form API shared with Android/iOS GPMOB-160 29ad330
* use the shared API in the demo GPMOB-160 b704fcc

# 2.1.0 (2026-10-20)


### Features

* await the charge in the demo checkout and console GPMOB-159 73cf758
* wait for the charge with awaitChargeState GPMOB-159 5376666

# 2.0.0 (2026-10-20)

A major release with a new error contract, a rebuilt card form theme and a changed 3DS flow; see [Migrating from 1.x](README.md#migrating-from-1x) in the README before upgrading.


### Bug Fixes

* ask for the Google Pay sheet only once across a rotation GPMOB-157 3a3972d
* charge 1 Kč in the demo checkout GPMOB-157 6058d16
* clear card form state on dispose and mask card data in logs GPMOB-140 420820c
* close the 3DS challenge on the payment's own return URL GPMOB-149 3e79e15
* default the card form to the platform's own appearance GPMOB-145 32b801e
* drop returnUrl from the charge request GPMOB-149 2367f89
* fetch the browser data the gateway now requires before a charge GPMOB-149 9b39e22
* give both dark demo themes a visible placeholder GPMOB-145 9919a6e
* give network failures SDK codes GPMOB-157 e82b0f3
* give the demo host a night theme GPMOB-145 954c0cc
* handle the lengths and widths a theme can state GPMOB-145 3982bc1
* keep payment credentials and tokens out of the debug log GPMOB-157 b3cd8f3
* keep the 3DS challenge clear of the system bars and the keyboard GPMOB-157 e09e2ed
* keep the card form out of screenshots in sheets and dialogs too GPMOB-157 63e03d2
* keep the expiry and CVV inputs on one line GPMOB-145 40eb14a
* keep the session auth interceptor from crashing the app GPMOB-156 18c0eca
* leave the cleartext policy to the host and declare INTERNET GPMOB-157 72d1f70
* let a 3DS challenge hand off to a banking app GPMOB-149 a3362b1
* mask payment_secret in the debug log GPMOB-157 9f88050
* name built-in gateways in the demo badge and charge 1 CZK GPMOB-146 63164ba
* point Environment constants at the Payments 4.0 gateways GPMOB-146 ec46db1
* report an unreachable 3DS challenge as PAYMENT_010 GPMOB-149 3bcf30a
* require fresh card entry on checkout retry GPMOB-140 6fd9928
* send the WebView user agent in browser_data GPMOB-149 89f1e9a
* ship R8 rules so a minified host can read the gateway GPMOB-156 62201a9
* size the label from Material's own scale GPMOB-145 e81ba2b
* stamp the card JWE with the gateway's time GPMOB-155 b9f3945
* take the 3DS return URL from the backend, not the charge response GPMOB-157 5ef4595
* take the entered text's and the cursor's color from the host theme when the form theme sets none GPMOB-145 cbb0351
* type the Google Pay failures GPMOB-157 bb9ce02
* use an https return URL for the 3DS challenge GPMOB-149 945ad4c
* wrap an unreadable response body as an SDK error GPMOB-149 afdc0d0


### Code Refactoring

* drop the public key API and hide card encryption GPMOB-155 b528c6e


### Features

* add demo checkout page GPMOB-139 88eaff9
* configure the demo app from a gitignored local.properties GPMOB-146 5f063f3
* keep the gateway's error body on HttpErrorContext GPMOB-157 e5c4ebe
* make ChargePaymentResponse.returnUrl optional GPMOB-149 e9f70bb
* read gateway values the SDK does not know as UNKNOWN GPMOB-156 9e9cdd2
* rebuild the card form theme around the cc-v4 parameter set GPMOB-145 849ee23
* render the typography and input text theme params GPMOB-145 118d1c2
* report a closed payment window as PAYMENT_011 GPMOB-157 0473a56
* warn when the host theme fights the system's dark mode GPMOB-145 0c651e8
* watch a charge for its 3DS action in the demo console GPMOB-149 2168ec8


### BREAKING CHANGES

* closing the wallet sheet or the 3DS screen throws PAYMENT_011; a cancellation comes only from the caller cancelling
* Google Pay failures are GopaySDKException PAYMENT_003/004 instead of PAYMENT_009
* transport failures are GopaySDKException NETWORK_001/004/005/007, a 5xx NETWORK_003 instead of NETWORK_002, AUTH_010 only on HTTP 400/401/403, an unexpected token exchange failure INTERNAL_001 and an empty 2xx INTERNAL_003
* response enums gain an unknown case for values the gateway adds later
* getPublicEncryptionKey removed; encryption utilities and key models are internal
* ChargePaymentRequest no longer takes returnUrl
* ChargePaymentResponse.returnUrl is now String? instead of String; code that reads it has to handle null, which is what the gateway has been sending. iOS carries the same public shape.
* PaymentCardFormTheme is rebuilt around cc-v4 params, see README migration table.

# 1.11.0 (2026-07-28)


### Features

* fill browser data on charge GPMOB-124 59337f6

# 1.10.0 (2026-07-24)


### Bug Fixes

* failing tests f40e1ce


### Features

* added form locales and localization logic GPMOB-126 5f37d81
* charge with JWE payload GPMOB-135 7b0ded8
* JWE structure update GPMOB-135 4920fe3

# 1.9.0 (2026-06-11)


### Features

* 3ds verification handling GPMOB-69 adef45c
* add create payment api method GPMOB-119 f45af1a
* add getQrPaymentInfo for bank transfer QR codes GPMOB-130 019f859
* add Google Pay support — getGooglePayInfo and charge instrument GPMOB-131 adb769f
* example app redesign cb5b214

## 1.8.2 (2026-06-05)


### Bug Fixes

* increase docker memory for sonar GPOMA-2292 ad99604

## 1.8.1 (2025-12-09)


### Bug Fixes

* import fix after renaming the package GPMOB-108 14cd7f2

# 1.8.0 (2025-12-02)


### Features

* add user-agent interceptor to network module GPMOB-110 47803e5

# 1.7.0 (2025-12-01)


### Features

* reset card form after submit GPMOB-112 71217d4

# 1.6.0 (2025-08-01)


### Features

* add custom development environment option on sdk init GPMOB-111 fad7a53
* add debug mode to sdk init GPMOB-111 4b0c0b0
* add secure_flag to payment form GPMOB-111 a5d72d0

# 1.5.0 (2025-07-07)


### Features

* card form validations and formatters GPMOB-67 d0ebfd1
* PaymentCard form GPMOB-67 27bf103
* update readme GPMOB-67 4243eb0

# 1.4.0 (2025-07-01)


### Bug Fixes

* Card data json snake-case properties 5b323a5
* card tokenization response optional fields 0451c32


### Features

* add public key api call 755b85e
* jwe data encryption and card tokenization 18d23e0
* public key f9d81ba
* public key api method GPMOB-60 52f3803
* SDK public facing interface 0eda768

# 1.3.0 (2025-06-26)


### Bug Fixes

* base64 android sdk 24 issue with reflection 2db601f
* reference error 477ea4a
* tokenstorage cypher issues 77b2f5b


### Features

* add authentication endpoint ceee6e3
* add token storage and tests bcd495b
* add unified exception handling e21e189
* example app demo space d61f005
* introduce encryption to token storage 1a23343
* jtw token handling faead8e

# 1.2.0 (2025-05-20)


### Features

* add network module GPMOB-55 79c11b3

# 1.1.0 (2025-04-30)


### Features

*  add GopaySDK configuration GPMOB-53 4a87544

# 1.0.0 (2025-04-15)


### Bug Fixes

* deprecated methods bccd3d0
* sonar issues 400e23e


### Features

* Initial commit - GPMOB-9 a06a5a9

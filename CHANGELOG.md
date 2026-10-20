# [2.0.0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/compare/1.11.0...2.0.0) (2026-10-20)


* feat!: report a closed payment window as PAYMENT_011 GPMOB-157 ([0473a56](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/0473a5674e8fabd232f56f006f57d1ed03ac6321))
* fix!: give network failures SDK codes GPMOB-157 ([e82b0f3](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/e82b0f3e57afb926cdc655ae94c822d13e136f7e))
* fix!: type the Google Pay failures GPMOB-157 ([bb9ce02](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/bb9ce02363901cbc80d0fd1b6acc64be6db5abb3))


### Bug Fixes

* analyze commits, set the version and release by hand GPMOB-153 ([5c1ad14](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/5c1ad14ab04ddf0d5eeb3dae00943326d6b16e8a))
* ask for the Google Pay sheet only once across a rotation GPMOB-157 ([3a3972d](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/3a3972d019b159c4185f084837f4ec404eaf7bd2))
* charge 1 Kč in the demo checkout GPMOB-157 ([6058d16](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/6058d16cd0841b6871ced64b0ad15efc51982495))
* clear card form state on dispose and mask card data in logs GPMOB-140 ([420820c](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/420820cff260f9ed4bf81e5b3c4af6604898ec71))
* clear the PR's SonarCloud findings GPMOB-157 ([a03a063](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/a03a063d358b0a48a202f4af4913741600053fe2))
* close the 3DS challenge on the payment's own return URL GPMOB-149 ([3e79e15](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/3e79e15d7aba0f86decd977b4d0e1ad7150ada42))
* drop returnUrl from the charge request GPMOB-149 ([2367f89](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/2367f89a32ccd552b703cc61b446ee85a7908973))
* fetch the browser data the gateway now requires before a charge GPMOB-149 ([9b39e22](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/9b39e22a34e38993ba12c884403f87cbe69b1dae))
* give both dark demo themes a visible placeholder GPMOB-145 ([9919a6e](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/9919a6e21e56751fb3e1c27935705f1b10c0a4ec))
* give the demo host a night theme GPMOB-145 ([954c0cc](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/954c0cc90952990708dc587976d662d46b235fbc))
* handle the lengths and widths a theme can state GPMOB-145 ([3982bc1](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/3982bc1911f0ffe40d260815114afa6a49fa6339))
* keep payment credentials and tokens out of the debug log GPMOB-157 ([b3cd8f3](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/b3cd8f30a31501a2354ccb413a664cb169445a0a))
* keep the 3DS challenge clear of the system bars and the keyboard GPMOB-157 ([e09e2ed](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/e09e2ed2aa42558fb40166a499a8dd1e91cdb6ab))
* keep the card form out of screenshots in sheets and dialogs too GPMOB-157 ([63e03d2](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/63e03d219f9fb9385850ee7d6241a0cd45ff76e3))
* keep the expiry and CVV inputs on one line GPMOB-145 ([40eb14a](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/40eb14abc81c29e5e7019b660b4265851c4953b0))
* keep the session auth interceptor from crashing the app GPMOB-156 ([18c0eca](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/18c0ecad4aa4f722a8eeb4307c33ddaec41d779b))
* leave the cleartext policy to the host and declare INTERNET GPMOB-157 ([72d1f70](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/72d1f708b79122bad58d1ec425265d4a0c4b18bd))
* let a 3DS challenge hand off to a banking app GPMOB-149 ([a3362b1](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/a3362b127f427cef62ffcf3a5159b0ef32bd1eff))
* mask payment_secret in the debug log GPMOB-157 ([9f88050](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/9f880504f653ba1344f9daf66e63fbb55e1cdec4))
* name built-in gateways in the demo badge and charge 1 CZK GPMOB-146 ([63164ba](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/63164ba4e48090ec75e027dcd1019b323f555ac1))
* never let focus make the border thinner than at rest GPMOB-145 ([e87c01b](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/e87c01b71c5a4444ab84fb9671dc955c6f7ef184))
* pad the demo documents inside the field GPMOB-145 ([6e6812f](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/6e6812f9f5168ffc13bf562d2332dc1d1949d5d5))
* point Environment constants at the Payments 4.0 gateways GPMOB-146 ([ec46db1](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/ec46db1601e1c8c83b595ed1a0f3c8f6625f0ca0))
* reject a font weight a document states outside the CSS range GPMOB-145 ([43838b8](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/43838b8a4c76562848664a336a86410eb30b6f5d))
* report an unreachable 3DS challenge as PAYMENT_010 GPMOB-149 ([3bcf30a](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/3bcf30a0dd68d38e6a9e7cb20fe50d686385ea3b))
* report the theme keys this platform cannot render GPMOB-145 ([a180ac6](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/a180ac699ed275b71aeb3639e5bffb85d2a42162))
* require fresh card entry on checkout retry GPMOB-140 ([6fd9928](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/6fd992878805b306196fdbf76567b71d750d3a90))
* restore the expiry labels GoPay translates GPMOB-145 ([1b7e104](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/1b7e104b1d80df03065a85410f699b4e4a244266))
* send the WebView user agent in browser_data GPMOB-149 ([89f1e9a](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/89f1e9a4eb1fac4d60850122b04a688f3d253b80))
* ship R8 rules so a minified host can read the gateway GPMOB-156 ([62201a9](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/62201a9dda43e87917a426aa2595e562a50d67f2))
* shorten the long expiry labels so the row stays aligned GPMOB-145 ([af354ec](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/af354eca5158943177350334a9e88bb210f14b01))
* size the label from Material's own scale GPMOB-145 ([e81ba2b](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/e81ba2ba5faf2100d96ccc373e6100fcaa64e09d))
* stamp the card JWE with the gateway's time GPMOB-155 ([b9f3945](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/b9f394564c3efcf9c0127ba92a7c1c5712198710))
* stretch the underline across the whole field GPMOB-145 ([71a916e](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/71a916e805ff1de102c1df7fea2674eae72163bc))
* take the 3DS return URL from the backend, not the charge response GPMOB-157 ([5ef4595](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/5ef4595e68b785f1af2e1d6e013077aa7ad3631b))
* take the text, cursor and selection colors from the field GPMOB-145 ([cbb0351](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/cbb03510c29853b0943340c19ca1df31e2ae924d))
* use an https return URL for the 3DS challenge GPMOB-149 ([945ad4c](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/945ad4cde887dbb961cb8b655fb686481d19d1b6))
* wrap an unreadable response body as an SDK error GPMOB-149 ([afdc0d0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/afdc0d0ba6abaf6dbcdb4567547762d8457d8662))


### Code Refactoring

* drop inputBorderStyle from the theme GPMOB-145 ([3e1f05f](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/3e1f05fa7780311615793b04d7be157673654adc))
* drop the JSON theme document GPMOB-145 ([e81d2db](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/e81d2dbe6a9560e6fb758998227df8aa3cc5fb67))
* drop the public key API and hide card encryption GPMOB-155 ([b528c6e](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/b528c6e37d580cd842a1e6c93276ea54566f80e2))


### Features

* add demo checkout page GPMOB-139 ([88eaff9](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/88eaff95621c3fd2c0d5bd96399db137985e84d3))
* add the underline border style with a focus gradient GPMOB-145 ([1e07c8f](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/1e07c8f362284e83f2f5e70d48b250bed808c1e0))
* build the input on Material's own text field decoration GPMOB-145 ([3b4d5e1](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/3b4d5e143090410ab862ec4c4913b8d53aef544f))
* default the border style to BOXED GPMOB-145 ([10d2faa](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/10d2faad972743a22f59307ef9432f0bf7f09337))
* keep the gateway's error body on HttpErrorContext GPMOB-157 ([e5c4ebe](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/e5c4ebe6be3a9059e27affdca26237e7247794d2))
* make ChargePaymentResponse.returnUrl optional GPMOB-149 ([e9f70bb](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/e9f70bbd46f024ce353583d680ebfbb5546454bf))
* point the demo app at any gateway without a rebuild GPMOB-146 ([461e221](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/461e221c9052956ea6e4587de18e90f801cd4fc6))
* read gateway values the SDK does not know as UNKNOWN GPMOB-156 ([9e9cdd2](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/9e9cdd2d116cd68e4832ff33b768581f68cf874e))
* rebuild the card form theme around the cc-v4 parameter set GPMOB-145 ([849ee23](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/849ee2369567a33392a90bb9c0c1566a84d7ca59))
* render the typography and input text theme params GPMOB-145 ([118d1c2](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/118d1c2358ef1e2c740753787bad2e85012c0c41))
* run the demo app from a gitignored .env GPMOB-146 ([8b923c9](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/8b923c9bd516d59a165a874b391820cdf95b9ccb))
* support collapsed input borders as one block GPMOB-145 ([c13baa9](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/c13baa929a405cb3785c4ac358557f8021ef5e00))
* theme the demo card form from JSON documents GPMOB-145 ([901cf6f](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/901cf6f09c65361ab4a57a68bf3456cd4b5c648c))
* unify focus and error border semantics GPMOB-145 ([4684ef9](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/4684ef9db0c16445f0602e8f3d3942ef604f87d1))
* warn when the host theme fights the system's dark mode GPMOB-145 ([0c651e8](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/0c651e8f97b8a17bef203e65e78728649ddb4644))
* watch a charge for its 3DS action in the demo console GPMOB-149 ([2168ec8](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/2168ec898588aaa63761b331034a9e4bc0767c4e))


### BREAKING CHANGES

* closing the wallet sheet or the 3DS screen throws PAYMENT_011; a cancellation comes only from the caller cancelling
* Google Pay failures are GopaySDKException PAYMENT_003/004 instead of PAYMENT_009
* transport failures are GopaySDKException NETWORK_001/004/005/007, a 5xx NETWORK_003 instead of NETWORK_002, AUTH_010 only on HTTP 400/401/403, an unexpected token exchange failure INTERNAL_001 and an empty 2xx INTERNAL_003
* response enums gain an unknown case for values the gateway adds later
* getPublicEncryptionKey removed; encryption utilities and key models are internal
* ChargePaymentRequest no longer takes returnUrl
* ChargePaymentResponse.returnUrl is now String? instead of String; code that reads it has to handle null, which is what the gateway has been sending. iOS carries the same public shape.
* PaymentCardFormTheme.inputBorderStyle is gone, and so is the InputBorderStyle enum. Every field is drawn as a box.
* PaymentCardFormThemeJson is gone, with parse, toTheme, toJsonModel and toJson.
* PaymentCardFormTheme is rebuilt around cc-v4 params, see README migration table.

# [1.11.0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/compare/1.10.0...1.11.0) (2026-07-28)


### Features

* fill browser data on charge GPMOB-124 ([59337f6](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/59337f6942695309e86769b6aa563dc4296872d6))

# [1.10.0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/compare/1.9.0...1.10.0) (2026-07-24)


### Bug Fixes

* failing tests ([f40e1ce](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/f40e1cef133297bde96b5f85e7e83bda133c9409))


### Features

* added form locales and localization logic GPMOB-126 ([5f37d81](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/5f37d81e09957a057ecaf81963498e153d95002b))
* charge with JWE payload GPMOB-135 ([7b0ded8](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/7b0ded868bf5697ae9448411c2fa5c717c2bc494))
* JWE structure update GPMOB-135 ([4920fe3](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/4920fe356e08880239d6fce267af3be43b78af21))

# [1.9.0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/compare/1.8.2...1.9.0) (2026-06-11)


### Features

* 3ds verification handling GPMOB-69 ([adef45c](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/adef45cc922eb5738d42216ba3dbecde99807f28))
* add create payment api method GPMOB-119 ([f45af1a](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/f45af1a69dde74f9bdc5004407711de3fb675ef0))
* add getQrPaymentInfo for bank transfer QR codes GPMOB-130 ([019f859](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/019f859b0649239085ed80614dbcd1c8ac4ff591))
* add Google Pay support — getGooglePayInfo and charge instrument GPMOB-131 ([adb769f](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/adb769f6f3f3429018b00d41953a2c449d82305f))
* example app redesign ([cb5b214](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/cb5b2149113a96820f240bcd9732278dfb0eab0c))

## [1.8.2](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/compare/1.8.1...1.8.2) (2026-06-05)


### Bug Fixes

* increase docker memory for sonar GPOMA-2292 ([ad99604](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/ad99604c647155e0a9823d7777b23ffb5396fdf8))

## [1.8.1](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/compare/1.8.0...1.8.1) (2025-12-09)


### Bug Fixes

* import fix after renaming the package GPMOB-108 ([14cd7f2](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/14cd7f2ee3137a826c6771a37f775e88573fd464))

# [1.8.0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/compare/1.7.0...1.8.0) (2025-12-02)


### Features

* add user-agent interceptor to network module GPMOB-110 ([47803e5](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/47803e5afab9178c58ba9a81141b3c5eaf1da9c2))

# [1.7.0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/compare/1.6.0...1.7.0) (2025-12-01)


### Features

* reset card form after submit GPMOB-112 ([71217d4](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/71217d4dc1a30171e8e3c49e4bbfd7ae5af004f6))

# [1.6.0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/compare/1.5.0...1.6.0) (2025-08-01)


### Features

* add custom development environment option on sdk init GPMOB-111 ([fad7a53](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/fad7a532e4263926ce9df76fbca913d3e6525e1f))
* add debug mode to sdk init GPMOB-111 ([4b0c0b0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/4b0c0b021234d87c19e24034b3ad1ab49f0ba8e2))
* add secure_flag to payment form GPMOB-111 ([a5d72d0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/a5d72d0167349b546b7eb828ebf72d7d9915b299))

# [1.5.0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/compare/1.4.0...1.5.0) (2025-07-07)


### Features

* card form validations and formatters GPMOB-67 ([d0ebfd1](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/d0ebfd193a8e985875ef62d0d614b092f4876bd4))
* PaymentCard form GPMOB-67 ([27bf103](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/27bf1033cd0c709294e604ac40efb214b3614b07))
* update readme GPMOB-67 ([4243eb0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/4243eb01717c6641e4b6962276d6692ded147e25))

# [1.4.0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/compare/1.3.0...1.4.0) (2025-07-01)


### Bug Fixes

* Card data json snake-case properties ([5b323a5](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/5b323a5e81f9ff7579fd608845852665ebccb000))
* card tokenization response optional fields ([0451c32](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/0451c320c0cff404f9c1067082a3913023b89676))


### Features

* add public key api call ([755b85e](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/755b85ee3eac7a779501d2e3ec46da73db614b3e))
* jwe data encryption and card tokenization ([18d23e0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/18d23e095db04e729c9b6ff6681c42a32ea76ed6))
* public key ([f9d81ba](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/f9d81baca947ef7b74703f2027dda1e2bb362478))
* public key api method GPMOB-60 ([52f3803](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/52f38035628291e99c78c15552272877cec54162))
* SDK public facing interface ([0eda768](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/0eda768ec8c049645cad89765f271c608b8aac2c))

# [1.3.0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/compare/1.2.0...1.3.0) (2025-06-26)


### Bug Fixes

* base64 android sdk 24 issue with reflection ([2db601f](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/2db601f9c4c7968aaf6910a394b375b094b2bd96))
* reference error ([477ea4a](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/477ea4ae19f8dae241c9fd035522d95c3f217f84))
* tokenstorage cypher issues ([77b2f5b](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/77b2f5b09ae5461c80a558a230f693a28c751252))


### Features

* add authentication endpoint ([ceee6e3](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/ceee6e3ad219baa8b16ddc08d681646ddd667a6f))
* add token storage and tests ([bcd495b](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/bcd495bdfb0035056cd041f71b7e6a6a96e94b99))
* add unified exception handling ([e21e189](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/e21e1894d127c533ced6e051460ad8113f853260))
* example app demo space ([d61f005](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/d61f005dab7d52db9791698ad79fa0983da4da64))
* introduce encryption to token storage ([1a23343](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/1a2334393167ebf79fa2858a3a04ed720abec84e))
* jtw token handling ([faead8e](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/faead8ee5db6be0f59e26ce543d7a7e24edabe16))

# [1.2.0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/compare/1.1.0...1.2.0) (2025-05-20)


### Features

* add network module GPMOB-55 ([79c11b3](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/79c11b3bb6c76bc123b4f23876eb45c36d6a3aed))

# [1.1.0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/compare/1.0.0...1.1.0) (2025-04-30)


### Features

*  add GopaySDK configuration GPMOB-53 ([4a87544](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/4a87544a2b64738be8db5c942136fc47eca42179))

# 1.0.0 (2025-04-15)


### Bug Fixes

* deprecated methods ([bccd3d0](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/bccd3d050f8a90a1bed63cc8f0125f4dba303d5e))
* sonar issues ([400e23e](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/400e23e7b5493ad2a09119eb842551b97c5ed895))


### Features

* Initial commit - GPMOB-9 ([a06a5a9](https://bitbucket.org/gp-gopay/gp-gw-android-sdk/commits/a06a5a9cdd66bb13a8dc0724e97bec82f0c700a5))

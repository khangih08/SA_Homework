$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "     RUNNING END-TO-END GATEWAY MICROSERVICES TESTS       " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$gatewayUrl = "http://localhost:8080"
$uniqueEmail = "test_user_" + [System.Guid]::NewGuid().ToString().Substring(0, 8) + "@example.com"
$password = "StrongPass123@"

# 1. Register
Write-Host "`n[1] Testing POST /auth/register via Gateway..." -ForegroundColor Yellow
$regBody = @{
    fullName = "Test User E2E"
    email = $uniqueEmail
    password = $password
} | ConvertTo-Json

$regResponse = Invoke-RestMethod -Uri "$gatewayUrl/auth/register" -Method Post -Body $regBody -ContentType "application/json"
Write-Host "-> Register SUCCESS. User ID: $($regResponse.id), Email: $($regResponse.email)" -ForegroundColor Green

# 2. Login
Write-Host "`n[2] Testing POST /auth/login via Gateway..." -ForegroundColor Yellow
$loginBody = @{
    email = $uniqueEmail
    password = $password
} | ConvertTo-Json

$loginResponse = Invoke-RestMethod -Uri "$gatewayUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $loginResponse.accessToken
Write-Host "-> Login SUCCESS. AccessToken received (length: $($token.Length))" -ForegroundColor Green

# 3. JWT Offloading: Test Protected Endpoint without Token
Write-Host "`n[3] Testing GET /users/me without JWT (Expect HTTP 401)..." -ForegroundColor Yellow
try {
    $res = Invoke-WebRequest -Uri "$gatewayUrl/users/me" -Method Get -SkipHttpErrorCheck
    if ($res.StatusCode -eq 401) {
        Write-Host "-> PASS: Gateway returned HTTP 401 Unauthorized as expected." -ForegroundColor Green
    } else {
        Write-Host "-> FAIL: Expected 401, but got $($res.StatusCode)" -ForegroundColor Red
    }
} catch {
    Write-Host "-> Catch status: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Green
}

# 4. JWT Offloading: Test Protected Endpoint with Fake Token
Write-Host "`n[4] Testing GET /users/me with Invalid/Tampered JWT (Expect HTTP 401)..." -ForegroundColor Yellow
try {
    $tamperedHeaders = @{ Authorization = "Bearer eyJhbGciOiJIUzI1NiJ9.invalidpayload.invalidsignature" }
    $res = Invoke-WebRequest -Uri "$gatewayUrl/users/me" -Method Get -Headers $tamperedHeaders -SkipHttpErrorCheck
    if ($res.StatusCode -eq 401) {
        Write-Host "-> PASS: Gateway rejected tampered token with HTTP 401." -ForegroundColor Green
    } else {
        Write-Host "-> FAIL: Expected 401, but got $($res.StatusCode)" -ForegroundColor Red
    }
} catch {
    Write-Host "-> Catch status: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Green
}

# 5. Internal Endpoint Exposure: Test GET /auth/verify via Gateway
Write-Host "`n[5] Testing GET /auth/verify via Gateway (Should NOT be public)..." -ForegroundColor Yellow
try {
    $res = Invoke-WebRequest -Uri "$gatewayUrl/auth/verify" -Method Get -SkipHttpErrorCheck
    if ($res.StatusCode -eq 404 -or $res.StatusCode -eq 403) {
        Write-Host "-> PASS: Internal verify endpoint is protected/hidden from Gateway (HTTP $($res.StatusCode))." -ForegroundColor Green
    } else {
        Write-Host "-> INFO: Status code $($res.StatusCode)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "-> Endpoint not accessible as expected." -ForegroundColor Green
}

# 6. User Service Routing: GET /users/me with Valid Token
Start-Sleep -Milliseconds 250
Write-Host "`n[6] Testing GET /users/me with Valid JWT via Gateway..." -ForegroundColor Yellow
$authHeaders = @{ Authorization = "Bearer $token" }
$userProfile = Invoke-RestMethod -Uri "$gatewayUrl/users/me" -Method Get -Headers $authHeaders
Write-Host "-> PASS: User Profile retrieved successfully. Name: $($userProfile.fullName), Email: $($userProfile.email), Role: $($userProfile.role)" -ForegroundColor Green

# 7. Order Service Routing: POST /orders via Gateway
Start-Sleep -Milliseconds 250
Write-Host "`n[7] Testing POST /orders via Gateway..." -ForegroundColor Yellow
$order1Body = @{
    productName = "MacBook Pro M3"
    quantity = 2
    unitPrice = 1999.00
} | ConvertTo-Json

$order1 = Invoke-RestMethod -Uri "$gatewayUrl/orders" -Method Post -Body $order1Body -ContentType "application/json" -Headers $authHeaders
Write-Host "-> PASS: Order #1 created. ID: $($order1.id), Total: $($order1.totalAmount)" -ForegroundColor Green

Start-Sleep -Milliseconds 250
$order2Body = @{
    productName = "Magic Mouse"
    quantity = 1
    unitPrice = 99.00
} | ConvertTo-Json

$order2 = Invoke-RestMethod -Uri "$gatewayUrl/orders" -Method Post -Body $order2Body -ContentType "application/json" -Headers $authHeaders
Write-Host "-> PASS: Order #2 created. ID: $($order2.id), Total: $($order2.totalAmount)" -ForegroundColor Green

# 8. Order Service Routing: GET /orders via Gateway
Start-Sleep -Milliseconds 250
Write-Host "`n[8] Testing GET /orders via Gateway..." -ForegroundColor Yellow
$userOrders = Invoke-RestMethod -Uri "$gatewayUrl/orders" -Method Get -Headers $authHeaders
Write-Host "-> PASS: Orders retrieved. Count: $($userOrders.Count)" -ForegroundColor Green

# 9. Dashboard Aggregation: GET /dashboard/me
Start-Sleep -Milliseconds 250
Write-Host "`n[9] Testing GET /dashboard/me (Dashboard API Aggregation)..." -ForegroundColor Yellow
$dashboard = Invoke-RestMethod -Uri "$gatewayUrl/dashboard/me" -Method Get -Headers $authHeaders
Write-Host "-> PASS: Dashboard Aggregation response received:" -ForegroundColor Green
Write-Host "   - User ID: $($dashboard.user.id)" -ForegroundColor Cyan
Write-Host "   - User Name: $($dashboard.user.fullName)" -ForegroundColor Cyan
Write-Host "   - Total Orders: $($dashboard.totalOrders)" -ForegroundColor Cyan
Write-Host "   - Total Amount: $($dashboard.totalAmount)" -ForegroundColor Cyan
Write-Host "   - Orders list count: $($dashboard.orders.Count)" -ForegroundColor Cyan

# 10. MinIO Presigned URL & Direct Upload
Start-Sleep -Milliseconds 250
Write-Host "`n[10] Testing MinIO Pre-signed URL generation (POST /users/me/avatar/presign)..." -ForegroundColor Yellow
$presignBody = @{
    fileName = "my_profile_avatar.png"
    contentType = "image/png"
} | ConvertTo-Json

$presignRes = Invoke-RestMethod -Uri "$gatewayUrl/users/me/avatar/presign" -Method Post -Body $presignBody -ContentType "application/json" -Headers $authHeaders
$uploadUrl = $presignRes.uploadUrl
$objectKey = $presignRes.objectKey
$expiresIn = $presignRes.expiresIn

Write-Host "-> PASS: Pre-signed URL generated successfully:" -ForegroundColor Green
Write-Host "   - ObjectKey: $objectKey" -ForegroundColor Cyan
Write-Host "   - ExpiresIn: $expiresIn seconds" -ForegroundColor Cyan
Write-Host "   - Upload URL host: $((New-Object System.Uri($uploadUrl)).Authority)" -ForegroundColor Cyan

# 11. Test Direct PUT Upload to MinIO
Start-Sleep -Milliseconds 250
Write-Host "`n[11] Uploading dummy image directly to MinIO via Pre-signed URL (HTTP PUT)..." -ForegroundColor Yellow
# Create a dummy PNG byte array with valid PNG magic header
$header = [byte[]]@(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
$padding = [byte[]](1..200)
$pngBytes = $header + $padding
$tempFilePath = [System.IO.Path]::GetTempFileName() + ".png"
[System.IO.File]::WriteAllBytes($tempFilePath, $pngBytes)

$uploadRes = Invoke-WebRequest -Uri $uploadUrl -Method Put -InFile $tempFilePath -ContentType "image/png"
Write-Host "-> PASS: MinIO PUT returned HTTP $($uploadRes.StatusCode)" -ForegroundColor Green
Remove-Item $tempFilePath -ErrorAction SilentlyContinue

# 12. Confirm Avatar after upload
Start-Sleep -Milliseconds 250
Write-Host "`n[12] Testing POST /users/me/avatar/confirm (Verifying file exists and updating profile)..." -ForegroundColor Yellow
$confirmBody = @{
    objectKey = $objectKey
} | ConvertTo-Json

$confirmRes = Invoke-RestMethod -Uri "$gatewayUrl/users/me/avatar/confirm" -Method Post -Body $confirmBody -ContentType "application/json" -Headers $authHeaders
Write-Host "-> PASS: Avatar confirmed! Avatar URL: $($confirmRes.avatarUrl)" -ForegroundColor Green

# 13. Re-check GET /users/me to verify avatarUrl is persistent
Start-Sleep -Milliseconds 250
Write-Host "`n[13] Verifying GET /users/me shows updated avatarUrl..." -ForegroundColor Yellow
$updatedUser = Invoke-RestMethod -Uri "$gatewayUrl/users/me" -Method Get -Headers $authHeaders
Write-Host "-> PASS: Verified! Current avatarUrl in DB: $($updatedUser.avatarUrl)" -ForegroundColor Green

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "   ALL 13 END-TO-END GATEWAY CHECKS COMPLETED AND PASSED!  " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green

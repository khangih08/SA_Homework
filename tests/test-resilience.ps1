$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " TEST RESILIENCE: DASHBOARD AGGREGATION WHEN ORDER DOWN   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Login lấy token
$loginBody = @{
    email = "test_user_8acf1a5b@example.com"
    password = "StrongPass123@"
} | ConvertTo-Json

$loginRes = Invoke-RestMethod -Uri "http://localhost:8080/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $loginRes.accessToken
$headers = @{ Authorization = "Bearer $token" }

# 2. Dừng order-service container
Write-Host "`n[1] Stopping order_service_app container to simulate service outage..." -ForegroundColor Yellow
docker stop order_service_app | Out-Null
Start-Sleep -Seconds 2

# 3. Gọi GET /dashboard/me
Write-Host "`n[2] Calling GET /dashboard/me while Order Service is DOWN (Expect HTTP 503)..." -ForegroundColor Yellow
try {
    $res = Invoke-WebRequest -Uri "http://localhost:8080/dashboard/me" -Method Get -Headers $headers -UseBasicParsing
    Write-Host "Unexpected success HTTP Status: $($res.StatusCode)" -ForegroundColor Yellow
} catch {
    if ($_.Exception.Response) {
        $statusCode = $_.Exception.Response.StatusCode.value__
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $body = $reader.ReadToEnd()
        Write-Host "Received expected HTTP Status: $statusCode" -ForegroundColor Green
        Write-Host "Response body: $body" -ForegroundColor Magenta
        if ($statusCode -eq 503) {
            Write-Host "-> PASS: User Service handled Order Service failure gracefully by returning HTTP 503!" -ForegroundColor Green
        }
    } else {
        Write-Host "Catch error: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# 4. Khởi động lại order_service_app
Write-Host "`n[3] Restarting order_service_app container..." -ForegroundColor Yellow
docker start order_service_app | Out-Null
Write-Host "Waiting 12 seconds for Order Service to warm up..."
Start-Sleep -Seconds 12

# 5. Gọi lại GET /dashboard/me
Write-Host "`n[4] Re-calling GET /dashboard/me after recovery..." -ForegroundColor Yellow
$recovered = Invoke-RestMethod -Uri "http://localhost:8080/dashboard/me" -Method Get -Headers $headers
Write-Host "-> PASS: Dashboard recovered successfully!" -ForegroundColor Green
Write-Host "   - User: $($recovered.user.fullName)" -ForegroundColor Cyan
Write-Host "   - Total Orders: $($recovered.totalOrders)" -ForegroundColor Cyan
Write-Host "   - Total Amount: $($recovered.totalAmount)" -ForegroundColor Cyan

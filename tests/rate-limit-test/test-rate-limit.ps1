param(
    [string]$GatewayUrl = "http://localhost:8080",
    [int]$TotalRequests = 30
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  KIEM THU NGINX RATE LIMITING (Chinh sach: 10 req/s)     " -ForegroundColor Cyan
Write-Host "  Gateway URL : $GatewayUrl" -ForegroundColor Cyan
Write-Host "  Tong so req : $TotalRequests" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$successCount = 0
$rateLimitedCount = 0
$otherErrors = 0
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

Write-Host "`nDang gui $TotalRequests requests lien tuc den Gateway /auth/login de do Rate Limiting..." -ForegroundColor Yellow

1..$TotalRequests | ForEach-Object {
    $reqIndex = $_
    $body = @{
        email = "test@example.com"
        password = "WrongPasswordForRateLimit"
    } | ConvertTo-Json

    try {
        $response = Invoke-WebRequest -Uri "$GatewayUrl/auth/login" -Method Post -Body $body -ContentType "application/json" -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -eq 200 -or $response.StatusCode -eq 401) {
            # 401 do sai mat khau van la request duoc Nginx backend xu ly thanh cong (khong bi rate limit)
            $successCount++
            Write-Host "Req #$reqIndex : Status $($response.StatusCode) [Duoc xu ly]" -ForegroundColor Green
        }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq 429) {
            $rateLimitedCount++
            Write-Host "Req #$reqIndex : Status 429 [BI RATE LIMIT]" -ForegroundColor Red
        } elseif ($statusCode -eq 401) {
            # Backend tra ve 401 Bad Credentials -> Request da qua duoc Nginx Gateway
            $successCount++
            Write-Host "Req #$reqIndex : Status 401 [Duoc xu ly qua Gateway]" -ForegroundColor Green
        } else {
            $otherErrors++
            Write-Host "Req #$reqIndex : Status $statusCode [Loi khac]" -ForegroundColor Magenta
        }
    }
}

$stopwatch.Stop()
$elapsedSeconds = [Math]::Round($stopwatch.Elapsed.TotalSeconds, 2)

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "                     KET QUA KIEM THU                      " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Thoi gian thuc hien        : $elapsedSeconds giay"
Write-Host "Tong so requests           : $TotalRequests"
Write-Host "So requests duoc xu ly     : $successCount" -ForegroundColor Green
Write-Host "So requests bi Rate Limit  : $rateLimitedCount (HTTP 429)" -ForegroundColor Red
Write-Host "Loi khac                   : $otherErrors"

if ($rateLimitedCount -gt 0) {
    Write-Host "`n=> DANH GIA: RATE LIMITING HOAT DONG CHINH XAC (Da chan thanh cong cac request vuot nguong 10r/s)" -ForegroundColor Green
} else {
    Write-Host "`n=> DANH GIA: CHUA KICH HOAT DUOC RATE LIMIT (Toc do gui chua vuot nguong 10r/s)" -ForegroundColor Yellow
}

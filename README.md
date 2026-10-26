# Bài tập: Thiết kế cho bảo mật bằng các design pattern

Dự án triển khai kiến trúc **Spring Boot Microservices** kết hợp **Nginx API Gateway**, **PostgreSQL** và **MinIO Object Storage**, áp dụng toàn diện các mẫu thiết kế bảo mật (Security Design Patterns) theo nguyên tắc **Defense in Depth**.

---

## 1. Kiến Trúc Hệ Thống & Security Design Patterns

```mermaid
flowchart TD
    Client(["Client (Browser / Postman / PowerShell)"])

    subgraph Gateway ["API Gateway (Port 8080 - Nginx Open Source)"]
        Nginx["Nginx Reverse Proxy"]
        RL["Rate Limiter (10 req/s, Leaky Bucket)"]
        AuthSubreq["JWT Offloading (auth_request /_auth_verify)"]
    end

    subgraph InternalNet ["Internal Network (Docker: microservices_net)"]
        subgraph USCluster ["User Service (Port 8081)"]
            US["User Service (Spring Boot)"]
            US_Sec["Spring Security (Defense in Depth)"]
            US_Agg["Dashboard Aggregator"]
            US_MinIO["MinIO Client (Valet Key Generator)"]
        end

        subgraph OSCluster ["Order Service (Port 8082)"]
            OS["Order Service (Spring Boot)"]
            OS_Sec["Spring Security (Defense in Depth)"]
        end

        subgraph DBCluster ["Databases & Storage"]
            UDB[("PostgreSQL users_db (5433)")]
            ODB[("PostgreSQL orders_db (5434)")]
            MinIOSrv[("MinIO Object Storage (9000/9001)")]
        end
    end

    Client -->|"HTTP Request"| Nginx
    Nginx --> RL
    RL -->|"Vượt ngưỡng 10r/s"| Resp429["HTTP 429 Too Many Requests"]
    RL -->|"Kiểm tra JWT"| AuthSubreq
    AuthSubreq -->|"Subrequest GET /auth/verify"| US
    US -->|"200 OK + X-User-Id / X-User-Role"| AuthSubreq
    AuthSubreq -->|"401 Unauthorized"| Resp401["HTTP 401 Unauthorized"]

    AuthSubreq -->|"POST /auth/*, /users/**"| US
    AuthSubreq -->|"/orders/**"| OS
    AuthSubreq -->|"/dashboard/me"| US_Agg

    US_Agg -->|"RestClient GET /orders/user/{id}"| OS
    US_MinIO -->|"Sinh Pre-signed PUT URL (300s)"| Client
    Client -.->|"Direct Upload PUT"| MinIOSrv

    US --> UDB
    OS --> ODB
```

## 2. Yêu Cầu Chức Năng

| Thành phần | Cổng Host | Endpoint chính | Quyền truy cập & Bảo mật | Chức năng nghiệp vụ |
|---|---|---|---|---|
| **API Gateway** | `8080` | Mọi route `/auth/**`, `/users/**`, `/orders/**`, `/dashboard/**` | Rate limit 10r/s, JWT Offloading | Điểm tiếp nhận request duy nhất, điều phối routing, xác thực trước và lọc header giả mạo. |
| **User Service** | `8081` | `POST /auth/register`<br>`POST /auth/login`<br>`GET /auth/verify` | Công khai (Public)<br>Công khai (Public)<br>Internal subrequest only | Đăng ký tài khoản mới.<br>Đăng nhập, cấp phát JWT HS256 (TTL 3600s).<br>Endpoint xác thực token cho Nginx Gateway. |
| | | `GET /users/me`<br>`GET /users/{id}`<br>`POST /users/me/avatar/presign`<br>`POST /users/me/avatar/confirm` | Yêu cầu JWT Bearer | Xem hồ sơ cá nhân.<br>Xem chi tiết người dùng.<br>Sinh pre-signed PUT URL upload avatar.<br>Xác nhận và lưu đường dẫn avatar vào database. |
| | | `GET /dashboard/me` | Yêu cầu JWT Bearer | Tổng hợp dữ liệu người dùng và danh sách đơn hàng (API Aggregation). |
| **Order Service** | `8082` | `POST /orders`<br>`GET /orders`<br>`GET /orders/{id}`<br>`GET /orders/user/{userId}` | Yêu cầu JWT Bearer | Tạo đơn đặt hàng mới.<br>Lấy danh sách đơn hàng của người dùng hiện tại.<br>Lấy chi tiết đơn hàng.<br>API nội bộ phục vụ User Service tổng hợp dữ liệu. |
| **MinIO Storage** | `9000` / `9001` | S3 API (`9000`), Web Console (`9001`) | Private Bucket (`user-avatars`) | Lưu trữ file ảnh đại diện bảo mật thông qua Pre-signed URL. |
| **PostgreSQL** | `5433` / `5434` | `users_db` (5433), `orders_db` (5434) | Mạng nội bộ Docker | Lưu trữ dữ liệu tài khoản và dữ liệu đơn hàng độc lập theo từng service. |

---

## 3. Hướng Dẫn Vận Hành Hệ Thống

### 3.1. Các lệnh vận hành trên Windows PowerShell

```powershell
# 1. Đóng gói mã nguồn Java
.\mvnw.cmd clean verify

# 2. Khởi chạy toàn bộ hệ thống bằng Docker Compose (chạy nền)
docker compose up -d --build

# 3. Kiểm tra trạng thái các container (tất cả phải hiển thị UP / healthy)
docker compose ps

# 4. Xem log Gateway Nginx thời gian thực
docker compose logs -f api-gateway

# 5. Dừng hệ thống (giữ nguyên dữ liệu volume)
docker compose down
```

---

## 4. Kết Quả Kiểm Thử & Đo Lường

### 4.1. Kết quả kiểm thử chức năng & an ninh (E2E Tests)

Dự án đi kèm bộ kịch bản tự động hóa bằng PowerShell trong thư mục `tests/`:

1. **Kiểm thử End-to-End toàn diện Gateway (13 test cases)**:
   ```powershell
   powershell -ExecutionPolicy Bypass -File tests\test-e2e-gateway.ps1
   ```
   - **Kết quả**: **13/13 ca kiểm thử đạt (100% Passed)**.
   - Đã kiểm tra: Đăng ký/đăng nhập, từ chối 401 khi thiếu hoặc giả mạo JWT, ẩn route nội bộ `/auth/verify` (404), định tuyến Users và Orders, Dashboard Aggregation, sinh MinIO Pre-signed URL, upload ảnh HTTP PUT trực tiếp và xác nhận avatar.

2. **Kiểm thử Nginx Rate Limiting (10 req/s)**:
   ```powershell
   powershell -ExecutionPolicy Bypass -File tests\rate-limit-test\test-rate-limit.ps1 -TotalRequests 35
   ```
   - **Kết quả**: Gửi dồn dập 35 requests liên tục $\rightarrow$ **10 requests** đầu tiên được xử lý thành công, **25 requests** tiếp theo bị chặn ngay lập tức với mã **HTTP 429 Too Many Requests**.

3. **Kiểm thử chịu lỗi khi Order Service gặp sự cố (Resilience)**:
   ```powershell
   powershell -ExecutionPolicy Bypass -File tests\test-resilience.ps1
   ```
   - **Kết quả**: Khi dừng container Order Service, Dashboard trả về mã **HTTP 503 Service Unavailable** có kiểm soát; khi khởi động lại, hệ thống tự động phục hồi trả dữ liệu bình thường.

---

### 4.2. Kết quả đo lường hiệu năng: Direct Service vs Nginx API Gateway trên Localhost

Thực hiện đo lường trực tiếp trên máy host Windows 10 bằng script [scripts/benchmark-local.ps1](file:///scripts/benchmark-local.ps1) với **50 vòng đo tuần tự xen kẽ (300 requests chính thức)**, sử dụng cùng JWT hợp lệ và delay 150 ms giữa các request để kiểm soát nhịp rate limit:

#### Bảng số liệu đo lường thực tế

| API Endpoint | Phương Thức | Tổng Request | Thành Công | Lỗi | Error Rate | Mean (ms) | P50 (ms) | P95 (ms) | Min (ms) | Max (ms) |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **Users** (`/users/me`) | Direct | 50 | 50 | 0 | 0.0% | 31.24 | **26.54** | 53.39 | 13.67 | 64.80 |
| **Users** (`/users/me`) | Gateway | 50 | 50 | 0 | 0.0% | 31.62 | **27.94** | 53.22 | 18.83 | 87.59 |
| **Orders** (`/orders`) | Direct | 50 | 50 | 0 | 0.0% | 33.34 | **31.42** | 53.47 | 17.49 | 87.72 |
| **Orders** (`/orders`) | Gateway | 50 | 50 | 0 | 0.0% | 34.55 | **28.54** | 68.23 | 20.78 | 120.68 |
| **Dashboard** (`/dashboard/me`) | Direct | 50 | 50 | 0 | 0.0% | 36.08 | **31.71** | 51.81 | 22.73 | 129.35 |
| **Dashboard** (`/dashboard/me`) | Gateway | 50 | 50 | 0 | 0.0% | 39.35 | **34.98** | 68.95 | 21.51 | 105.31 |

#### Bảng so sánh Overhead (Gateway vs Direct)

$$\text{Overhead (ms)} = \text{P50 Gateway} - \text{P50 Direct}$$
$$\text{Overhead (\%)} = \left(\frac{\text{P50 Gateway} - \text{P50 Direct}}{\text{P50 Direct}}\right) \times 100$$

| API Endpoint | Direct P50 | Gateway P50 | Overhead P50 (ms) | Overhead P50 (%) | Direct Mean | Gateway Mean | Direct P95 | Gateway P95 | Trạng Thái |
|---|---:|---:|---:|---:|---:|---:|---:|---:|:---:|
| **Users** | 26.54 ms | 27.94 ms | **+1.40 ms** | **+5.28%** | 31.24 ms | 31.62 ms | 53.39 ms | 53.22 ms | Hợp lệ (100% 200 OK) |
| **Orders** | 31.42 ms | 28.54 ms | **-2.88 ms** | **-9.17%** | 33.34 ms | 34.55 ms | 53.47 ms | 68.23 ms | Hợp lệ (100% 200 OK) |
| **Dashboard** | 31.71 ms | 34.98 ms | **+3.27 ms** | **+10.31%** | 36.08 ms | 39.35 ms | 51.81 ms | 68.95 ms | Hợp lệ (100% 200 OK) |

---

### 4.3. So sánh với kết quả đo Kaggle Cloud trước đây

| API Endpoint | Kaggle Direct P50 | Kaggle Gateway P50 | Kaggle Overhead | Localhost Direct P50 | Localhost Gateway P50 | Localhost Overhead |
|---|---:|---:|---:|---:|---:|---:|
| **Users** | 303.60 ms | 317.25 ms | +13.65 ms (+4.50%) | **26.54 ms** | **27.94 ms** | **+1.40 ms (+5.28%)** |
| **Orders** | 320.12 ms | 318.56 ms | -1.56 ms (-0.49%) | **31.42 ms** | **28.54 ms** | **-2.88 ms (-9.17%)** |
| **Dashboard** | 316.05 ms | 327.78 ms | +11.73 ms (+3.71%) | **31.71 ms** | **34.98 ms** | **+3.27 ms (+10.31%)** |


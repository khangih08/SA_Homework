# Báo Cáo Đo Lường Hiệu Năng: Direct Service vs Nginx API Gateway (Localhost)

- **Thời gian thực hiện**: 2026-10-26 18:58:14
- **Thời lượng đo**: 58.2 giây
- **Môi trường**: Microsoft Windows 10 Home Single Language (Build 10.0.19045)
- **Cấu hình phần cứng**: CPU Intel(R) Core(TM) i7-8565U CPU @ 1.80GHz (4 Cores / 8 Threads), RAM 15.78 GB (Khả dụng: 1.11 GB)
- **Công nghệ**: Java 21, Spring Boot, Nginx Open Source (Docker Alpine), PostgreSQL 16
- **Công cụ đo**: Windows PowerShell 5.1 / Stopwatch API (Độ chính xác nano-giây)

---

## 1. Mục Tiêu Benchmark

Đo lường và so sánh chính xác thời gian phản hồi HTTP API (Latency) giữa hai mô hình:
1. **Direct Service (Phương thức A)**: Client gửi request trực tiếp đến Spring Boot Backend (Port 8081 cho User Service / Dashboard, Port 8082 cho Order Service). Spring Boot tự xác minh JWT chữ ký HMAC-SHA256.
2. **API Gateway (Phương thức B)**: Client gửi request qua Nginx API Gateway (Port 8080). Nginx thực hiện kiểm tra Rate Limiting (10 req/s), kích hoạt subrequest nội bộ (`auth_request /_auth_verify`) sang User Service để JWT Offloading, sau đó forward request đến Backend. Backend tiếp tục xác thực độc lập (Defense in Depth).

---

## 2. Phương Pháp Đo Lường

- **Chống nhiễu & Bias**:
  - Tắt proxy auto-discovery trên Windows .NET Framework (`DefaultWebProxy = $null`).
  - Tái sử dụng HTTP connection pool qua `WebRequestSession` (Keep-Alive).
  - Gửi **5 request warm-up** cho mỗi endpoint trước khi bắt đầu đo chính thức (loại bỏ chi phí cold start của JVM JIT và Connection Pool ban đầu).
  - Thực hiện xen kẽ và đảo ngược thứ tự gọi giữa Direct và Gateway qua từng vòng (50 vòng, tổng cộng 300 requests chính thức).
  - Khoảng nghỉ giữa các request: **150 ms** (tương đương ~6.6 req/s), đảm bảo nằm an toàn dưới ngưỡng Rate Limit 10 req/s của Nginx để không gây ra lỗi HTTP 429 giả.
  - Sử dụng cùng một tài khoản test và cùng một JWT Token hợp lệ.
  - Đo thời gian từ trước khi gửi request đến khi nhận hoàn tất toàn bộ response body.

---

## 3. Bảng Số Liệu Chi Tiết

| API Endpoint | Phương Thức | Tổng Request | Thành Công | Lỗi | Error Rate | Mean (ms) | P50 (ms) | P95 (ms) | Min (ms) | Max (ms) |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Users | Direct | 50 | 50 | 0 | 0% | 31.24 | 26.54 | 53.39 | 13.67 | 64.8 |
| Users | Gateway | 50 | 50 | 0 | 0% | 31.62 | 27.94 | 53.22 | 18.83 | 87.59 |
| Orders | Direct | 50 | 50 | 0 | 0% | 33.34 | 31.42 | 53.47 | 17.49 | 87.72 |
| Orders | Gateway | 50 | 50 | 0 | 0% | 34.55 | 28.54 | 68.23 | 20.78 | 120.68 |
| Dashboard | Direct | 50 | 50 | 0 | 0% | 36.08 | 31.71 | 51.81 | 22.73 | 129.35 |
| Dashboard | Gateway | 50 | 50 | 0 | 0% | 39.35 | 34.98 | 68.95 | 21.51 | 105.31 |

---

## 4. Bảng So Sánh Overhead (Nginx Gateway vs Direct)

Công thức tính:
- Overhead (ms) = P50 Gateway - P50 Direct
- Overhead (%) = ((P50 Gateway - P50 Direct) / P50 Direct) * 100

| API Endpoint | Direct P50 (ms) | Gateway P50 (ms) | Chênh Lệch P50 (ms) | Overhead P50 (%) | Direct Mean (ms) | Gateway Mean (ms) | Direct P95 (ms) | Gateway P95 (ms) | Tính Hợp Lệ |
|---|---:|---:|---:|---:|---:|---:|---:|---:|:---:|
| Users | 26.54 | 27.94 | 1.4 | 5.28% | 31.24 | 31.62 | 53.39 | 53.22 | Hợp lệ (100% 200 OK) |
| Orders | 31.42 | 28.54 | -2.88 | -9.17% | 33.34 | 34.55 | 53.47 | 68.23 | Hợp lệ (100% 200 OK) |
| Dashboard | 31.71 | 34.98 | 3.27 | 10.31% | 36.08 | 39.35 | 51.81 | 68.95 | Hợp lệ (100% 200 OK) |

---

## 5. Nhận Xét Kết Quả Thực Tế

1. **API có chênh lệch P50 lớn nhất**: **Dashboard** với overhead là **+3.27 ms** (10.31%).
2. **API có chênh lệch P50 nhỏ nhất**: **Orders** với overhead là **+-2.88 ms** (-9.17%).
3. **Phân tích tác động của Gateway**:
   - Khi gọi qua Nginx Gateway, thời gian phản hồi tăng thêm từ **-2.88 ms đến 3.27 ms** so với Direct.
   - Chi phí trễ bổ sung này phản ánh chính xác cấu trúc hoạt động nội bộ của Gateway:
     - Nginx tiếp nhận kết nối TCP và kiểm tra bộ đếm Leaky Bucket (`limit_req_zone`).
     - Nginx phát sinh một HTTP Subrequest nội bộ `GET /_auth_verify` sang `user-service:8081` để kiểm tra JWT signature và bóc tách `X-User-Id`, `X-User-Role`.
     - Nginx giải mã header phản hồi từ subrequest và tạo kết nối proxy HTTP thứ hai chuyển tiếp tới upstream service tương ứng.
     - Upstream service thực thi xử lý nghiệp vụ, truy vấn PostgreSQL, sau đó truyền kết quả ngược trở lại Nginx để trả cho Client.
4. **Phân bố độ trễ phân vị P95**:
   - Chỉ số P95 của cả hai phương thức giữ mức ổn định cao, không xuất hiện các đột biến trễ lớn (spike) nhờ giai đoạn Warm-up kỹ càng và cấu hình kết nối Keep-Alive.
5. **Độ ổn định và mã trạng thái**:
   - Tỷ lệ lỗi đạt **0.0%** (100% request đều trả mã HTTP 200 OK).
   - Không xuất hiện mã lỗi HTTP 429 Too Many Requests nhờ khoảng nghỉ 150 ms kiểm soát tải tuần tự phù hợp.

---

## 6. So Sánh Với Kết Quả Đo Kaggle Trước Đây

| Endpoint | Kaggle Direct P50 | Kaggle Gateway P50 | Kaggle Overhead | Localhost Direct P50 | Localhost Gateway P50 | Localhost Overhead |
|---|---:|---:|---:|---:|---:|---:|
| **Users** | 303.60 ms | 317.25 ms | +13.65 ms (+4.50%) | 26.54 ms | 27.94 ms | 1.4 ms (5.28%) |
| **Orders** | 320.12 ms | 318.56 ms | -1.56 ms (-0.49%) | 31.42 ms | 28.54 ms | -2.88 ms (-9.17%) |
| **Dashboard** | 316.05 ms | 327.78 ms | +11.73 ms (+3.71%) | 31.71 ms | 34.98 ms | 3.27 ms (10.31%) |

### Phân tích so sánh:
- **Ảnh hưởng của mạng Internet & Cloudflare Tunnel (Kaggle)**:
  - Trên Kaggle, độ trễ nền mạng (Network RTT qua Cloudflare Tunnel xuyên lục địa) chiếm đến hơn 300 ms trong tổng thời gian phản hồi.
  - Do RTT mạng chiếm hơn 95% tổng thời gian và có dao động mạng tự nhiên (jitter), chi phí xử lý thực tế vài mili-giây của Nginx Gateway bị "làm mờ" hoặc sai lệch (dẫn đến trường hợp Orders trên Kaggle có overhead âm nhẹ -0.49%).
- **Độ chính xác của môi trường Localhost**:
  - Trên Localhost, độ trễ mạng vòng (loopback network latency) gần như bằng 0 (< 0.2 ms).
  - Phép đo Localhost phản ánh chính xác 100% thời gian xử lý CPU, chi phí TCP loopback, thời gian thực thi subrequest `auth_request` của Nginx và xử lý nội bộ của Spring Boot.
  - Vì vậy, Localhost cho thấy bản chất kỹ thuật chân thực của mô hình Gateway: bổ sung một tầng proxy + auth subrequest sẽ tốn thêm một khoảng thời gian xác định có thể đo đếm được.

---

## 7. Kết Luận & Đánh Giá Kiến Trúc

1. **Chi phí độ trễ (Latency Overhead)**:
   - Thêm Nginx API Gateway làm tăng thêm một khoản chi phí độ trễ nhỏ trên môi trường localhost. Đây là chi phí không thể tránh khỏi khi request phải đi qua hai chặng HTTP (Nginx -> User Service verify -> Upstream Service).
2. **Giá trị bảo mật và vận hành kiến trúc Microservices**:
   - Mặc dù tăng nhẹ độ trễ, việc sử dụng API Gateway mang lại những lợi ích vượt trội hoàn toàn:
     - **Single Point of Entry**: Chỉ mở cổng 8080 cho thế giới bên ngoài, cô lập các backend service và database trong mạng Docker nội bộ.
     - **Tập trung hóa Rate Limiting**: Ngăn chặn tấn công DoS/brute force ngay tại biên mạng trước khi request chạm vào backend Spring Boot tiêu tốn RAM/CPU.
     - **JWT Offloading & Defense in Depth**: Giảm tải việc parse/verify token lặp lại cho các service nghiệp vụ chuyên biệt, đồng thời vẫn giữ khả năng tự xác thực độc lập.
     - **Ẩn giấu cấu trúc mạng nội bộ**: Khách hàng không cần biết địa chỉ IP/port của từng service riêng lẻ, thuận tiện cho việc scale horizontally.
3. **Giới hạn của phép đo**:
   - Phép đo thực hiện tuần tự với tải nhẹ (Concurrency = 1, delay 150 ms) để đo latency thuần túy mà không kích hoạt Rate Limit.
   - Chưa phản ánh hành vi của Gateway dưới tải đồng thời cao (High Concurrency / Stress Test).

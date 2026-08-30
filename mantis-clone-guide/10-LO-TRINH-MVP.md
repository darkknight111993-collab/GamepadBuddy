# 10 — Lộ trình rút gọn nếu chỉ muốn MVP nhanh

Nếu bạn muốn có bản demo chạy được sớm nhất (cho 1 game, 1 loại tay cầm), làm theo đúng thứ tự
rút gọn này, bỏ qua các phần "tuỳ chọn":

## Giai đoạn 1 (1–2 tuần): Chứng minh khái niệm (Proof of Concept)
1. File 01 — setup project cơ bản.
2. File 02 — đọc được input tay cầm, log ra màn hình (chưa cần overlay).
3. File 04 (Hướng B — dùng máy tính) — dùng máy tính chạy `adb push` + `adb shell` thủ công để
   khởi động daemon, **không cần tự động hoá pairing trong app ở giai đoạn này**.
4. File 05 — daemon uinput tiêm được 1 điểm chạm cố định khi nhấn 1 nút bất kỳ trên tay cầm.

**Mốc thành công giai đoạn 1**: nhấn nút A trên tay cầm → 1 chấm được "chạm" tại toạ độ cố định
trong 1 game test, đo được bằng mắt thường là có phản hồi.

## Giai đoạn 2 (2–3 tuần): Overlay + Mapping thật
5. File 03 — overlay hiện được, kéo-thả đặt vị trí nút/joystick.
6. File 06 — Mapping Engine nối input tay cầm → daemon theo đúng vị trí đã đặt trong overlay.
7. File 07 — tự nhận diện app, tự bật/tắt overlay theo đúng game.

**Mốc thành công giai đoạn 2**: chơi được trọn 1 trận game MOBA/Battle Royale hoàn toàn bằng tay
cầm vật lý, không cần chạm tay vào màn hình.

## Giai đoạn 3 (tuỳ chọn, khi đã có người dùng thật): Sản phẩm hoàn chỉnh
8. File 04 (Hướng A) — tự động hoá pairing ADB ngay trong app, bỏ bước cần máy tính.
9. File 08 — tài khoản, Credits, Pro Pass, chia sẻ config cộng đồng.
10. File 09 — test đa thiết bị kỹ càng, đóng gói phát hành.

## Rủi ro lớn nhất cần xác nhận SỚM (ngay từ Giai đoạn 1)
- `/dev/uinput` có bị chặn trên ROM bạn định hỗ trợ không (một số ROM Trung Quốc hạn chế truy
  cập ngay cả với quyền shell) — nếu bị chặn hoàn toàn, cần chuyển hướng sang lệnh `input`/
  `sendevent` chấp nhận độ trễ cao hơn.
- Game mục tiêu có cơ chế chống giả lập input không (một số SDK anti-cheat phát hiện được touch
  event không đến từ driver cảm ứng thật) — test sớm với đúng game bạn nhắm tới trước khi đầu tư
  nhiều công sức vào toàn bộ hệ thống.

---
Toàn bộ 10 file trong bộ hướng dẫn này nên được đọc theo đúng thứ tự số, mỗi file có checklist
riêng để bạn đánh dấu tiến độ.

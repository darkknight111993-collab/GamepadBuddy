# 05 — Daemon tiêm touch (MantisBuddy) — phần quan trọng nhất

## Mục tiêu
Một tiến trình chạy bằng quyền `shell` (uid 2000, cấp qua kết nối ADB ở file 04), nhận lệnh từ
app chính qua kênh cục bộ, và tiêm sự kiện chạm/phím vào hệ thống với độ trễ thấp.

## Vì sao không dùng lệnh `input tap x y` cho mỗi lần bấm?
`input tap` khởi chạy 1 tiến trình Java mới mỗi lần gọi → độ trễ 80–150ms, không chấp nhận được
cho gaming. Giải pháp đúng là **mở `/dev/uinput` một lần**, tạo virtual touchscreen device, sau
đó ghi sự kiện trực tiếp — độ trễ chỉ vài ms.

## Bước 1: Viết daemon bằng C (nhẹ, nhanh, không cần JVM)

```c
// mantisbuddy.c (rút gọn)
#include <linux/uinput.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>

int fd;

void setup_uinput_touchscreen() {
    fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    ioctl(fd, UI_SET_EVBIT, EV_ABS);
    ioctl(fd, UI_SET_ABSBIT, ABS_MT_POSITION_X);
    ioctl(fd, UI_SET_ABSBIT, ABS_MT_POSITION_Y);
    ioctl(fd, UI_SET_ABSBIT, ABS_MT_TRACKING_ID);
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    ioctl(fd, UI_SET_KEYBIT, BTN_TOUCH);
    // struct uinput_setup ... device name "mantisbuddy-touch"
    ioctl(fd, UI_DEV_SETUP, &usetup);
    ioctl(fd, UI_DEV_CREATE);
}

void emit_touch(int x, int y, int down) {
    struct input_event ev;
    // gửi ABS_MT_POSITION_X, ABS_MT_POSITION_Y, BTN_TOUCH down/up, rồi EV_SYN
    write(fd, &ev, sizeof(ev));
}

int main() {
    setup_uinput_touchscreen();
    // mở Unix domain socket lắng nghe lệnh từ app chính (file 06 sẽ gọi qua đây)
    int sock = socket(AF_UNIX, SOCK_STREAM, 0);
    // bind tới địa chỉ trừu tượng "@mantisbridge" (abstract socket — không cần file quyền)
    // loop: đọc lệnh "TAP x y" / "DOWN id x y" / "MOVE id x y" / "UP id" -> gọi emit_touch
}
```

> Đây là bản rút gọn để bạn hình dung cấu trúc — khi triển khai thật cần: multi-touch (nhiều
> `tracking_id` cho nhiều ngón/joystick cùng lúc), xử lý lỗi mở `/dev/uinput` (một số ROM chặn),
> và dọn dẹp `UI_DEV_DESTROY` khi thoát.

## Bước 2: Build daemon cho kiến trúc ARM (arm64-v8a, armeabi-v7a)

Dùng Android NDK để cross-compile:
```bash
$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang \
    -o mantisbuddy mantisbuddy.c -static
```
Đóng gói binary đã build sẵn vào `assets/mantisbuddy` trong APK (cho từng ABI).

## Bước 3: Đẩy & khởi chạy daemon qua kết nối ADB đã pairing (file 04)

```kotlin
suspend fun deployAndStartDaemon(adbConnection: AdbConnection) {
    val binary = context.assets.open("mantisbuddy").readBytes()
    adbConnection.push(binary, "/data/local/tmp/mantisbuddy")
    adbConnection.shell("chmod 755 /data/local/tmp/mantisbuddy")
    adbConnection.shell("/data/local/tmp/mantisbuddy &")  // chạy nền, giữ quyền shell
}
```

## Bước 4: App chính giao tiếp với daemon qua Local Socket

```kotlin
class DaemonClient {
    private val socket = LocalSocket()
    fun connect() = socket.connect(LocalSocketAddress("mantisbridge",
        LocalSocketAddress.Namespace.ABSTRACT))

    fun tap(x: Int, y: Int) = send("TAP $x $y")
    fun dragStart(id: Int, x: Int, y: Int) = send("DOWN $id $x $y")
    fun dragMove(id: Int, x: Int, y: Int) = send("MOVE $id $x $y")
    fun dragEnd(id: Int) = send("UP $id")

    private fun send(cmd: String) = socket.outputStream.write((cmd + "\n").toByteArray())
}
```

Mapping Engine (file 02 → 06) gọi các hàm này thay vì đụng trực tiếp vào daemon.

## Bước 5: Giữ daemon sống ổn định
- Daemon nên tự khởi động lại nếu bị kill (một số ROM MIUI/OneUI diệt tiến trình nền mạnh —
  đây là lý do màn hình bạn chụp có 2 ảnh về "MIUI Security" / can thiệp Autostart — Mantis
  hướng dẫn người dùng loại trừ app khỏi tối ưu pin/MIUI Optimization).
- App chính nên có watchdog: định kỳ ping daemon, nếu mất kết nối → tự đẩy/khởi động lại qua
  ADB connection đã lưu (không cần pairing lại, chỉ cần `connect`).

## Checklist
- [ ] Daemon build chạy được qua `adb shell`, tạo được virtual touch device (`getevent -l` thấy
      device mới xuất hiện).
- [ ] Tiêm được 1 cú tap vào toạ độ bất kỳ, xác nhận bằng cách tap vào 1 app test.
- [ ] Multi-touch: tiêm được 2 điểm chạm đồng thời (vd 2 joystick ảo).
- [ ] Local Socket giao tiếp 2 chiều ổn định, độ trễ < 20ms.
- [ ] Daemon tự hồi phục sau khi bị kill/mất kết nối.

Tiếp theo: **06-HE-THONG-PROFILE.md**

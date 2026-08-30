/*
 * MantisBuddy daemon (file 05 - Daemon tiêm touch).
 *
 * Chạy bằng quyền shell (uid 2000) cấp qua kết nối ADB (file 04).
 *   - Tạo virtual multitouch device qua /dev/uinput (1 lần, độ trễ vài ms).
 *   - Lắng nghe lệnh từ app chính qua abstract Unix domain socket "@mantisbridge".
 *
 * Giao thức lệnh (mỗi dòng 1 lệnh, kết thúc bằng '\n'):
 *   TAP  x y        -> chạm nhanh tại (x, y)
 *   DOWN id x y      -> bắt đầu chạm (pointer id = 0..MAX_SLOTS-1)
 *   MOVE id x y      -> di chuyển chạm đang giữ
 *   UP   id          -> kết thúc chạm
 *   PING             -> daemon trả lời "PONG"
 *
 * Build (file 05 - Bước 2) và deploy (file 04 Hướng B) xem scripts/ .
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#define PORT         13579
#define MAX_X        1080
#define MAX_Y        2400
#define MAX_SLOTS    10

static int uinput_fd = -1;

/* ---------- uinput setup ---------- */

static void die(const char *msg) {
    perror(msg);
    exit(1);
}

static void emit(int type, int code, int value) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type = (uint16_t)type;
    ev.code = (uint16_t)code;
    ev.value = value;
    if (write(uinput_fd, &ev, sizeof(ev)) < 0)
        perror("write uinput");
}

static void syn(void) {
    emit(EV_SYN, SYN_REPORT, 0);
}

static void set_abs(int code, int min, int max) {
    struct uinput_abs_setup s;
    memset(&s, 0, sizeof(s));
    s.code = (uint16_t)code;
    s.absinfo.minimum = min;
    s.absinfo.maximum = max;
    if (ioctl(uinput_fd, UI_ABS_SETUP, &s) < 0)
        die("UI_ABS_SETUP");
}

static void setup_uinput(void) {
    struct uinput_setup usetup;

    uinput_fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (uinput_fd < 0) die("open /dev/uinput");

    ioctl(uinput_fd, UI_SET_EVBIT, EV_KEY);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_TOUCH);
    ioctl(uinput_fd, UI_SET_KEYBIT, BTN_TOOL_FINGER);

    ioctl(uinput_fd, UI_SET_EVBIT, EV_ABS);
    ioctl(uinput_fd, UI_SET_ABSBIT, ABS_MT_POSITION_X);
    ioctl(uinput_fd, UI_SET_ABSBIT, ABS_MT_POSITION_Y);
    ioctl(uinput_fd, UI_SET_ABSBIT, ABS_MT_TRACKING_ID);
    ioctl(uinput_fd, UI_SET_ABSBIT, ABS_X);
    ioctl(uinput_fd, UI_SET_ABSBIT, ABS_Y);

    set_abs(ABS_MT_POSITION_X, 0, MAX_X);
    set_abs(ABS_MT_POSITION_Y, 0, MAX_Y);
    set_abs(ABS_MT_TRACKING_ID, 0, MAX_SLOTS - 1);
    set_abs(ABS_X, 0, MAX_X);
    set_abs(ABS_Y, 0, MAX_Y);

    memset(&usetup, 0, sizeof(usetup));
    usetup.id.bustype = BUS_VIRTUAL;
    usetup.id.vendor  = 0x1234;
    usetup.id.product = 0x5678;
    usetup.id.version = 1;
    strncpy(usetup.name, "mantisbuddy-touch", UINPUT_MAX_NAME_SIZE - 1);

    if (ioctl(uinput_fd, UI_DEV_SETUP, &usetup) < 0) die("UI_DEV_SETUP");
    if (ioctl(uinput_fd, UI_DEV_CREATE) < 0)     die("UI_DEV_CREATE");
}

static void cleanup(void) {
    if (uinput_fd >= 0) {
        ioctl(uinput_fd, UI_DEV_DESTROY);
        close(uinput_fd);
        uinput_fd = -1;
    }
}

/* ---------- multitouch (Protocol B, slot-based) ---------- */

static void touch_down(int id, int x, int y) {
    if (id < 0 || id >= MAX_SLOTS) return;
    emit(EV_ABS, ABS_MT_SLOT, id);
    emit(EV_ABS, ABS_MT_TRACKING_ID, id);
    emit(EV_ABS, ABS_MT_POSITION_X, x);
    emit(EV_ABS, ABS_MT_POSITION_Y, y);
    emit(EV_KEY, BTN_TOUCH, 1);
    syn();
}

static void touch_move(int id, int x, int y) {
    if (id < 0 || id >= MAX_SLOTS) return;
    emit(EV_ABS, ABS_MT_SLOT, id);
    emit(EV_ABS, ABS_MT_POSITION_X, x);
    emit(EV_ABS, ABS_MT_POSITION_Y, y);
    syn();
}

static void touch_up(int id) {
    if (id < 0 || id >= MAX_SLOTS) return;
    emit(EV_ABS, ABS_MT_SLOT, id);
    emit(EV_ABS, ABS_MT_TRACKING_ID, -1);
    emit(EV_KEY, BTN_TOUCH, 0);
    syn();
}

static void do_tap(int x, int y) {
    touch_down(0, x, y);
    usleep(30000);
    touch_up(0);
}

/* ---------- lệnh từ app ---------- */

static void handle_line(int client_fd, const char *line) {
    int id, x, y;
    if (strcmp(line, "PING") == 0) {
        const char *pong = "PONG\n";
        write(client_fd, pong, (unsigned)strlen(pong));
        return;
    }
    if (sscanf(line, "TAP %d %d", &x, &y) == 2) {
        do_tap(x, y);
    } else if (sscanf(line, "DOWN %d %d %d", &id, &x, &y) == 3) {
        touch_down(id, x, y);
    } else if (sscanf(line, "MOVE %d %d %d", &id, &x, &y) == 3) {
        touch_move(id, x, y);
    } else if (sscanf(line, "UP %d", &id) == 1) {
        touch_up(id);
    }
}

static void handle_client(int client_fd) {
    char buf[256];
    int i = 0, n;
    while ((n = (int)read(client_fd, buf + i, sizeof(buf) - i - 1)) > 0) {
        i += n;
        buf[i] = '\0';

        char *p = buf;
        char *nl;
        while ((nl = strchr(p, '\n')) != NULL) {
            *nl = '\0';
            if (nl > p && nl[-1] == '\r') nl[-1] = '\0';
            handle_line(client_fd, p);
            p = nl + 1;
        }
        /* giữ phần chưa kết thúc dòng */
        memmove(buf, p, strlen(p) + 1);
        i = (int)strlen(buf);
    }
}

/* ---------- main ---------- */

int main(void) {
    atexit(cleanup);
    setup_uinput();

    int server = socket(AF_INET, SOCK_STREAM, 0);
    if (server < 0) die("socket");

    int opt = 1;
    setsockopt(server, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = inet_addr("127.0.0.1");
    addr.sin_port = htons(PORT);

    if (bind(server, (struct sockaddr *)&addr, sizeof(addr)) < 0) die("bind");
    if (listen(server, 5) < 0) die("listen");

    printf("[mantisbuddy] ready on 127.0.0.1:%d (virtual touch %dx%d)\n", PORT, MAX_X, MAX_Y);
    fflush(stdout);

    for (;;) {
        int client = accept(server, NULL, NULL);
        if (client < 0) { perror("accept"); continue; }
        handle_client(client);
        close(client);
    }
    return 0;
}

package com.gamepadbuddy.profile

import android.view.Surface

/**
 * Bug fix: chuyển toạ độ "logic" (không gian hiển thị hiện tại — overlay đang vẽ nút ở đâu,
 * thường là NGANG khi chơi game) sang toạ độ "raw" của tấm cảm ứng vật lý.
 *
 * Vì sao cần bước này:
 * Driver cảm ứng luôn báo toạ độ thô (raw) theo hướng DỌC tự nhiên gốc của panel, bất kể UI
 * đang xoay ngang hay dọc — đây cũng là lý do daemon (app/src/main/cpp/mantisbuddy.c) khai báo
 * ABS_MT_POSITION_X/Y cố định 1080x2400 (RAW_PORTRAIT_W/H bên dưới PHẢI khớp với 2 hằng số
 * MAX_X/MAX_Y trong file .c đó).
 *
 * Khi hệ thống nhận MotionEvent thật từ cảm ứng, Android tự áp ma trận xoay raw -> logic cho
 * mình. Nhưng khi TỰ TIÊM sự kiện qua uinput (giả lập cảm ứng), ta phải tự làm NGƯỢC LẠI: từ
 * toạ độ logic (nơi overlay đang vẽ nút, không gian ngang khi chơi game) suy ra toạ độ raw để
 * daemon ghi vào /dev/uinput. Trước đây code chỉ scale tuyến tính x->x, y->y mà không đổi trục,
 * nên khi chơi ở chế độ ngang, chạm bị lệch hẳn trục (bấm ngang lại tạo ra chạm dọc) — đây là
 * nguyên nhân chính khiến app "kết nối/pair xong hết" nhưng điều khiển không đúng trong game.
 */
object CoordinateRotation {

    /** Phải khớp với #define MAX_X / MAX_Y trong app/src/main/cpp/mantisbuddy.c */
    const val RAW_PORTRAIT_W = 1080
    const val RAW_PORTRAIT_H = 2400

    /**
     * @param logicalX/Y toạ độ px trong không gian đang hiển thị hiện tại (overlay).
     * @param logicalW/H kích thước không gian hiển thị hiện tại
     *        (resources.displayMetrics.widthPixels/heightPixels tại thời điểm gọi).
     * @param rotation Surface.ROTATION_0/90/180/270 (WindowManager.defaultDisplay.rotation).
     * @return toạ độ (x,y) trong không gian raw 1080x2400 mà daemon hiểu.
     */
    fun logicalToRaw(
        logicalX: Float,
        logicalY: Float,
        logicalW: Int,
        logicalH: Int,
        rotation: Int
    ): Pair<Int, Int> {
        if (logicalW <= 0 || logicalH <= 0) return 0 to 0

        // Chuẩn hoá về tỉ lệ [0,1] trước để công thức không phụ thuộc độ phân giải thật.
        val fx = (logicalX / logicalW).coerceIn(0f, 1f)
        val fy = (logicalY / logicalH).coerceIn(0f, 1f)

        // Suy ra ngược từ công thức xoay-theo-chiều-kim-đồng-hồ chuẩn của Android
        // (logic = raw xoay `rotation` độ theo chiều kim đồng hồ quanh tâm ảnh).
        val (rawFx, rawFy) = when (rotation) {
            Surface.ROTATION_0 -> fx to fy
            Surface.ROTATION_90 -> fy to (1f - fx)
            Surface.ROTATION_180 -> (1f - fx) to (1f - fy)
            Surface.ROTATION_270 -> (1f - fy) to fx
            else -> fx to fy
        }

        return (rawFx * RAW_PORTRAIT_W).toInt() to (rawFy * RAW_PORTRAIT_H).toInt()
    }
}

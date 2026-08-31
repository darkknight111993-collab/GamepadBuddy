package com.gamepadbuddy.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gamepadbuddy.MainActivity
import com.gamepadbuddy.daemon.DaemonClient
import com.gamepadbuddy.detector.AppDetector
import com.gamepadbuddy.detector.DetectorState
import com.gamepadbuddy.input.GamepadEvent
import com.gamepadbuddy.profile.CoordinateMapper
import com.gamepadbuddy.profile.CoordinateRotation
import com.gamepadbuddy.profile.MappedWidget
import com.gamepadbuddy.profile.MappingEngine
import com.gamepadbuddy.profile.Profile
import com.gamepadbuddy.profile.ProfileRepository
import com.gamepadbuddy.pairing.PairingCoordinator
import com.gamepadbuddy.pairing.PairingNotifications
import com.gamepadbuddy.pairing.PairingStore
import com.gamepadbuddy.pairing.WirelessDebugging

/**
 * Service nền "trái tim" (file 01 + 03 + 06 + 07): vẽ overlay, giữ daemon, dịch input tay cầm
 * qua MappingEngine, và tự switch profile theo app foreground (file 07).
 *
 * - Play Mode: overlay FLAG_NOT_FOCUSABLE → game vẫn nhận touch; gamepad điều khiển tiêm chạm.
 * - Edit Mode: bỏ FLAG_NOT_FOCUSABLE → overlay bắt touch để kéo-thả widget.
 */
class OverlayService : Service() {

    companion object {
        /** UI dùng để biết service (và MappingEngine/daemon) có đang chạy hay không. */
        @Volatile var isRunning = false
        /** UI dùng để biết daemon (LocalSocket @mantisbridge) đã kết nối thành công hay chưa. */
        @Volatile var isDaemonConnected = false
    }

    private lateinit var wm: WindowManager
    private lateinit var root: OverlayRootView
    private lateinit var keyCapture: KeyCaptureWindow
    private val daemon = DaemonClient()
    private var daemonThread: Thread? = null
    private var redeployTriggered = false
    private lateinit var repo: ProfileRepository
    private var engine: MappingEngine? = null
    private var editMode = false
    private var activePackage: String? = ""
    private var lockedPackage: String? = null
    /** id widget nút đang chờ bắt phím vật lý; null nghĩa là không ở chế độ gán. */
    private var pendingBindButtonId: String? = null
    private var bindOverlay: FloatingBindOverlay? = null

    private val NOTIF_ID = 1001
    private val CHANNEL_ID = "overlay_channel"
    private val DETECT_INTERVAL = 1500L
    /** Khoảng thời gian retry deploy + reconnect daemon khi chưa kết nối được. */
    private val REDEPLOY_INTERVAL = 15000L

    // Bug fix: scale tuyến tính x->x, y->y (bản cũ) không đổi trục khi màn hình đang NGANG,
    // khiến chạm bị lệch trục 90 độ so với vị trí overlay thật sự vẽ ra. Dùng
    // CoordinateRotation để quy đổi đúng theo hướng xoay hiện tại trước khi gửi cho daemon.
    private val mapper = object : CoordinateMapper {
        override fun toDaemon(x: Float, y: Float): Pair<Int, Int> {
            @Suppress("DEPRECATION")
            val rotation = wm.defaultDisplay.rotation
            return CoordinateRotation.logicalToRaw(
                x, y,
                resources.displayMetrics.widthPixels,
                resources.displayMetrics.heightPixels,
                rotation
            )
        }
    }

    private val reloadReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == DetectorState.ACTION_RELOAD) onForegroundChanged(activePackage)
        }
    }
    private val editReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == DetectorState.ACTION_EDIT_PROFILE) enterEditModeFor(i?.getStringExtra("package"))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val detectRunnable = object : Runnable {
        override fun run() {
            val pkg = AppDetector.getForegroundPackage(this@OverlayService)
            if (pkg != activePackage) onForegroundChanged(pkg)
            handler.postDelayed(this, DETECT_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForeground(NOTIF_ID, buildNotification())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        WirelessDebugging.register(this)
        repo = ProfileRepository(this)

        root = OverlayRootView(this, object : OverlayRootView.RootCallbacks {
            override fun onToggleEdit() = toggleEditMode()
            override fun onAddButton() = addButtonWidget()
            override fun onAddJoystick() = addJoystickWidget()
            override fun onClose() = stopSelf()
            override fun onBindButton(widgetId: String) = startBindButton(widgetId)
            override fun onBindJoystick(widgetId: String) = startBindJoystick(widgetId)
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            playFlags(),
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(root, params)
        root.tag = params
        // Ẩn overlay cho tới khi phát hiện app có profile (tránh hiện toolbar 4 nút trên màn hình chính).
        root.visibility = View.INVISIBLE

        keyCapture = KeyCaptureWindow(
            this,
            { code, down -> feedButton(code, down) },
            { lx, ly -> feedAxis(lx, ly) }
        )
        keyCapture.show()

        // Giữ daemon luôn kết nối: thử connect, thấy fail thì retry định kỳ.
        // Nếu thiết bị đã từng ghép nối (có khoá ADB) thì chủ động deploy lại daemon
        // qua wireless debugging; ngược lại post notification hướng user vào Pairing.
        daemonThread = Thread {
            while (!Thread.currentThread().isInterrupted) {
                if (isDaemonConnected) { Thread.sleep(2000); continue }
                val ok = runCatching { daemon.connect() }.getOrDefault(false)
                isDaemonConnected = ok
                if (ok) {
                    redeployTriggered = false
                    PairingNotifications.cancelShortcut(this@OverlayService)
                    Thread.sleep(2000); continue
                }
                // Đã từng ghép nối → thử deploy + reconnect định kỳ (KHÔNG chốt cờ sớm) cho tới
                // khi isDaemonConnected = true. Nếu deploy thất bại (mDNS/push lỗi), vòng lặp sẽ
                // tự thử lại sau REDEPLOY_INTERVAL thay vì bỏ cuộc vĩnh viễn.
                if (PairingStore.hasValidKey(this@OverlayService)) {
                    redeployTriggered = true
                    PairingCoordinator.onWirelessActive(this@OverlayService)
                } else {
                    PairingNotifications.postShortcut(this@OverlayService)
                }
                Thread.sleep(REDEPLOY_INTERVAL)
            }
        }.apply { name = "daemon-reconnect"; start() }
        ContextCompat.registerReceiver(
            this, reloadReceiver, IntentFilter(DetectorState.ACTION_RELOAD),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this, editReceiver, IntentFilter(DetectorState.ACTION_EDIT_PROFILE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        handler.post(detectRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        isDaemonConnected = false
        daemonThread?.interrupt()
        daemonThread = null
        super.onDestroy()
        handler.removeCallbacks(detectRunnable)
        runCatching { unregisterReceiver(reloadReceiver) }
        runCatching { unregisterReceiver(editReceiver) }
        runCatching { wm.removeView(root) }
        bindOverlay?.dismiss()
        WirelessDebugging.unregister(this)
        keyCapture.hide()
        daemon.close()
    }

    private fun playFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun editFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun toggleEditMode() = setEditMode(!editMode)

    private fun setEditMode(on: Boolean) {
        if (!on) {
            lockedPackage = null
            pendingBindButtonId = null
            bindOverlay?.dismiss()
            bindOverlay = null
        }
        editMode = on
        root.editMode = on
        val params = root.tag as? WindowManager.LayoutParams
        if (params != null) {
            params.flags = if (on) editFlags() else playFlags()
            wm.updateViewLayout(root, params)
        }
    }

    private fun enterEditModeFor(pkg: String?) {
        if (pkg == null) return
        val profile = repo.getForPackage(pkg) ?: Profile("edit_$pkg", pkg, pkg, emptyList())
        activePackage = pkg
        lockedPackage = pkg
        setActiveProfile(profile)
        root.visibility = View.VISIBLE
        setEditMode(true)
    }

    private fun feedButton(code: Int, down: Boolean) {
        // Nếu đang chờ gán 1 nút vật lý (bấm vào widget trong Edit Mode) -> nút KẾ TIẾP nhấn
        // xuống sẽ được bắt vào panel gán, KHÔNG chạy vào MappingEngine bình thường.
        if (pendingBindButtonId != null) {
            if (down) {
                pendingBindButtonId = null
                bindOverlay?.onKeyCaptured(code)
            }
            return
        }
        if (editMode) return
        engine?.onGamepadEvent(GamepadEvent.Button(code, down))
    }

    private fun feedAxis(lx: Float, ly: Float) {
        if (editMode) return
        engine?.onGamepadEvent(GamepadEvent.Axis(lx, ly, 0f, 0f, 0f, 0f))
    }

    /* ---------- File 07: tự nhận diện app & load profile ---------- */

    private fun onForegroundChanged(pkg: String?) {
        if (lockedPackage != null) return
        activePackage = pkg
        if (pkg != null && pkg != packageName) DetectorState.lastGamePackage = pkg
        val profile = if (pkg != null && pkg != packageName) repo.getForPackage(pkg) else null
        if (profile != null) {
            setActiveProfile(profile)
            root.visibility = View.VISIBLE
        } else {
            // Không có profile cho app này → ẩn overlay (tránh đè lên Home/app khác)
            engine = null
            root.clearWidgets()
            root.visibility = View.INVISIBLE
        }
    }

    private fun setActiveProfile(profile: Profile) {
        engine = MappingEngine(daemon, profile, mapper)
        buildWidgets(profile)
    }

    private fun buildWidgets(profile: Profile) {
        root.clearWidgets()
        for (w in profile.widgets) {
            when (w) {
                is MappedWidget.Button ->
                    root.addButton(w) { x, y -> w.x = x; w.y = y; repo.save(profile) }
                is MappedWidget.Joystick ->
                    root.addJoystick(w,
                        onPos = { x, y -> w.x = x; w.y = y; repo.save(profile) },
                        onMove = { _, _ -> }, onRelease = {})
            }
        }
    }

    private fun addButtonWidget() {
        val p = engine?.getProfile() ?: return
        val dm = resources.displayMetrics
        val w = MappedWidget.Button("btn_${System.currentTimeMillis()}", dm.widthPixels / 2f, dm.heightPixels / 2f, 0)
        val np = p.copy(widgets = p.widgets + w)
        engine?.setProfile(np)
        repo.save(np)
        buildWidgets(np)
    }

    private fun addJoystickWidget() {
        val p = engine?.getProfile() ?: return
        val dm = resources.displayMetrics
        val w = MappedWidget.Joystick("js_${System.currentTimeMillis()}", dm.widthPixels / 2f, dm.heightPixels / 2f, 100f, com.gamepadbuddy.profile.AxisGroup.LEFT_STICK)
        val np = p.copy(widgets = p.widgets + w)
        engine?.setProfile(np)
        repo.save(np)
        buildWidgets(np)
    }

    /* ---------- Gán nút/cần vật lý (chạm widget ở Edit Mode) ---------- */

    private fun startBindButton(widgetId: String) {
        val profile = engine?.getProfile() ?: return
        val idx = profile.widgets.filterIsInstance<MappedWidget.Button>().indexOfFirst { it.id == widgetId }
        val label = "Nút ${idx + 1}"

        bindOverlay?.dismiss()
        pendingBindButtonId = widgetId
        bindOverlay = FloatingBindOverlay.forButton(
            context = this,
            widgetLabel = label,
            onBound = { keyCode ->
                updateProfile { p ->
                    p.copy(widgets = p.widgets.map { w ->
                        if (w is MappedWidget.Button && w.id == widgetId) w.copy(boundKeyCode = keyCode) else w
                    })
                }
                bindOverlay = null
            },
            onCancel = {
                pendingBindButtonId = null
                bindOverlay = null
            }
        )
        bindOverlay?.show()
    }

    private fun startBindJoystick(widgetId: String) {
        val profile = engine?.getProfile() ?: return
        val idx = profile.widgets.filterIsInstance<MappedWidget.Joystick>().indexOfFirst { it.id == widgetId }
        val label = "Cần ${idx + 1}"

        bindOverlay?.dismiss()
        bindOverlay = FloatingBindOverlay.forJoystick(
            context = this,
            widgetLabel = label,
            onChosen = { group ->
                updateProfile { p ->
                    p.copy(widgets = p.widgets.map { w ->
                        if (w is MappedWidget.Joystick && w.id == widgetId) w.copy(axisGroup = group) else w
                    })
                }
                bindOverlay = null
            },
            onCancel = { bindOverlay = null }
        )
        bindOverlay?.show()
    }

    /** Áp transform lên Profile đang active, lưu xuống repo, và vẽ lại toàn bộ widget. */
    private fun updateProfile(transform: (Profile) -> Profile) {
        val p = engine?.getProfile() ?: return
        val np = transform(p)
        engine?.setProfile(np)
        repo.save(np)
        buildWidgets(np)
    }

    /* ---------- Notification (bắt buộc cho Foreground Service) ---------- */

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "GamepadBuddy Overlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GamepadBuddy")
            .setContentText("Overlay đang chạy")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .build()
    }
}

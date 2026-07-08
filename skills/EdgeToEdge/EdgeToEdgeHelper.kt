package com.pdf.word.utils


import android.R
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import android.widget.RelativeLayout
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.navigation.NavigationView
import com.google.android.material.shape.MaterialShapeDrawable
import timber.log.Timber
import java.util.function.Supplier

private const val STATUS_BG_VIEW_TAG = "status_bar_bg"

// region public functions
fun Resources.isDarkMode(): Boolean =
    (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

fun Resources.Theme.isDarkMode(): Boolean? =
    obtainStyledAttributes(intArrayOf(R.attr.windowLightStatusBar))
        .takeIf { it.hasValue(0) }
        ?.getBoolean(0, false)

/**
 * Needs to be called before Activity super.onCreate, for enableEdgeToEdge call!
 *
 * Enables edge-to-edge, with status bar content (text/icon) color for the given [statusBarBgColor].
 *
 * @param statusBarBgColor for determining status bar content (text/icon) color (dark vs light).
 * If null or transparent, it will set status bar text color per if dark mode, from [theme] if not null, or from resources.
 * @param theme if [statusBarBgColor] is null or transparent, then it is used (if set = not null) to determine
 * status bar text color (dark vs light). If [statusBarBgColor] is null or transparent and [theme] is null,
 * system [Resources] is used to determine if dark mode. Default is the Activity's theme.
 */
@JvmOverloads
fun ComponentActivity.enableEdgeToEdge(
    @ColorInt statusBarBgColor: Int? = null,
    theme: Resources.Theme? = this.theme,
) {
    @Suppress("DEPRECATION")
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    val systemBarStyle = getNoScrimSystemBarStyleForColor(statusBarBgColor, theme)
    enableEdgeToEdge(systemBarStyle)
}

/**
 * Needs to be called before Activity super.onCreate, for enableEdgeToEdge call!
 * Enables edge-to-edge, adds a view behind status bar to set background color,
 * from global layout listener, to match top view color, if found.
 *
 * @param theme used only if failed to auto find top view color,
 * for determining light/dark status bar content color. Default is the activity's theme.
 * If null, system [Resources] light/dark mode is used to determine content color.
 */
@JvmOverloads
fun ComponentActivity.handleEdgeToEdgeAuto(
    theme: Resources.Theme? = this.theme,
) {
    handleEdgeToEdgeInternal(statusBarBgColor = null, theme, setAutoHandler = true, enabler = null)
}

/**
 * Needs to be called before Activity super.onCreate, for enableEdgeToEdge call!
 * Enables edge-to-edge, adds a view behind status bar to set background color,
 * from global layout listener, to match top view color, if found.
 *
 * @param enabler can be used to switch on/off the status bar background view and color setting,
 * if you need custom handling in some place.
 */
fun ComponentActivity.handleEdgeToEdgeAuto(enabler: Supplier<Boolean>) {
    handleEdgeToEdgeInternal(statusBarBgColor = null, theme = null, setAutoHandler = true, enabler)
}

/**
 * Needs to be called before Activity super.onCreate, for enableEdgeToEdge call!
 * Enables edge-to-edge, adds a view behind status bar to set background color,
 * from global layout listener, to match top view color, if found.
 *
 * @param enabler can be used to switch on/off the status bar background view and color setting,
 * if you need custom handling in some place.
 * @param theme used only if failed to auto find top view color, for determining light/dark status bar content color.
 */
fun ComponentActivity.handleEdgeToEdgeAuto(
    enabler: Supplier<Boolean>,
    theme: Resources.Theme,
) {
    handleEdgeToEdgeInternal(statusBarBgColor = null, theme, setAutoHandler = true, enabler)
}

/**
 * Needs to be called before Activity super.onCreate, for enableEdgeToEdge call!
 * Enables edge-to-edge, adds a view with [statusBarBgColor] behind status bar,
 * and sets status bar content (text) color for the given [statusBarBgColor].
 */
fun ComponentActivity.handleEdgeToEdge(@ColorInt statusBarBgColor: Int) {
    enableEdgeToEdge(statusBarBgColor, theme = null)
    handleEdgeToEdgeInternal(statusBarBgColor, theme = null, setAutoHandler = false, enabler = null)
}

/**
 * @param statusBarBgColor determines status-bar content (text) color (dark vs light), if not transparent.
 * If transparent, it will use [theme] (if not null) or system [Resources] to determine if dark mode is on or off.
 * @param theme for determining light/dark mode, if [statusBarBgColor] is transparent. Default is the activity's theme.
 * If null, system [Resources] to determine if dark mode is on or off.
 * @return [SystemBarStyle] for use in [ComponentActivity].[enableEdgeToEdge].
 */
@JvmOverloads
fun ComponentActivity.getNoScrimSystemBarStyleForColor(
    @ColorInt statusBarBgColor: Int?,
    theme: Resources.Theme? = this.theme,
): SystemBarStyle {
    val isDarkColor = isLightColor(statusBarBgColor)?.not()
    return SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { resources ->
        isDarkColor ?: theme?.isDarkMode() ?: resources.isDarkMode()
    }
}

/**
 * @param theme for determining light/dark mode, if [statusBarBgColor] is transparent. Default is the activity's theme.
 * If null, system [Resources] to determine if dark mode is on or off.
 */
@JvmOverloads
fun ComponentActivity.setStatusBarViewBgColor(theme: Resources.Theme? = this.theme) {
    setStatusBarViewBgColorEnsured(theme = theme, enabler = null, statusBarBgColor = null)
}

/**
 * Sets background color to the view added behind status bar previously, if enabled.
 * It also set the status bar content (text) color to dark or light, depending on the [statusBarBgColor].
 */
fun ComponentActivity.setStatusBarViewBgColor(@ColorInt statusBarBgColor: Int) {
    setStatusBarViewBgColorEnsured(theme = null, enabler = null, statusBarBgColor)
}
// endregion public functions

// region edge to edge handling
/**
 * Sets window insets listener - if [setAutoHandler] is true it keeps in until destroy, otherwise fires once.
 * On window insets, it adds a view behind status bar to set background color,
 * and add global layouts listener to update the color (fires once - removes itself after first call).
 */
private fun ComponentActivity.handleEdgeToEdgeInternal(
    @ColorInt statusBarBgColor: Int?,
    theme: Resources.Theme?,
    setAutoHandler: Boolean,
    enabler: Supplier<Boolean>?,
) {
    enableEdgeToEdge(statusBarBgColor, theme)
    lifecycle.addObserver(
        observer = object : DefaultLifecycleObserver {
            val layoutListener = object: ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    window?.decorView?.aliveViewTreeObserver?.removeOnGlobalLayoutListener(this) ?: return
                    setStatusBarViewBgColorEnsured(theme, enabler, statusBarBgColor = null)
                }
            }

            override fun onCreate(owner: LifecycleOwner) {
                val decorView = window?.decorView ?: return
                (owner as ComponentActivity).setDecorWindowInsetsListener(decorView)
                ViewCompat.requestApplyInsets(decorView)
            }

            /** As the listener is set and not added, we need to make sure to re-set it, if set somewhere else. */
            override fun onResume(owner: LifecycleOwner) {
                (owner as ComponentActivity).setDecorWindowInsetsListener()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                val decorView = (owner as ComponentActivity).window?.decorView ?: return
                decorView.setOnApplyWindowInsetsListener(null)
                decorView.aliveViewTreeObserver?.removeOnGlobalLayoutListener(layoutListener)
            }

            private fun ComponentActivity.setDecorWindowInsetsListener(
                decorView: View? = window?.decorView
            ) {
                decorView ?: return
                ViewCompat.setOnApplyWindowInsetsListener(decorView) { view, windowInsets ->
                    if (!setAutoHandler) view.setOnApplyWindowInsetsListener(null)
                    if (!isFinishing && !isDestroyed) {
                        handleInsets(statusBarBgColor, theme, enabler, windowInsets)
                        view.aliveViewTreeObserver?.addOnGlobalLayoutListener(layoutListener)
                    }
                    WindowInsetsCompat.CONSUMED
                }
            }
        }
    )
}

/**
 * Sets background color to the view added behind status bar previously, if enabled.
 * It also set the status bar content (text) color to dark or light, depending on the [statusBarBgColor],
 * or [theme] if [statusBarBgColor] is null or transparent.
 */
private fun ComponentActivity.setStatusBarViewBgColorEnsured(
    theme: Resources.Theme?,
    enabler: Supplier<Boolean>?,
    @ColorInt statusBarBgColor: Int?,
) {
    if (isFinishing || isDestroyed) return
    setStatusBarViewBgColor(theme, enabler, statusBarBgColor)
    window.decorView.post {
        if (isFinishing || isDestroyed) return@post
        setStatusBarViewBgColor(theme, enabler, statusBarBgColor)
    }
}

/**
 * Sets background color to the view added behind status bar previously, if enabled.
 * It also set the status bar content (text) color to dark or light, depending on the [statusBarBgColor],
 * or [theme] if [statusBarBgColor] is null or transparent.
 */
private fun ComponentActivity.setStatusBarViewBgColor(
    theme: Resources.Theme?,
    enabler: Supplier<Boolean>?,
    @ColorInt statusBarBgColor: Int? = null,
): Boolean {
    if (enabler?.get() == false) return false
    val contentView = findViewById<ViewGroup?>(R.id.content) ?: return false
    val windowInsets = ViewCompat.getRootWindowInsets(contentView)
    val topLayout: ViewGroup = contentView.children.firstOrNull() as? ViewGroup ?: return false
    val systemInsets = windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars())
    val statusBarHeight = systemInsets?.getStatusBarHeightCompat(this) ?: 0
    val statusBgColor = statusBarBgColor ?: topLayout.findTopColor(statusBarHeight) ?: Color.TRANSPARENT

    val addedView = contentView.findViewWithTag<View>(STATUS_BG_VIEW_TAG) ?: return false

    val existingColor = addedView.extractColor()
    if (existingColor == null || existingColor != statusBarBgColor) {
        if (statusBarBgColor == Color.TRANSPARENT || isLightColor(statusBarBgColor) != isLightColor(existingColor)) {
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = isLightColor(statusBgColor, theme)
        }
        addedView.setBackgroundColor(statusBgColor)
    }
    return true
}

/**
 * If status bar background view was not added yet, it add it, with the given [statusBarBgColor];
 * otherwise it just updates the background color of the existing view.
 * Also disables fistSystemWindows for all top views,
 * @return true if new view was inserted.
 */
private fun ComponentActivity.handleInsets(
    statusBarBgColor: Int?,
    theme: Resources.Theme?,
    enabler: Supplier<Boolean>?,
    windowInsets: WindowInsetsCompat?
): Boolean {
    val contentView = findViewById<ViewGroup>(R.id.content) ?: return false
    val systemInsets = windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars()) ?: return false
    val statusBarHeight = systemInsets.getStatusBarHeightCompat(this)
    val addedView = contentView.findViewWithTag<View>(STATUS_BG_VIEW_TAG)
    if (addedView != null) {
        setStatusBarViewBgColorEnsured(theme, enabler, statusBarBgColor)
        return false
    }
    val topLayout = contentView.children.firstOrNull() as? ViewGroup ?: return false
    val bgColor = statusBarBgColor ?: topLayout.findTopColor(statusBarHeight) ?: Color.TRANSPARENT
    return setPaddingForInsetsAndInsertView(systemInsets, bgColor, contentView, theme, enabler)
}

/**
 * If view was inserted, it also sets status bar content (text/icon) color to light/dark,
 * depending on the [statusBarBgColor].
 * @return true if new view was inserted.
 */
private fun ComponentActivity.setPaddingForInsetsAndInsertView(
    systemInsets: Insets?,
    @ColorInt statusBarBgColor: Int,
    contentView: ViewGroup = findViewById(R.id.content),
    theme: Resources.Theme?,
    enabler: Supplier<Boolean>?,
): Boolean {
    if (systemInsets == null) return false
    val statusBarHeight = systemInsets.getStatusBarHeightCompat(this)
    with(systemInsets) {
        if (statusBarHeight == 0) {
            contentView.updatePadding(top = top, bottom = bottom, left = left, right = right)
            return false
        }

        val bgColor = if (enabler == null || enabler.get()) statusBarBgColor else Color.TRANSPARENT
        if (insertStatusBarBackgroundView(contentView, bgColor, statusBarHeight)) {
            contentView.updatePadding(top = 0, bottom = bottom, left = left, right = right)
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = isLightColor(bgColor, theme)
            return true
        } else {
            Timber.e("Failed to add view for status bar background color!")
            return false
        }
    }
}

/** @return true if new view was inserted. */
private fun insertStatusBarBackgroundView(
    contentView: ViewGroup,
    @ColorInt statusBarBgColor: Int,
    statusBarHeight: Int,
): Boolean {
    val topLayout: ViewGroup = contentView.children.firstOrNull() as? ViewGroup ?: return false
    topLayout.fitsSystemWindows = false
    val statusBarBgView = View(contentView.context).apply {
        setBackgroundColor(statusBarBgColor)
        tag = STATUS_BG_VIEW_TAG
    }
    return topLayout.insertTopView(statusBarBgView, statusBarHeight)
}

private fun Context.isLightColor(@ColorInt color: Int, theme: Resources.Theme?): Boolean =
    isLightColor(color) ?: (theme?.isDarkMode() ?: resources.isDarkMode()).not()

private fun isLightColor(@ColorInt color: Int?): Boolean? =
    if (color == null || color == Color.TRANSPARENT) null
    else ColorUtils.calculateLuminance(color) > 0.5

private val View.aliveViewTreeObserver: ViewTreeObserver?
    get() = viewTreeObserver?.takeIf { it.isAlive }

private fun Insets.getStatusBarHeightCompat(context: Context): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return top
    if (top < 100) return top
    val legacyHeight = getStatusBarHeightLegacy(context.resources)
    return if (legacyHeight > 0) legacyHeight else top
}

private fun getStatusBarHeightLegacy(resources: Resources): Int {
    var statusBarHeight = 0
    val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
    if (resourceId > 0) {
        statusBarHeight = resources.getDimensionPixelSize(resourceId)
    }
    return statusBarHeight
}
// endregion edge to edge handling

// region status bar color finding
/**
 * Finds the color of the topmost view that is aligned to the top of this ViewGroup, if there is one, with color set.
 * The topmost view is determined by the following criteria:
 * 1. Depth in the view hierarchy (deeper views take precedence).
 * 2. Elevation (higher elevation takes precedence among views at the same depth).
 * 3. Z-order (later added views take precedence among views with the same depth and elevation).
 *
 * @return The color of the topmost view, or null if no suitable view is found.
 */
private fun ViewGroup.findTopColor(statusBarHeight: Int): Int? {
    val candidates = collectTopViewColorCandidates(statusBarHeight)

    if (candidates.isEmpty()) return null

    val maxDepth = candidates.maxOf { it.depth }
    val deepestCandidates = candidates.filter { it.depth == maxDepth }

    val winner = deepestCandidates.maxWithOrNull(
        compareBy<ViewColorCandidate> { it.elevation }
            .thenBy { it.zOrder }
    )

    return winner?.color
}

private data class ViewColorCandidate(
    val color: Int?,
    val elevation: Float,
    val depth: Int,
    val zOrder: Int,
    val viewClass: String
)

private fun ViewGroup.collectTopViewColorCandidates(statusBarHeight: Int): List<ViewColorCandidate> {
    val candidates = mutableListOf<ViewColorCandidate>()
    val screenWidth = resources.displayMetrics.widthPixels
    collectTopViewColorCandidates(candidates, screenWidth, statusBarHeight, depth = 0, zOrder = 0)
    return candidates
}

private fun ViewGroup.collectTopViewColorCandidates(
    candidates: MutableList<ViewColorCandidate>,
    screenWidth: Int,
    statusBarHeight: Int,
    depth: Int,
    zOrder: Int,
) {
    children.forEachIndexed { i, view ->
        if (view.isRelevantTopView(statusBarHeight)) {
            if (view is ViewGroup) {
                view.collectTopViewColorCandidates(
                    candidates, screenWidth, statusBarHeight, depth = depth + 1, zOrder = i + 1
                )
            }
        }
    }
    val hasChildCandidates = candidates.any { it.depth > depth }
    if ((background != null || foreground != null) && !hasChildCandidates && width == screenWidth) {
        val bgColor = extractColor()
        if (bgColor != null && bgColor != Color.TRANSPARENT) {
            candidates.add(
                ViewColorCandidate(
                    color = bgColor,
                    elevation = elevation,
                    depth = depth,
                    zOrder = zOrder,
                    viewClass = javaClass.simpleName
                )
            )
        }
    }
}

private fun View.isRelevantTopView(statusBarHeight: Int): Boolean =
    isVisible && isTopAligned(statusBarHeight) && this !is NavigationView && tag != STATUS_BG_VIEW_TAG

/**
 * The goal of this function is to determine what the actual background color of the view is
 * - which can be seen when rendered on the screen.
 */
@ColorInt
private fun View.extractColor() : Int? = extractColorByRendering() ?: extractBlendedColor()

/**
 * The goal of this function is to determine what the actual background color of the view is
 * - which can be seen when rendered on the screen.
 */
@ColorInt
private fun View.extractBlendedColor() : Int? {
    val bg = background
    val fg = foreground
    val blendedColor = when {
        fg == null -> bg?.extractColorRecursive()
        bg == null -> fg.extractColorRecursive()
        else -> blendColors(bg.extractColorRecursive(), fg.extractColorRecursive())
    }
    return blendedColor
}

@ColorInt
private fun Drawable.extractColorRecursive(
    considerRipple: Boolean = true,
    considerLayer: Boolean = true,
    considerStateList: Boolean = true,
) : Int? {
    val bg = this
    val bgColor = try {
        when {
            bg is ColorDrawable -> bg.color
            bg is GradientDrawable -> bg.color?.defaultColor
            bg is MaterialShapeDrawable -> bg.fillColor?.defaultColor
            bg is RippleDrawable && considerRipple -> {
                bg.getDrawable(0).extractColorRecursive(considerRipple = false)
            }
            bg is LayerDrawable && considerLayer -> {
                bg.getDrawable(bg.numberOfLayers - 1).extractColorRecursive(considerLayer = false)
            }
            bg is StateListDrawable && considerStateList -> {
                bg.current.extractColorRecursive(considerStateList = false)
            }
            else -> bg.tryExtractColor()
        }
    } catch (_: Exception) {
        null
    }

    return bgColor
}

private fun Drawable.tryExtractColor(): Int? {
    return try {
        // Try reflection for other drawable types that might have color properties
        val colorField = javaClass.getDeclaredField("mColor")
        colorField.isAccessible = true
        colorField.get(this) as? Int
    } catch (_: Exception) {
        try {
            // Try another common field name
            val paintField = javaClass.getDeclaredField("mPaint")
            paintField.isAccessible = true
            val paint = paintField.get(this) as? Paint
            paint?.color
        } catch (_: Exception) {
            null
        }
    }
}

private fun blendColors(background: Int?, foreground: Int?): Int? {
    if (foreground == null) return background
    if (background == null) return foreground

    val fgAlpha = Color.alpha(foreground) / 255f
    val bgAlpha = Color.alpha(background) / 255f
    val outAlpha = fgAlpha + bgAlpha * (1 - fgAlpha)

    fun blendChannel(fg: Int, bg: Int): Int =
        ((fg * fgAlpha + bg * bgAlpha * (1 - fgAlpha)) / outAlpha).toInt()

    val r = blendChannel(Color.red(foreground), Color.red(background))
    val g = blendChannel(Color.green(foreground), Color.green(background))
    val b = blendChannel(Color.blue(foreground), Color.blue(background))
    val a = (outAlpha * 255).toInt()

    return Color.argb(a, r, g, b)
}

private fun View.extractColorByRendering(): Int? {
    var bitmap: Bitmap? = null
    var color: Int? = null
    try {
        val size = 2
        val pixelPosition = 1
        bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        draw(canvas)
        color = bitmap[pixelPosition, pixelPosition]
    } catch (_: Exception) {
    } finally {
        bitmap?.recycle()
    }
    return color
}
// endregion status bar color finding

// region View insertion helpers
private typealias GroupParams = ViewGroup.LayoutParams
private typealias LinearParams = LinearLayout.LayoutParams
private typealias MarginParams = ViewGroup.MarginLayoutParams
private typealias ConstraintParams = ConstraintLayout.LayoutParams
private typealias RelativeParams = RelativeLayout.LayoutParams
private typealias FrameParams = FrameLayout.LayoutParams
private typealias DrawerParams = DrawerLayout.LayoutParams

private fun ViewGroup.insertTopView(view: View, viewHeight: Int): Boolean {
    if (insertViewAtTop(view, viewHeight)) return true

    val parent = parent as? ViewGroup
    if (parent?.insertViewAtTop(view, viewHeight) == true) return true

    val t = IllegalStateException("Couldn't insert ${javaClass.simpleName} into ${parent?.javaClass?.simpleName}")
    Timber.e(t, "children: %s", parent?.children?.toList()?.map { it.javaClass.simpleName }?.toString())

    return false
}

private fun ViewGroup.insertViewAtTop(view: View, viewHeight: Int): Boolean =
    when (childCount) {
        1 -> when (this) {
            is LinearLayout -> {
                insertViewAtTop(ll = this, view, viewHeight)
                        || addWrappedTopView(view, viewHeight)
            }
            is ConstraintLayout -> {
                val child = getChildAt(0)!!
                val childParams = (child.layoutParams as? ConstraintParams)
                if (childParams?.topToTop == ConstraintLayout.LayoutParams.PARENT_ID) {
                    childParams.topMargin = viewHeight
                    child.layoutParams = childParams
                    view.layoutParams = ConstraintParams(ConstraintParams.MATCH_PARENT, viewHeight)
                    addView(view)
                    true
                } else {
                    addWrappedTopView(view, viewHeight)
                }
            }
            is DrawerLayout -> {
                addWrappedTopView(view, viewHeight)
            }
            else -> {
                addViewAndTopMarginToChild(view, viewHeight)
                true
            }
        }

        0 -> {
            view.layoutParams = makeChildLayoutParams(LinearParams.MATCH_PARENT, viewHeight)
            addView(view)
            true
        }

        else -> false
    }

private fun ViewGroup.addViewAndTopMarginToChild(view: View, viewHeight: Int, topMargin: Int = viewHeight) {
    val child = getChildAt(0)!!
    val childParams = (child.layoutParams as? MarginParams)
        ?: MarginParams(MarginParams.MATCH_PARENT, MarginParams.WRAP_CONTENT)
    childParams.topMargin = topMargin
    child.layoutParams = childParams
    view.layoutParams = makeChildLayoutParams(GroupParams.MATCH_PARENT, viewHeight)
    addView(view)
}

private fun insertViewAtTop(ll: LinearLayout, view: View, viewHeight: Int): Boolean {
    if (ll.isEmpty()) {
        view.layoutParams = LinearParams(LinearParams.MATCH_PARENT, viewHeight)
        ll.addView(view)
        return true
    }

    if (ll.orientation == HORIZONTAL) {
        return false
    }

    if (ll.childCount > 1) {
        if (ll.canAddView()) {
            view.layoutParams = LinearParams(LinearParams.MATCH_PARENT, viewHeight)
            ll.addView(view, 0)
            return true
        } else {
            return false
        }
    }

    val child = ll.getChildAt(0)!!
    child.changeLinearMatchParentHeightToWeight()
    view.layoutParams = LinearParams(LinearParams.MATCH_PARENT, viewHeight)
    ll.addView(view, 0)
    return true
}

private fun ViewGroup.addWrappedTopView(view: View, viewHeight: Int): Boolean =
    try {
        val child = getChildAt(0)!!
        removeView(child)
        val wrapperLayout = LinearLayout(view.context)
        wrapperLayout.layoutParams = layoutParams ?: makeParentLayoutParams()
        view.layoutParams = LinearParams(LinearParams.MATCH_PARENT, viewHeight)
        wrapperLayout.addView(view)
        child.changeLinearMatchParentHeightToWeight()
        wrapperLayout.addView(child)
        addView(wrapperLayout)
        true
    } catch (e: IllegalStateException) {
        Timber.e(e, "addWrappedTopView failed")
        false
    }


private fun LinearLayout.canAddView(): Boolean =
    children.none { it.layoutParams?.height == ViewGroup.LayoutParams.MATCH_PARENT }

private fun View.changeLinearMatchParentHeightToWeight() {
    (layoutParams as? LinearParams)?.let { params ->
        if (params.height == LinearParams.MATCH_PARENT) {
            params.height = 0
            params.weight = 1f
        }
        layoutParams = params
    }
}

private fun View.makeChildLayoutParams(
    width: Int = GroupParams.MATCH_PARENT,
    height: Int = GroupParams.MATCH_PARENT,
): GroupParams = when (this) {
    is LinearLayout -> LinearParams(width, height)
    is ConstraintLayout -> ConstraintParams(width, height)
    is RelativeLayout -> RelativeParams(width, height)
    is FrameLayout -> FrameParams(width, height)
    is DrawerLayout -> DrawerParams(width, height)
    else -> GroupParams(width, height)
}

private fun View.makeParentLayoutParams(
    width: Int = GroupParams.MATCH_PARENT,
    height: Int = GroupParams.MATCH_PARENT,
): GroupParams =
    (parent as? ViewGroup)?.makeChildLayoutParams(width, height)
        ?: GroupParams(width, height)
// endregion View insertion helpers

// region View top alignment check
private fun View.isTopAligned(statusBarHeight: Int): Boolean {
    if (tag == STATUS_BG_VIEW_TAG) return false
    try {
        if (isLaidOut) {
            val location = IntArray(2)
            getLocationOnScreen(location)
            val top = location[1]
            val result = top == statusBarHeight
            return result
        } else {
            return isTopAlignedBeforeLayout()
        }
    } catch (e: Exception) {
        e.message?.let(Timber::w) ?: Timber.w(e)
        return false
    }
}

private fun View.isTopAlignedBeforeLayout(): Boolean {
    val parent = parent as? ViewGroup ?: return false
    val result = when (parent) {
        is LinearLayout -> parent.orientation == HORIZONTAL || parent.children.firstOrNull() == this
        is ConstraintLayout -> parent.isViewConstrainedToParentTop(this)
        is RelativeLayout -> parent.isViewAlignedTop(this)
        is FrameLayout -> parent.isViewAlignedTop(this)
        is DrawerLayout -> parent.isViewAlignedTop(this)
        is CoordinatorLayout -> parent.isViewAlignedTopInCoordinator(this)
        else -> false
    }
    return result
}

private fun ConstraintLayout.isViewConstrainedToParentTop(view: View): Boolean {
    val constraintSet = ConstraintSet()
    constraintSet.clone(this)
    val constraint = constraintSet.getConstraint(view.id)
    return constraint.layout.topToTop == ConstraintSet.PARENT_ID
}

@Suppress("UnusedReceiverParameter")
private fun RelativeLayout.isViewAlignedTop(view: View): Boolean {
    val params = view.layoutParams as? RelativeLayout.LayoutParams ?: return false
    return params.rules[RelativeLayout.ALIGN_PARENT_TOP] != 0
}

private fun CoordinatorLayout.isViewAlignedTopInCoordinator(view: View): Boolean {
    val params = view.layoutParams as? CoordinatorLayout.LayoutParams ?: return false

    // Check if view is anchored to another view
    val anchorId = params.anchorId
    if (anchorId != View.NO_ID) {
        val anchorView = findViewById<View>(anchorId)
        // If anchored, the view's top-alignment depends on the anchor's position
        return anchorView?.top == 0
    }

    // Check gravity for non-anchored views
    val verticalGravity = params.gravity and Gravity.VERTICAL_GRAVITY_MASK
    return (verticalGravity == Gravity.NO_GRAVITY) ||
            (verticalGravity == Gravity.TOP) ||
            ((verticalGravity != Gravity.BOTTOM) && (verticalGravity != Gravity.CENTER_VERTICAL))
}

@Suppress("UnusedReceiverParameter")
private fun FrameLayout.isViewAlignedTop(view: View): Boolean {
    val params = view.layoutParams as? FrameLayout.LayoutParams ?: return false
    val verticalGravity = params.gravity and Gravity.VERTICAL_GRAVITY_MASK
    return (verticalGravity != Gravity.BOTTOM) && (verticalGravity != Gravity.CENTER_VERTICAL)
}

@Suppress("UnusedReceiverParameter")
private fun DrawerLayout.isViewAlignedTop(view: View): Boolean {
    val params = view.layoutParams as? DrawerLayout.LayoutParams ?: return false
    val verticalGravity = params.gravity and Gravity.VERTICAL_GRAVITY_MASK
    return (verticalGravity == Gravity.NO_GRAVITY) ||
            (verticalGravity == Gravity.TOP) ||
            ((verticalGravity != Gravity.BOTTOM) && (verticalGravity != Gravity.CENTER_VERTICAL))
}
// endregion View top alignment check

// region full screen
fun ComponentActivity.enableFullScreenCompat() {
    window?.setFullScreenCompat() ?: return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        @Suppress("DEPRECATION")
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                window?.decorView?.setOnSystemUiVisibilityChangeListener { vis: Int ->
                    if ((vis and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        // Flags cleared -> re-hide
                        window.setFullScreenCompat()
                    }
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                window?.decorView?.setOnSystemUiVisibilityChangeListener(null)
            }
        })
    }
}

fun Window.setFullScreenCompat() {
    enableEdgeToEdge(darkBars = null)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Android 11+ (API 30+) - Modern approach
        insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    } else {
        // Android 10 and below - Legacy approach
        @Suppress("Deprecation")
        setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        @Suppress("Deprecation")
        decorView.setSystemUiVisibility(
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        )
    }
}
// endregion full screen

// region window edge to edge
/** For use in dialogs and dialog fragments. */
fun Window.enableEdgeToEdge(darkBars: Boolean? = context.resources.isDarkMode()) {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    setBarsBgTransparentCompat()
    setBarsIsContrastEnforcedCompat()
    darkBars?.let(::setBarsAppearanceModeCompat)
    adjustLayoutInDisplayCutoutModeCompat()
}

private fun Window.setBarsIsContrastEnforcedCompat() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val isNightModeSystemAuto = context.resources.configuration.uiMode == UiModeManager.MODE_NIGHT_AUTO
        isNavigationBarContrastEnforced = isNightModeSystemAuto
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("Deprecation")
            isStatusBarContrastEnforced = false
        }
    }
}

@Suppress("ObsoleteSdkInt")
private fun Window.setBarsAppearanceModeCompat(isDarkMode: Boolean) {
    val windowInsetsController = WindowInsetsControllerCompat(this, decorView)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        windowInsetsController.isAppearanceLightStatusBars = !isDarkMode
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        windowInsetsController.isAppearanceLightNavigationBars = !isDarkMode
    }
}

@Suppress("Deprecation", "ObsoleteSdkInt")
private fun Window.setBarsBgTransparentCompat() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        }
    }
}

private fun Window.adjustLayoutInDisplayCutoutModeCompat() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
        attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
}
// endregion window edge to edge

// region dialog fragment edge to edge
@JvmOverloads
fun DialogFragment.enableEdgeToEdge(view: View, darkBars: Boolean? = null) {
    dialog?.window?.enableEdgeToEdge(darkBars = darkBars)
    view.updateAllPaddingsForInsets()
}

fun View.updateAllPaddingsForInsets() {
    val windowInsets: WindowInsetsCompat? = ViewCompat.getRootWindowInsets(this)
    if (windowInsets != null) {
        updateAllPaddingsForInsets(windowInsets)
        return
    }
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        view.setOnApplyWindowInsetsListener(null)
        view.updateAllPaddingsForInsets(windowInsets)
        WindowInsetsCompat.CONSUMED
    }
    ViewCompat.requestApplyInsets(this)
}

private fun View.updateAllPaddingsForInsets(windowInsets: WindowInsetsCompat) {
    val insets: Insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
    updatePadding(top = insets.top, bottom = insets.bottom, left = insets.left, right = insets.right)
}
// endregion dialog fragment edge to edge

// region on config change
fun ComponentActivity.updatePaddingsForInsets() {
    if (isFinishing || isDestroyed) return
    val decorView = window?.decorView ?: return
    val contentView = decorView.findViewById<ViewGroup>(R.id.content) ?: return
    ViewCompat.setOnApplyWindowInsetsListener(decorView) { view, windowInsets ->
        decorView.tag = null
        ViewCompat.setOnApplyWindowInsetsListener(decorView, null)
        updatePaddingsForInsets(contentView, windowInsets)
        WindowInsetsCompat.CONSUMED
    }
    ViewCompat.requestApplyInsets(decorView)
}

private fun ComponentActivity.updatePaddingsForInsets(contentView: View, windowInsets: WindowInsetsCompat) {
    val insets: Insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
    val isLandscape: Boolean = contentView.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val topView = contentView.findViewWithTag<View>(STATUS_BG_VIEW_TAG)
    val top = if (topView != null) 0 else insets.getStatusBarHeightCompat(this)
    val hasCutout = windowInsets.displayCutout != null
    if (isLandscape) {
        val topIsLeft = display.rotation == Surface.ROTATION_90
        val left =
            if (insets.left > 0) insets.left
            else if (!topIsLeft) insets.bottom // nav bar is left
            else if (hasCutout) insets.top
            else 0
        val right =
            if (insets.right > 0) insets.right
            else if (topIsLeft) insets.bottom // nav bar is right
            else if (hasCutout) insets.top
            else 0
        contentView.updatePadding(top = top, bottom = 0, left = left, right = right)
    } else {
        val navBarHeight =
            if (insets.bottom > 0) insets.bottom
            else if (insets.right > 0) insets.right // sometimes it glitches and keeps landscape insets
            else if (insets.left > 0) insets.left
            else insets.top
        contentView.updatePadding(top = top, bottom = navBarHeight, left = 0, right = 0)
    }
}
// end region on config change

//    <style name="BaseAdTheme" >
//        <item name="android:windowExitAnimation">@null</item>
//        <item name="android:windowEnterAnimation">@null</item>
//        <item name="android:windowLayoutInDisplayCutoutMode">shortEdges</item>
//        <item name="android:windowTranslucentStatus">true</item>
//        <item name="android:windowDrawsSystemBarBackgrounds">true</item>
//        <item name="android:statusBarColor">@android:color/transparent</item>
//    </style>
//
//    <style name="AdTheme" parent="BaseAdTheme" >
//        <item name="android:fitsSystemWindows">false</item> // add true in values-v29
//    </style>
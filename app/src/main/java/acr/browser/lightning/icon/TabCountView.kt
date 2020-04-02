package acr.browser.lightning.icon

import acr.browser.lightning.R
import acr.browser.lightning.extensions.preferredLocale
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.withStyledAttributes
import java.text.NumberFormat

/**
 * A view that draws a count enclosed by a border. Defaults to drawing zero, draws infinity if the
 * number is greater than 99.
 *
 * Attributes:
 * - [R.styleable.TabCountView_tabIconColor] - The color used to draw the number and border.
 * Defaults to black.
 * - [R.styleable.TabCountView_tabIconTextSize] - The count text size, defaults to 14.
 * - [R.styleable.TabCountView_tabIconBorderRadius] - The radius of the border's corners. Defaults
 * to 0.
 * - [R.styleable.TabCountView_tabIconBorderWidth] - The width of the border. Defaults to 0.
 */
class TabCountView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val numberFormat = NumberFormat.getInstance(context.preferredLocale)
    private val paint: Paint = Paint().apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private var borderRadius: Float = 0F
    private var borderWidth: Float = 0F
    private val workingRect = RectF()

    private var count: Int = 0
    public var textColor: Int = Color.BLACK

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        context.withStyledAttributes(attrs, R.styleable.TabCountView) {
            paint.color = getColor(R.styleable.TabCountView_tabIconColor, Color.BLACK)
            textColor = paint.color
            paint.textSize = getDimension(R.styleable.TabCountView_tabIconTextSize, 14F)
            borderRadius = getDimension(R.styleable.TabCountView_tabIconBorderRadius, 0F)
            borderWidth = getDimension(R.styleable.TabCountView_tabIconBorderWidth, 0F)
        }

    }

    private fun updateBitmap()
    {
        // Assuming the size will never change
        if (bitmap==null) {
            val localBitmap = Bitmap.createBitmap(width * 2,
                    height * 2,
                    Bitmap.Config.ARGB_8888);

            bitmapCanvas = Canvas(localBitmap);
            bitmap = localBitmap
            workingRect.set(width.toFloat() / 4, height.toFloat() / 4, width.toFloat(), height.toFloat())
        }

    }

    /**
     * Update the number count displayed by the view.
     */
    fun updateCount(count: Int) {
        this.count = count
        contentDescription = count.toString()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val text: String = if (count > MAX_DISPLAYABLE_NUMBER) {
            context.getString(R.string.infinity)
        } else {
            numberFormat.format(count)
        }

        // Create our bitmap first time around
        updateBitmap()
        // Reset our bitmap
        bitmapCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        // Make sure we reset our text color in case it did change
        paint.color = textColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = borderWidth
        // Draw antialias round rect in bitmap
        bitmapCanvas?.drawRoundRect(workingRect, borderRadius, borderRadius, paint)

        // Draw that bitmap in our canvas using a local variable to make Kotlin happy
        val localBitmap = bitmap;
        if (localBitmap!=null)
        {
            canvas.drawBitmap(localBitmap, -width.toFloat()/8, -height.toFloat()/8, paint);
        }

        // Now render our text
        paint.style = Paint.Style.FILL
        paint.color = textColor
        val xPos = width / 2F
        val yPos = height / 2 - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(text, xPos, yPos, paint)

        super.onDraw(canvas)
    }

    companion object {
        private const val MAX_DISPLAYABLE_NUMBER = 99
    }

}

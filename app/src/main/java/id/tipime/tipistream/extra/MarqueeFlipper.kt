package id.tipime.tipistream.extra

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ViewFlipper
import id.tipime.tipistream.R

/**
 * A bottom-banner marquee that rotates between several running-text messages.
 *
 * Each message is its own scrolling [TextView]; this flipper cycles through them. A TextView's
 * marquee only animates while it is selected, and a plain ViewFlipper never selects the incoming
 * child, so we re-arm the selection on every flip.
 *
 * Children are created programmatically (never declared in XML) and are forced to
 * [ViewGroup.CLIP_CHILDREN] so a long scrolling marquee cannot paint over neighbouring views.
 */
class MarqueeFlipper @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewFlipper(context, attrs) {

    init {
        clipChildren = true
    }

    /** Messages currently shown, in rotation order. Setting this rebuilds the children. */
    var messages: List<CharSequence> = emptyList()
        set(value) {
            field = value
            updateMessages()
        }

    private fun updateMessages() {
        stopFlipping()
        removeAllViews()

        if (messages.isEmpty()) {
            visibility = GONE
            return
        }

        visibility = VISIBLE
        messages.forEach { message ->
            addView(buildMarquee(message))
        }

        // swap instantly: pass a real (empty) animation resource — setInAnimation(context, 0)
        // throws Resources$NotFoundException
        setInAnimation(context, R.anim.no_animation)
        setOutAnimation(context, R.anim.no_animation)
        setDisplayedChild(0)
        armMarquee(currentView)
        startFlipping()
    }

    private fun buildMarquee(message: CharSequence): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setTextColor(Color.WHITE)
            textSize = 14f
            setLines(1)
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1 // forever
            isHorizontalScrollBarEnabled = false
            // a marquee must be focusable to animate, but it must never steal focus from the
            // channel grid — so it is focusable but not focusable-in-touch-mode, and it is never
            // the next-focus target of anything.
            isFocusable = true
            isFocusableInTouchMode = false
            // pad the ends so the looping marquee keeps a gap between repeats
            text = "    $message    "
        }
    }

    override fun showNext() {
        super.showNext()
        armMarquee(currentView)
    }

    override fun showPrevious() {
        super.showPrevious()
        armMarquee(currentView)
    }

    override fun setDisplayedChild(whichChild: Int) {
        super.setDisplayedChild(whichChild)
        armMarquee(currentView)
    }

    private fun armMarquee(view: android.view.View?) {
        if (view is TextView) view.isSelected = true
    }
}

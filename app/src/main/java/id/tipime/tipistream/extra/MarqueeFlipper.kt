package id.tipime.tipistream.extra

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import android.widget.ViewFlipper

/**
 * A [ViewFlipper] that keeps the marquee animation of the currently shown child running.
 *
 * A TextView's marquee only animates while it is selected, but a plain ViewFlipper just swaps
 * views without ever selecting the incoming one — so rotating marquee messages would freeze on
 * every flip. Re-arming the selection on each swap keeps each message scrolling.
 */
class MarqueeFlipper @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewFlipper(context, attrs) {

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

    private fun armMarquee(view: View?) {
        if (view is TextView) view.isSelected = true
    }
}

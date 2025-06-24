package com.example.aifloatingball.preference

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.example.aifloatingball.R

class CustomSeekBarPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.seekBarPreferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    private var mSeekBar: SeekBar? = null
    private var mSeekBarValueTextView: TextView? = null
    private var mMin: Int = 0
    private var mMax: Int = 100
    private var mValue: Int = 0
    private var mTrackingTouch: Boolean = false

    init {
        layoutResource = R.layout.preference_custom_seekbar

        attrs?.let {
            val a = context.obtainStyledAttributes(
                it,
                androidx.preference.R.styleable.SeekBarPreference,
                defStyleAttr,
                defStyleRes
            )

            mMin = a.getInt(androidx.preference.R.styleable.SeekBarPreference_min, 0)
            mMax = a.getInt(androidx.preference.R.styleable.SeekBarPreference_android_max, 100)
            
            a.recycle()
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        
        mSeekBar = holder.findViewById(R.id.custom_seekbar) as SeekBar
        mSeekBarValueTextView = holder.findViewById(R.id.custom_seekbar_value) as TextView
        
        mSeekBar?.max = mMax - mMin
        mSeekBar?.progress = mValue - mMin
        
        updateLabelValue(mValue)
        
        mSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && !mTrackingTouch) {
                    syncValueInternal(progress + mMin)
                }
                updateLabelValue(progress + mMin)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                mTrackingTouch = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                mTrackingTouch = false
                val progress = seekBar.progress + mMin
                if (progress != mValue) {
                    syncValueInternal(progress)
                }
            }
        })
        
        mSeekBar?.isEnabled = isEnabled
    }

    private fun updateLabelValue(value: Int) {
        mSeekBarValueTextView?.text = value.toString()
    }

    private fun syncValueInternal(value: Int) {
        if (value != mValue) {
            mValue = value
            persistInt(value)
            notifyChanged()
        }
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        mValue = getPersistedInt(if (defaultValue is Int) defaultValue else mValue)
    }

    fun setMin(min: Int) {
        if (min > mMax) {
            mMin = mMax
        } else {
            mMin = min
        }
        notifyChanged()
    }

    fun setMax(max: Int) {
        if (max < mMin) {
            mMax = mMin
        } else {
            mMax = max
        }
        notifyChanged()
    }

    fun setValue(value: Int) {
        var newValue = value
        if (newValue < mMin) {
            newValue = mMin
        } else if (newValue > mMax) {
            newValue = mMax
        }
        
        if (newValue != mValue) {
            mValue = newValue
            persistInt(newValue)
            notifyChanged()
        }
    }

    fun getValue(): Int {
        return mValue
    }
} 
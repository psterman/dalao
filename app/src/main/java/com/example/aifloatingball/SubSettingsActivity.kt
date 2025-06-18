package com.example.aifloatingball

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class SubSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sub_settings)

        val prefResId = intent.getIntExtra(EXTRA_PREFERENCE_RESOURCE, 0)
        val title = intent.getStringExtra(EXTRA_PREFERENCE_TITLE) ?: ""

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = title

        if (savedInstanceState == null && prefResId != 0) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.sub_settings_container, SubSettingsFragment.newInstance(prefResId))
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_PREFERENCE_RESOURCE = "preference_resource"
        const val EXTRA_PREFERENCE_TITLE = "preference_title"
    }
} 
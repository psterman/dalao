package com.example.aifloatingball.settings

import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.example.aifloatingball.R
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.SearchEngineCategory
import com.google.gson.Gson

/**
 * A dialog fragment for adding a new or editing an existing search engine.
 *
 * To use this fragment, create an instance using `newInstance` and show it.
 * The result is returned via the Fragment Result API. The calling Fragment or Activity
 * should set a listener using `childFragmentManager.setFragmentResultListener`
 * with the [REQUEST_KEY].
 *
 * The result bundle will contain the saved [SearchEngine] object as a JSON string
 * under the key [RESULT_KEY_ENGINE_JSON].
 */
class EditEngineDialogFragment : DialogFragment() {

    private val gson = Gson()
    private var existingEngine: SearchEngine? = null
    private var isEditMode: Boolean = false
    private var category: SearchEngineCategory = SearchEngineCategory.GENERAL

    private lateinit var displayNameEditText: EditText
    private lateinit var nameEditText: EditText
    private lateinit var searchUrlEditText: EditText
    private lateinit var homeUrlEditText: EditText
    private lateinit var descriptionEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString(ARG_ENGINE_JSON)?.let {
            existingEngine = gson.fromJson(it, SearchEngine::class.java)
            isEditMode = true
        }
        arguments?.getSerializable(ARG_CATEGORY)?.let {
            category = it as SearchEngineCategory
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val title = if (isEditMode) "修改搜索引擎" else "添加搜索引擎"

        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_engine, null)
        displayNameEditText = view.findViewById(R.id.edit_engine_display_name)
        nameEditText = view.findViewById(R.id.edit_engine_name)
        searchUrlEditText = view.findViewById(R.id.edit_engine_search_url)
        homeUrlEditText = view.findViewById(R.id.edit_engine_home_url)
        descriptionEditText = view.findViewById(R.id.edit_engine_description)

        if (isEditMode) {
            displayNameEditText.setText(existingEngine?.displayName)
            nameEditText.setText(existingEngine?.name)
            nameEditText.isEnabled = false // Internal name should not be changed
            searchUrlEditText.setText(existingEngine?.searchUrl)
            homeUrlEditText.setText(existingEngine?.url)
            descriptionEditText.setText(existingEngine?.description)
        }

        val builder = AlertDialog.Builder(requireContext())
            .setView(view)
            .setTitle(title)
            .setPositiveButton("保存", null) // Set to null to override and handle validation
            .setNegativeButton("取消") { dialog, _ ->
                dialog.cancel()
            }

        val dialog = builder.create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                handleSave()
            }
        }

        return dialog
    }

    private fun handleSave() {
        if (!validateInput()) {
            return
        }

        val displayName = displayNameEditText.text.toString().trim()
        val name = nameEditText.text.toString().trim()
        val searchUrl = searchUrlEditText.text.toString().trim()
        val homeUrl = homeUrlEditText.text.toString().trim()
        val description = descriptionEditText.text.toString().trim()

        val resultEngine = if (isEditMode) {
            existingEngine!!.copy(
                displayName = displayName,
                searchUrl = searchUrl,
                url = if (homeUrl.isNotEmpty()) homeUrl else searchUrl.split('?').firstOrNull() ?: "",
                description = description
            )
        } else {
            SearchEngine(
                name = name,
                displayName = displayName,
                searchUrl = searchUrl,
                url = if (homeUrl.isNotEmpty()) homeUrl else searchUrl.split('?').firstOrNull() ?: "",
                iconResId = R.drawable.ic_web_default,
                description = description,
                isCustom = true,
                category = this.category
            )
        }

        setFragmentResult(REQUEST_KEY, bundleOf(RESULT_KEY_ENGINE_JSON to gson.toJson(resultEngine)))
        dismiss()
    }

    private fun validateInput(): Boolean {
        val displayName = displayNameEditText.text.toString().trim()
        if (TextUtils.isEmpty(displayName)) {
            displayNameEditText.error = "显示名称不能为空"
            return false
        }

        if (!isEditMode) {
            val name = nameEditText.text.toString().trim()
            if (TextUtils.isEmpty(name)) {
                nameEditText.error = "内部名称不能为空"
                return false
            } else if (name.contains(" ")) {
                nameEditText.error = "内部名称不能包含空格"
                return false
            }
        }

        val urlTemplate = searchUrlEditText.text.toString().trim()
        if (TextUtils.isEmpty(urlTemplate)) {
            searchUrlEditText.error = "搜索 URL 不能为空"
            return false
        } else if (!urlTemplate.contains("{query}")) {
            searchUrlEditText.error = "搜索 URL 必须包含 {query} 占位符"
            return false
        }

        return true
    }

    companion object {
        const val TAG = "EditEngineDialogFragment"
        const val REQUEST_KEY = "edit_engine_dialog_request"
        const val RESULT_KEY_ENGINE_JSON = "search_engine_result_json"

        private const val ARG_ENGINE_JSON = "search_engine_json"
        private const val ARG_CATEGORY = "category_json"

        /**
         * Creates a new instance of EditEngineDialogFragment.
         *
         * @param engine The [SearchEngine] to edit. If null, the dialog will be in "add" mode.
         * @param category The [SearchEngineCategory] for the new engine.
         * @return A new instance of EditEngineDialogFragment.
         */
        fun newInstance(engine: SearchEngine?, category: SearchEngineCategory): EditEngineDialogFragment {
            val fragment = EditEngineDialogFragment()
            val args = Bundle()
            engine?.let {
                args.putString(ARG_ENGINE_JSON, Gson().toJson(it))
            }
            args.putSerializable(ARG_CATEGORY, category)
            fragment.arguments = args
            return fragment
        }
    }
} 
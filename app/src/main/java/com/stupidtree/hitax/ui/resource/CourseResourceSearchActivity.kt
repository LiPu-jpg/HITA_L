package com.stupidtree.hitax.ui.resource

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.material.snackbar.Snackbar
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.R
import com.stupidtree.hitax.data.model.resource.CourseResourceItem
import com.stupidtree.hitax.databinding.ActivityCourseResourceSearchBinding
import com.stupidtree.hitax.databinding.DynamicTeacherSearchResultItemBinding
import com.stupidtree.hitax.utils.ActivityUtils
import com.stupidtree.hitax.utils.CourseCodeUtils
import com.stupidtree.style.base.BaseActivity
import com.stupidtree.style.base.BaseListAdapter

class CourseResourceSearchActivity :
    BaseActivity<CourseResourceSearchViewModel, ActivityCourseResourceSearchBinding>() {

    private lateinit var adapter: CourseResourceAdapter
    private var mode: ActivityUtils.CourseResourceMode = ActivityUtils.CourseResourceMode.VIEW

    override fun getViewModelClass(): Class<CourseResourceSearchViewModel> =
        CourseResourceSearchViewModel::class.java

    override fun initViewBinding(): ActivityCourseResourceSearchBinding =
        ActivityCourseResourceSearchBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
    }

    override fun initViews() {
        mode = runCatching {
            ActivityUtils.CourseResourceMode.valueOf(
                intent.getStringExtra("mode") ?: ActivityUtils.CourseResourceMode.VIEW.name
            )
        }.getOrDefault(ActivityUtils.CourseResourceMode.VIEW)

        binding.toolbar.title = if (mode == ActivityUtils.CourseResourceMode.SUBMIT) {
            getString(R.string.course_resource_submit_title)
        } else {
            getString(R.string.course_resource_title)
        }

        adapter = CourseResourceAdapter(mutableListOf())
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.searchInput.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO) {
                    startSearch()
                    return true
                }
                return false
            }
        })
        binding.searchButton.setOnClickListener { startSearch() }
        binding.swipeRefresh.setColorSchemeColors(getColorPrimary())
        binding.swipeRefresh.setOnRefreshListener { startSearch() }

        viewModel.resultsLiveData.observe(this) { state ->
            binding.swipeRefresh.isRefreshing = false
            if (state.state == DataState.STATE.SUCCESS) {
                val items = state.data ?: emptyList()
                adapter.notifyItemChangedSmooth(items)
                binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                binding.emptyText.setText(R.string.course_resource_empty)
            } else {
                binding.emptyText.visibility = View.VISIBLE
                binding.emptyText.setText(R.string.course_resource_failed)
                state.message?.takeIf { it.isNotBlank() }?.let {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        val initialQuery = intent.getStringExtra("query")
        if (!initialQuery.isNullOrBlank()) {
            val normalized = CourseCodeUtils.normalize(initialQuery) ?: initialQuery
            binding.searchInput.setText(normalized)
            binding.searchInput.setSelection(normalized.length)
            binding.swipeRefresh.isRefreshing = true
            viewModel.search(normalized)
        }
    }

    private fun startSearch() {
        val input = binding.searchInput.text?.toString()?.trim().orEmpty()
        val query = CourseCodeUtils.normalize(input) ?: input
        if (query.isBlank()) return
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
        binding.swipeRefresh.isRefreshing = true
        viewModel.search(query)
    }

    inner class CourseResourceAdapter(mBeans: MutableList<CourseResourceItem>) :
        BaseListAdapter<CourseResourceItem, CourseResourceAdapter.Holder>(this, mBeans) {

        inner class Holder(val binding: DynamicTeacherSearchResultItemBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun getViewBinding(parent: ViewGroup, viewType: Int): ViewBinding {
            return DynamicTeacherSearchResultItemBinding.inflate(layoutInflater, parent, false)
        }

        override fun createViewHolder(viewBinding: ViewBinding, viewType: Int): Holder {
            return Holder(viewBinding as DynamicTeacherSearchResultItemBinding)
        }

        override fun bindHolder(holder: Holder, data: CourseResourceItem?, position: Int) {
            holder.binding.picture.setImageResource(R.drawable.ic_baseline_menu_24)
            holder.binding.title.text = data?.courseName
            holder.binding.subtitle.text = listOf(data?.courseCode, data?.teachers?.take(3)?.joinToString(" / "))
                .filter { !it.isNullOrBlank() }
                .joinToString("  ·  ")
            holder.binding.tag.visibility = View.VISIBLE
            holder.binding.tag.text = if (data?.repoType == "multi-project") "多课程" else "课程"
            holder.binding.card.setOnClickListener {
                data ?: return@setOnClickListener
                if (mode == ActivityUtils.CourseResourceMode.SUBMIT) {
                    ActivityUtils.startCourseContributionActivity(
                        this@CourseResourceSearchActivity,
                        data.repoName,
                        data.courseName,
                        data.courseCode,
                        data.repoType,
                    )
                } else {
                    ActivityUtils.startCourseReadmeActivity(
                        this@CourseResourceSearchActivity,
                        data.repoName,
                        data.courseName,
                        data.courseCode,
                        data.repoType,
                    )
                }
            }
        }
    }
}

package com.stupidtree.hitax.ui.resource

import android.os.Bundle
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.R
import com.stupidtree.hitax.databinding.ActivityCourseReadmeBinding
import com.stupidtree.hitax.utils.ActivityUtils
import com.stupidtree.style.base.BaseActivity
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
// syntax highlight removed (prism4j artifacts not available in current mirrors)

class CourseReadmeActivity : BaseActivity<CourseReadmeViewModel, ActivityCourseReadmeBinding>() {
    private lateinit var repoName: String
    private lateinit var courseName: String
    private lateinit var courseCode: String
    private lateinit var repoType: String

    override fun getViewModelClass(): Class<CourseReadmeViewModel> = CourseReadmeViewModel::class.java

    override fun initViewBinding(): ActivityCourseReadmeBinding =
        ActivityCourseReadmeBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
    }

    override fun initViews() {
        repoName = intent.getStringExtra("repoName") ?: ""
        courseName = intent.getStringExtra("courseName") ?: repoName
        courseCode = intent.getStringExtra("courseCode") ?: repoName
        repoType = intent.getStringExtra("repoType") ?: "normal"

        binding.toolbar.title = courseName
        binding.courseCode.text = courseCode
        binding.readmeText.text = getString(R.string.course_readme_loading)
        binding.buttonContribute.setOnClickListener {
            ActivityUtils.startCourseContributionActivity(this, repoName, courseName, courseCode, repoType)
        }

        viewModel.readmeLiveData.observe(this) { state ->
            binding.progress.visibility = View.GONE
            if (state.state == DataState.STATE.SUCCESS) {
                val data = state.data ?: return@observe
                binding.sourceText.text = getString(R.string.course_readme_source, data.source)
                Markwon.builder(this)
                    .usePlugin(LinkifyPlugin.create())
                    .usePlugin(HtmlPlugin.create())
                    .usePlugin(TablePlugin.create(this))
                    .usePlugin(TaskListPlugin.create(this))
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(JLatexMathPlugin.create(binding.readmeText.textSize))
                    .usePlugin(GlideImagesPlugin.create(this))
                    .build()
                    .setMarkdown(binding.readmeText, data.markdown)
            } else {
                binding.readmeText.text = state.message ?: getString(R.string.course_resource_failed)
                Snackbar.make(binding.root, state.message ?: getString(R.string.course_resource_failed), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        binding.progress.visibility = View.VISIBLE
        viewModel.load(repoName)
    }
}

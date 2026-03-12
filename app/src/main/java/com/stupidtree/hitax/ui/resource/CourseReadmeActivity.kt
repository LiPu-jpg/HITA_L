package com.stupidtree.hitax.ui.resource

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import org.jsoup.Jsoup
import java.net.URI
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
        applyStatusBarInsets()
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
                val processed = preprocessReadme(data.markdown)
                val linkBase = data.source
                val markwon = Markwon.builder(this)
                    .usePlugin(LinkifyPlugin.create())
                    .usePlugin(HtmlPlugin.create())
                    .usePlugin(TablePlugin.create(this))
                    .usePlugin(TaskListPlugin.create(this))
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(JLatexMathPlugin.create(binding.readmeText.textSize))
                    .usePlugin(GlideImagesPlugin.create(this))
                    .usePlugin(object : AbstractMarkwonPlugin() {
                        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                            builder.linkResolver { _: View, link: String ->
                                openLink(resolveReadmeLink(link, linkBase))
                            }
                        }
                    })
                    .build()
                markwon.setMarkdown(binding.readmeText, processed)
                binding.readmeText.movementMethod = LinkMovementMethod.getInstance()
            } else {
                val rawMessage = state.message?.trim().orEmpty()
                val friendly = if (rawMessage.contains("invalid repo name", ignoreCase = true)) {
                    getString(R.string.course_readme_missing)
                } else {
                    rawMessage.ifBlank { getString(R.string.course_resource_failed) }
                }
                binding.readmeText.text = friendly
                Snackbar.make(binding.root, friendly, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        binding.progress.visibility = View.VISIBLE
        viewModel.load(repoName)
    }

    private fun applyStatusBarInsets() {
        val target = binding.root
        val originalTop = target.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(target) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = originalTop + bars.top)
            insets
        }
    }

    private fun preprocessReadme(markdown: String): String {
        val withTables = convertHtmlTables(markdown)
        val startPattern = Regex("\\{\\{[%<]\\s*details\\s+([^%>]+)\\s*[%>]\\}\\}", RegexOption.IGNORE_CASE)
        val endPattern = Regex("\\{\\{[%<]\\s*/details\\s*[%>]\\}\\}", RegexOption.IGNORE_CASE)
        val replacedStart = startPattern.replace(withTables) { match ->
            val attrs = match.groupValues[1]
            val title = Regex("title\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                .find(attrs)?.groupValues?.get(1) ?: getString(R.string.course_resource_open)
            val closed = Regex("closed\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                .find(attrs)?.groupValues?.get(1)?.trim()?.lowercase()
            val openAttr = if (closed == "true") "" else " open"
            "<details$openAttr><summary>$title</summary>"
        }
        return endPattern.replace(replacedStart, "</details>")
    }

    private fun convertHtmlTables(markdown: String): String {
        val tablePattern = Regex("(?is)<table[^>]*>.*?</table>")
        return tablePattern.replace(markdown) { match ->
            runCatching {
                val doc = Jsoup.parse(match.value)
                val table = doc.selectFirst("table") ?: return@replace match.value
                val rows = table.select("tr")
                if (rows.isEmpty()) return@replace match.value
                val cellsList = rows.map { row ->
                    row.select("th,td").map { it.text().trim() }
                }
                val maxCols = cellsList.maxOfOrNull { it.size } ?: 0
                if (maxCols == 0) return@replace match.value
                fun pad(row: List<String>): List<String> {
                    if (row.size >= maxCols) return row
                    return row + List(maxCols - row.size) { "" }
                }
                val header = pad(cellsList.first())
                val headerRow = header.joinToString(" | ")
                val separator = List(maxCols) { "---" }.joinToString(" | ")
                val body = cellsList.drop(1).joinToString("\n") { row ->
                    pad(row).joinToString(" | ")
                }
                listOf(headerRow, separator, body).filter { it.isNotBlank() }.joinToString("\n")
            }.getOrDefault(match.value)
        }
    }

    private fun resolveReadmeLink(link: String, source: String): String {
        if (link.startsWith("http://") || link.startsWith("https://")) {
            return link
        }
        val base = source.trim()
        if (base.startsWith("http://") || base.startsWith("https://")) {
            return runCatching { URI(base).resolve(link).toString() }.getOrDefault(link)
        }
        return link
    }

    private fun openLink(link: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            startActivity(intent)
        }
    }
}

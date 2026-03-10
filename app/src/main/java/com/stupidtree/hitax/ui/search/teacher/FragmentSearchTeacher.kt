package com.stupidtree.hitax.ui.search.teacher

import android.view.View
import com.stupidtree.hitax.R
import com.stupidtree.hitax.ui.search.BasicFragmentSearchResult
import com.stupidtree.hitax.utils.ActivityUtils

class FragmentSearchTeacher : BasicFragmentSearchResult<TeacherSearched, SearchTeacherViewModel>() {


    override fun updateHintText(reload: Boolean, addedSize: Int) {
        result?.text = getString(R.string.teacher_total_searched, addedSize)
    }


    override fun getHolderLayoutId(): Int {
        return R.layout.dynamic_teacher_search_result_item
    }

    override fun bindListHolder(
        simpleHolder: SearchListAdapter.SimpleHolder?,
        data: TeacherSearched,
        position: Int
    ) {
        simpleHolder?.title?.text = data.name
        simpleHolder?.subtitle?.text = data.department
        simpleHolder?.picture?.setImageResource(R.drawable.ic_baseline_menu_24)
        simpleHolder?.tag?.visibility = View.VISIBLE
        simpleHolder?.tag?.text = if (data.repoType == "multi-project") "多课程" else "课程"
        simpleHolder?.card?.setOnLongClickListener {
            ActivityUtils.startTeacherHomepageSearch(requireContext(), data.name)
            true
        }
    }

    override fun getViewModelClass(): Class<SearchTeacherViewModel> {
        return SearchTeacherViewModel::class.java
    }

    override fun getLayoutId(): Int {
        return R.layout.fragment_search_result_1
    }

    override fun onItemClicked(card: View?, data: TeacherSearched, position: Int) {
        val repoName = data.repoName.ifBlank {
            data.courseCode.ifBlank { data.courseName.ifBlank { data.name } }
        }
        val courseName = data.courseName.ifBlank { data.courseCode.ifBlank { repoName } }
        val courseCode = data.courseCode.ifBlank { repoName }
        ActivityUtils.startCourseReadmeActivity(
            requireContext(),
            repoName = repoName,
            courseName = courseName,
            courseCode = courseCode,
            repoType = data.repoType.ifBlank { "normal" },
        )
    }

}

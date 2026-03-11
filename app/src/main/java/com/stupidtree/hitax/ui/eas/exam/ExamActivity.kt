package com.stupidtree.hitax.ui.eas.exam

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.recyclerview.widget.LinearLayoutManager
import com.stupidtree.component.data.DataState
import com.stupidtree.hitax.data.model.eas.ExamItem
import com.stupidtree.hitax.data.model.eas.TermItem
import com.stupidtree.hitax.R
import com.stupidtree.hitax.ui.eas.EASActivity
import com.stupidtree.hitax.databinding.ActivityEasExamBinding
import com.stupidtree.style.base.BaseListAdapter
import com.stupidtree.style.widgets.PopUpCheckableList

class ExamActivity :
    EASActivity<ExamViewModel, ActivityEasExamBinding>() {
    lateinit var listAdapter: ExamListAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
    }

    private fun bindLiveData(){
        viewModel.termsLiveData.observe(this) { data ->
            if (data.state == DataState.STATE.SUCCESS) {
                if (!data.data.isNullOrEmpty()) {
                    for (t in data.data!!) {
                        if (t.isCurrent) {
                            viewModel.selectedTermLiveData.value = t
                            return@observe
                        }
                    }
                    viewModel.selectedTermLiveData.value = data.data?.get(0)
                }
            } else {
                binding.refresh.isRefreshing = false
                binding.examTermText.setText(R.string.load_failed)
            }
        }
        viewModel.selectedTermLiveData.observe(this) {
            it?.let { term ->
                binding.refresh.isRefreshing = true
                binding.examTermText.text = getDisplayTermName(term, viewModel.termsLiveData.value?.data)
            }
        }
        viewModel.selectedExamTypeLiveData.observe(this) {
            it?.let { type ->
                binding.refresh.isRefreshing = true
                binding.examTypeText.text = when (type) {
                    ExamViewModel.ExamType.ALL -> getString(R.string.exam_type_all)
                    ExamViewModel.ExamType.MIDTERM -> getString(R.string.exam_type_midterm)
                    ExamViewModel.ExamType.FINAL -> getString(R.string.exam_type_final)
                }
            }
        }
        viewModel.examInfoLiveData.observe(this){
            binding.refresh.isRefreshing = false
            if (it.state == DataState.STATE.SUCCESS) {
                it.data?.let { it1 -> listAdapter.notifyItemChangedSmooth(it1) }
            }
            binding.emptyView.visibility = if(it.data?.size?:0 >0){
                 View.GONE
            }else{
                View.VISIBLE
            }
        }
    }
    override fun refresh() {
        binding.refresh.isRefreshing = true
        viewModel.startRefresh()
    }

    override fun initViewBinding(): ActivityEasExamBinding {
        return ActivityEasExamBinding.inflate(layoutInflater)
    }

    override fun getViewModelClass(): Class<ExamViewModel> {
        return ExamViewModel::class.java
    }

    override fun initViews() {
        super.initViews()
        bindLiveData()
        binding.refresh.setColorSchemeColors(getColorPrimary())
        listAdapter = ExamListAdapter(this, mutableListOf())
        binding.refresh.setOnRefreshListener {
            refresh()
        }
        binding.examStructure.adapter = listAdapter
        binding.examStructure.layoutManager = LinearLayoutManager(getThis())
        binding.examTermLayout.setOnClickListener {
            viewModel.termsLiveData.value?.data?.let { terms ->
                val names = terms.map { getDisplayTermName(it, terms) }
                if (names.isEmpty()) return@setOnClickListener
                PopUpCheckableList<TermItem>()
                    .setListData(names, terms)
                    .setTitle(getString(R.string.pick_exam_term))
                    .setOnConfirmListener(object :
                        PopUpCheckableList.OnConfirmListener<TermItem> {
                        override fun OnConfirm(title: String?, key: TermItem) {
                            viewModel.selectedTermLiveData.value = key
                        }
                    }).show(supportFragmentManager, "exam_terms")
            }
        }
        binding.examTypeLayout.setOnClickListener {
            val names = mutableListOf(
                getString(R.string.exam_type_all),
                getString(R.string.exam_type_midterm),
                getString(R.string.exam_type_final)
            )
            val list = arrayListOf(
                ExamViewModel.ExamType.ALL,
                ExamViewModel.ExamType.MIDTERM,
                ExamViewModel.ExamType.FINAL
            )
            PopUpCheckableList<ExamViewModel.ExamType>()
                .setListData(names, list)
                .setTitle(getString(R.string.pick_exam_type))
                .setOnConfirmListener(object :
                    PopUpCheckableList.OnConfirmListener<ExamViewModel.ExamType> {
                    override fun OnConfirm(title: String?, key: ExamViewModel.ExamType) {
                        viewModel.selectedExamTypeLiveData.value = key
                    }
                }).show(supportFragmentManager, "exam_types")
        }
        listAdapter.setOnItemClickListener(object :
            BaseListAdapter.OnItemClickListener<ExamItem> {
            override fun onItemClick(data: ExamItem?, card: View?, position: Int) {
                data?.let {
                    ExamDetailFragment(it).
                    show(supportFragmentManager, "exam_detail")
                }
            }
        })
        listAdapter.setOnItemLongClickListener(object :
            BaseListAdapter.OnItemLongClickListener<ExamItem> {
            override fun onItemLongClick(data: ExamItem?, view: View?, position: Int): Boolean {
                val memoId = data?.memoId ?: return false
                AlertDialog.Builder(this@ExamActivity)
                    .setTitle(R.string.exam_memo_delete_title)
                    .setMessage(data.courseName ?: "")
                    .setPositiveButton(R.string.exam_memo_delete_confirm) { _, _ ->
                        viewModel.deleteMemo(memoId)
                    }
                    .setNegativeButton(R.string.exam_memo_add_cancel, null)
                    .show()
                return true
            }
        })
        binding.examMemoHint.setOnClickListener {
            showAddMemoDialog()
        }
        viewModel.selectedExamTypeLiveData.value = ExamViewModel.ExamType.ALL
    }

    private fun getDisplayTermName(term: TermItem, allTerms: List<TermItem>?): String {
        val termName = term.termName.trim()
        if (termName.isNotBlank() && allTerms != null) {
            val duplicates = allTerms.count { it.termName.trim() == termName }
            if (duplicates <= 1) {
                return termName
            }
        } else if (termName.isNotBlank()) {
            return termName
        }
        return term.name
    }

    private fun showAddMemoDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_exam_memo, null)
        val titleInput = dialogView.findViewById<AppCompatEditText>(R.id.memo_title)
        val dateInput = dialogView.findViewById<AppCompatEditText>(R.id.memo_date)
        val timeInput = dialogView.findViewById<AppCompatEditText>(R.id.memo_time)
        val locationInput = dialogView.findViewById<AppCompatEditText>(R.id.memo_location)
        val typeInput = dialogView.findViewById<AppCompatEditText>(R.id.memo_type)

        val defaultType = when (viewModel.selectedExamTypeLiveData.value) {
            ExamViewModel.ExamType.MIDTERM -> getString(R.string.exam_type_midterm)
            ExamViewModel.ExamType.FINAL -> getString(R.string.exam_type_final)
            else -> ""
        }
        if (defaultType.isNotBlank()) {
            typeInput.setText(defaultType)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.exam_memo_add_title)
            .setView(dialogView)
            .setPositiveButton(R.string.exam_memo_add_confirm, null)
            .setNegativeButton(R.string.exam_memo_add_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = titleInput.text?.toString()?.trim().orEmpty()
                if (title.isBlank()) {
                    Toast.makeText(this, R.string.exam_memo_title_required, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val item = ExamItem().apply {
                    courseName = title
                    examDate = dateInput.text?.toString()?.trim().orEmpty()
                    examTime = timeInput.text?.toString()?.trim().orEmpty()
                    examLocation = locationInput.text?.toString()?.trim().orEmpty()
                    examType = typeInput.text?.toString()?.trim().orEmpty()
                    termName = viewModel.selectedTermLiveData.value?.name ?: ""
                }
                viewModel.addMemo(item)
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}

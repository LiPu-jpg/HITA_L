package com.stupidtree.hitax.ui.main.navigation

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.ColorStateList
import android.view.View
import com.bumptech.glide.Glide
import com.stupidtree.hitax.R
import com.stupidtree.hitax.data.repository.EASRepository
import com.stupidtree.hitax.databinding.FragmentNavigationBinding
import com.stupidtree.style.base.BaseFragment
import com.stupidtree.hitax.ui.eas.classroom.EmptyClassroomActivity
import com.stupidtree.hitax.ui.eas.exam.ExamActivity
import com.stupidtree.hitax.ui.eas.imp.ImportTimetableActivity
import com.stupidtree.hitax.ui.eas.login.PopUpLoginEAS
import com.stupidtree.hitax.ui.eas.score.ScoreInquiryActivity
import com.stupidtree.hitax.utils.ActivityUtils.CourseResourceMode
import com.stupidtree.hitax.ui.snatch.CourseSnatchActivity
import com.stupidtree.hitax.utils.ActivityUtils
import com.stupidtree.hitax.utils.ImageUtils
import com.stupidtree.stupiduser.data.repository.LocalUserRepository
import com.stupidtree.style.widgets.PopUpText

class NavigationFragment : BaseFragment<NavigationViewModel, FragmentNavigationBinding>() {
    override fun getViewModelClass(): Class<NavigationViewModel> {
        return NavigationViewModel::class.java
    }

    override fun initViews(view: View) {
        viewModel.recentTimetableLiveData.observe(this) {
            if (it == null) {
                binding?.recentSubtitle?.setText(R.string.none)
            } else {
                binding?.recentSubtitle?.text = it.name
            }
        }
        viewModel.timetableCountLiveData.observe(this) {
            if (it == 0) {
                binding?.timetableSubtitle?.setText(R.string.no_timetable)
            } else {
                binding?.timetableSubtitle?.text = getString(R.string.timetable_count_format, it)
            }

        }
        binding?.cardTimetable?.setOnClickListener {
            ActivityUtils.startTimetableManager(requireContext())
        }
        binding?.cardRecentTimetable?.setOnClickListener {
            viewModel.recentTimetableLiveData.value?.let {
                ActivityUtils.startTimetableDetailActivity(requireContext(), it.id)
            }
        }
        binding?.cardImport?.setOnClickListener {
            ActivityUtils.showEasVerifyWindow(
                requireContext(),
                directTo = ImportTimetableActivity::class.java,
                onResponseListener = object : PopUpLoginEAS.OnResponseListener {
                    override fun onSuccess(window: PopUpLoginEAS) {
                        ActivityUtils.startActivity(
                            requireContext(),
                            ImportTimetableActivity::class.java
                        )
                        window.dismiss()
                    }

                    override fun onFailed(window: PopUpLoginEAS) {

                    }

                })
        }
        binding?.cardEmptyClassroom?.setOnClickListener {
            ActivityUtils.showEasVerifyWindow(
                requireContext(),
                directTo = EmptyClassroomActivity::class.java,
                onResponseListener = object : PopUpLoginEAS.OnResponseListener {
                    override fun onSuccess(window: PopUpLoginEAS) {
                        ActivityUtils.startActivity(
                            requireContext(),
                            EmptyClassroomActivity::class.java
                        )
                        window.dismiss()
                    }

                    override fun onFailed(window: PopUpLoginEAS) {

                    }

                })
        }
        binding?.cardScores?.setOnClickListener {
            ActivityUtils.showEasVerifyWindow(
                requireContext(),
                directTo = ScoreInquiryActivity::class.java,
                onResponseListener = object : PopUpLoginEAS.OnResponseListener {
                    override fun onSuccess(window: PopUpLoginEAS) {
                        ActivityUtils.startActivity(
                            requireContext(),
                            ScoreInquiryActivity::class.java
                        )
                        window.dismiss()
                    }

                    override fun onFailed(window: PopUpLoginEAS) {

                    }
                }
            )
        }
        binding?.cardSubjects?.setOnClickListener {
            ActivityUtils.showEasVerifyWindow(
                requireContext(),
                directTo = ExamActivity::class.java,
                onResponseListener = object : PopUpLoginEAS.OnResponseListener {
                    override fun onSuccess(window: PopUpLoginEAS) {
                        ActivityUtils.startActivity(
                            requireContext(),
                            ExamActivity::class.java
                        )
                        window.dismiss()
                    }

                    override fun onFailed(window: PopUpLoginEAS) {

                    }
                }
            )
        }
        binding?.cardCourseLookup?.setOnClickListener {
            ActivityUtils.startCourseResourceSearchActivity(requireContext(), mode = CourseResourceMode.VIEW)
        }
        binding?.cardCourseSubmitPr?.setOnClickListener {
            ActivityUtils.startCourseResourceSearchActivity(requireContext(), mode = CourseResourceMode.SUBMIT)
        }
        binding?.cardCourseSnatch?.setOnClickListener {
            ActivityUtils.startActivity(requireContext(), CourseSnatchActivity::class.java)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onStart() {
        super.onStart()
        viewModel.startRefresh()

        LocalUserRepository.getInstance(requireContext()).getLoggedInUser().let {
            if (it.isValid()) { //如果已登录
                //装载头像
                binding?.avatar?.let { it1 ->
                    com.stupidtree.stupiduser.util.ImageUtils.loadAvatarInto(
                        requireContext(),
                        it.avatar,
                        it1
                    )
                }
                binding?.avatar?.let { it1 ->
                    com.stupidtree.stupiduser.util.ImageUtils.loadAvatarInto(
                        requireContext(),
                        it.avatar,
                        it1
                    )
                }
                //设置各种文字
                binding?.username?.text = it.username
                binding?.nickname?.text = it.nickname
                binding?.userCard?.setOnClickListener { v ->
                    ActivityUtils.startProfileActivity(
                        requireContext(),
                        it.id,
                        binding?.avatar
                    )
                }
            } else {
                //未登录的信息显示
                val easToken = activity?.application?.let { EASRepository.getInstance(it).getEasToken() }
                if (easToken?.isLogin() == true) {
                    binding?.username?.text = easToken.name?.ifBlank { easToken.username }
                        ?: easToken.username
                    binding?.nickname?.text = easToken.stuId?.ifBlank { easToken.username } ?: ""
                } else {
                    binding?.username?.setText(R.string.eas_account_not_logged_in_title)
                    binding?.nickname?.setText(R.string.eas_account_not_logged_in_subtitle)
                }
                binding?.avatar?.setImageResource(R.drawable.place_holder_avatar)
                binding?.userCard?.setOnClickListener {
                    ActivityUtils.showEasVerifyWindow<Activity>(
                        requireContext(),
                        directTo = null,
                        onResponseListener = object : PopUpLoginEAS.OnResponseListener {
                            override fun onSuccess(window: PopUpLoginEAS) {
                                window.dismiss()
                                refreshEasState()
                            }

                            override fun onFailed(window: PopUpLoginEAS) {}
                        }
                    )
                }
            }
        }
        refreshEasState()
    }

    private fun refreshEasState() {
        val token = activity?.application?.let { EASRepository.getInstance(it).getEasToken() }
        token?.let { token ->
            if (token.isLogin()) {
                binding?.easTitle?.text = "教务登录为 ${token.username}"
                binding?.easDot?.imageTintList = ColorStateList.valueOf(getColorPrimary())
                binding?.easAvatar?.let { it1 ->
                    ImageUtils.loadEASPictureInto(
                        token.picture ?: "",
                        it1
                    )
                }
                binding?.easActionIcon?.setOnClickListener {
                    PopUpText().setTitle(R.string.menu_logout_jw).setOnConfirmListener(object :PopUpText.OnConfirmListener{
                        override fun OnConfirm() {
                            activity?.application?.let {
                                EASRepository.getInstance(it).logout()
                                refreshEasState()
                            }
                        }

                    }).show(parentFragmentManager,"logout")

                }
                binding?.easActionIcon?.visibility = View.VISIBLE
                binding?.easLayout?.isClickable = false
            } else {
                binding?.easTitle?.text = "未登录教务"
                binding?.easDot?.imageTintList = ColorStateList.valueOf(getColorPrimaryDisabled())
                binding?.easActionIcon?.visibility = View.INVISIBLE
                binding?.easLayout?.isClickable = true
                binding?.easLayout?.setOnClickListener {
                    ActivityUtils.showEasVerifyWindow<Activity>(
                        requireContext(),
                        directTo = null,
                        onResponseListener = object : PopUpLoginEAS.OnResponseListener {
                            override fun onSuccess(window: PopUpLoginEAS) {
                                window.dismiss()
                                refreshEasState()
                            }
                            override fun onFailed(window: PopUpLoginEAS) {

                            }

                        })
                }
            }
        }
    }


    override fun initViewBinding(): FragmentNavigationBinding {
        return FragmentNavigationBinding.inflate(layoutInflater)
    }
}

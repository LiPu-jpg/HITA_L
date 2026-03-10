package com.stupidtree.hitax.ui.resource

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebViewClient
import com.stupidtree.hitax.databinding.ActivityInternalWebBinding
import com.stupidtree.style.base.BaseActivity

class InternalWebActivity : BaseActivity<InternalWebViewModel, ActivityInternalWebBinding>() {
    override fun getViewModelClass(): Class<InternalWebViewModel> = InternalWebViewModel::class.java

    override fun initViewBinding(): ActivityInternalWebBinding =
        ActivityInternalWebBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun initViews() {
        binding.toolbar.title = intent.getStringExtra("title") ?: ""
        binding.webview.webChromeClient = WebChromeClient()
        binding.webview.webViewClient = WebViewClient()
        binding.webview.settings.javaScriptEnabled = true
        binding.webview.settings.loadWithOverviewMode = true
        binding.webview.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        binding.webview.loadUrl(intent.getStringExtra("url") ?: "about:blank")
    }
}

class InternalWebViewModel : androidx.lifecycle.ViewModel()
package com.stupidtree.stupiduser.data.model

class CheckUpdateResult {
    var shouldUpdate: Boolean = false
    var latestVersionCode: Long = 0
    var latestVersionName: String = ""
    var latestUrl: String = ""
    var updateLog: String = ""
}

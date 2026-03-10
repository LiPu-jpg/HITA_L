package com.stupidtree.hitax.data.model.resource

data class CourseResourceItem(
    var repoName: String = "",
    var courseCode: String = "",
    var courseName: String = "",
    var repoType: String = "normal",
    var teachers: List<String> = emptyList(),
    var aliases: List<String> = emptyList(),
)

data class CourseSectionSummary(
    var label: String = "",
    var preview: String = "",
)

data class CourseSummary(
    var name: String = "",
    var code: String = "",
    var reviewTopics: List<String> = emptyList(),
    var teachers: List<String> = emptyList(),
    var sections: List<String> = emptyList(),
)

data class CourseStructureSummary(
    var repoType: String = "normal",
    var sections: List<CourseSectionSummary> = emptyList(),
    var courses: List<CourseSummary> = emptyList(),
    var appendTargets: List<String> = emptyList(),
    var teachers: List<String> = emptyList(),
)

data class CourseReadmeData(
    var source: String = "",
    var markdown: String = "",
)

data class ValidateReadmeResult(
    var ok: Boolean = false,
    var toml: String = "",
    var normalizedReadme: String = "",
)

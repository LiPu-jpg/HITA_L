# HITA 课表（维护分支）

[App 下载（Releases）](https://github.com/LiPu-jpg/HITA_L/releases/latest)

## 项目背景
项目最初来自哈尔滨工业大学（深圳）2018 级本科生大一年度立项，原名 HITSZ 助手，重构版改名为 HITA 课表。
该项目长期无人维护，本分支为接手维护的延续版本，同时附带了本研抓包逆向出来的api清单（由于更新时为非选课时间，部分选课api不能完全确认功能，同时部分api学校也没做完），便于后续更新。

## 应用简介
这是面向哈尔滨工业大学（深圳）学生的工具类 APP（非官方）。

### 当前主要功能
- 课表与日程：导入课表、按周查看、课程详情
- 教务服务：成绩查询、学分绩与排名、空教室查询
- 选课助手：定时发包抢课（按设定时间发 5 次）
- 课程资源：应用内搜索课程资料与 README、支持追加型投稿
- 教师搜索：优先使用课程资源数据，同时提供教师主页检索入口
- 考试：暂无官方接口，提供考试备忘录

## 数据与版权说明
- 课程与课表数据来自教务系统，本应用不额外采集或上传。
- 课程资料来源 HOA（校内民间开源组织），欢迎同学参与贡献。官网：hoa.moe
- 如有问题请联系：2720649216@qq.com

## 用到第三方开源库
- 加载效果按钮：[LoadingButtonAndroid](https://github.com/leandroBorgesFerreira/LoadingButtonAndroid)
- 显示多行的 CollapsingToolbarLayout：[multiline-collapsingtoolbar](https://github.com/opacapp/multiline-collapsingtoolbar)
- θ 社区上传图片压缩：[Luban](https://github.com/Curzibn/Luban)
- 今日页下拉交互：[PullLoadXiaochengxu](https://github.com/LucianZhang/PullLoadXiaochengxu)

## License

[MIT](LICENSE) © Stupid Tree

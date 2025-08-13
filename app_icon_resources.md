# 应用图标资源映射表

## 需要创建的图标资源文件

### 音乐类
- `ic_qqmusic.xml` - QQ音乐图标
- `ic_netease_music.xml` - 网易云音乐图标

### 生活服务类
- `ic_eleme.xml` - 饿了么图标
- `ic_douban.xml` - 豆瓣图标

### 地图导航类
- `ic_gaode_map.xml` - 高德地图图标
- `ic_baidu_map.xml` - 百度地图图标

### 浏览器类
- `ic_quark.xml` - 夸克浏览器图标
- `ic_uc_browser.xml` - UC浏览器图标

### 金融类
- `ic_alipay.xml` - 支付宝图标
- `ic_wechat_pay.xml` - 微信支付图标
- `ic_cmb.xml` - 招商银行图标
- `ic_antfortune.xml` - 蚂蚁财富图标

### 出行类
- `ic_didi.xml` - 滴滴出行图标
- `ic_railway12306.xml` - 12306图标
- `ic_ctrip.xml` - 携程旅行图标
- `ic_qunar.xml` - 去哪儿图标
- `ic_hellobike.xml` - 哈啰出行图标

### 招聘类
- `ic_boss.xml` - BOSS直聘图标
- `ic_liepin.xml` - 猎聘图标
- `ic_zhaopin.xml` - 前程无忧图标

### 教育类
- `ic_youdao_dict.xml` - 有道词典图标
- `ic_baicizhan.xml` - 百词斩图标
- `ic_zuoyebang.xml` - 作业帮图标
- `ic_yuansouti.xml` - 小猿搜题图标

### 新闻类
- `ic_netease_news.xml` - 网易新闻图标

## 图标设计规范

1. **尺寸**: 24dp x 24dp
2. **格式**: Vector Drawable (XML)
3. **颜色**: 使用品牌主色调
4. **风格**: 简洁、现代、易识别
5. **兼容性**: 支持不同主题模式

## 实现方案

### 方案1: 使用Vector Drawable
创建基于品牌色彩的简化图标，使用XML vector drawable格式。

### 方案2: 使用字母图标
为每个应用创建带有品牌色彩的字母图标。

### 方案3: 混合方案
- 优先使用已安装应用的真实图标
- 未安装时使用自定义vector drawable
- 最后使用字母图标作为fallback

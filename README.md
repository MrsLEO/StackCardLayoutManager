# StackCardLayoutManager
Android 自定义RecyclerView.LayoutManager，卡片式层叠效果

效果见下图:

![](https://github.com/biansemao/StackCardLayoutManager/blob/master/GIF.gif)

## 3. Use:
1.直接设置layoutManager为StackCardLayoutManager即可

2.可使用StackCardLayoutManager.StackConfig对一些相关属性做配置。
# Attributes
| attr 属性 | description 描述 |
|-----------|-----------------|
| space | 间距 |
| stackCount | 可见数 |
| stackPosition | 初始可见的位置 |
| stackScale | 缩放比例 |
| parallex | 视差因子 |
| isCycle | 是否能无限循环，若列表数为1不允许无限循环 |
| isAutoCycle | 若能无限循环，是否自动开始循环 |
| autoCycleTime | 自动循环时间间隔，毫秒 |

注：StackCardLayoutManager未对RecyclerView及其item的padding及margin做考虑


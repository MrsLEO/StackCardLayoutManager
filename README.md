# StackCardLayoutManager
Android 自定义RecyclerView.LayoutManager，卡片式层叠效果

效果见下图:

![](https://github.com/biansemao/StackCardLayoutManager/blob/master/GIF.gif)

## 3. Use:
1.直接设置layoutManager为StackCardLayoutManager即可

2.可使用StackCardLayoutManager.StackConfig对一些相关属性做配置。
# Attributes
| attr 属性 | description 描述 | value 属性值 |
|-----------|-----------------|-------------|
| space | 间距 | int，默认60 |
| stackCount | 可见数 | int，默认3 |
| stackPosition | 初始可见的位置 | int，默认0 |
| stackScale | 缩放比例 | float，[0.0,1.0]，默认0.9 |
| parallex | 视差因子 | float，[1.0,2.0]，默认1f |
| isCycle | 是否能无限循环，若列表数为1不允许无限循环 | boolean，默认false |
| isAutoCycle | 若能无限循环，是否自动开始循环 | boolean，默认false |
| autoCycleTime | 自动循环时间间隔，毫秒 | int，默认3000 |
| isAdjustSize | 是否重新校准调整RecyclerView宽高 | boolean，默认false |
| direction | 摆放方向：LEFT,TOP,RIGHT,BOTTOM | StackDirection，默认StackDirection.RIGHT |
# Method
## 1.setOnPositionChangeListener
设置位置改变监听

注：
1.若出现列表嵌套列表的情况，建议将isAdjustSize设置为true
2.StackCardLayoutManager未对RecyclerView及其item的padding及margin做过多考虑，使用时请自定注意


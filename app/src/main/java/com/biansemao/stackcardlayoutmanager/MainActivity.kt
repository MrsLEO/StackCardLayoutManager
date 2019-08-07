package com.biansemao.stackcardlayoutmanager

import android.content.Context
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val firstListRv = findViewById<RecyclerView>(R.id.rv_list_first)
        val secondListRv = findViewById<RecyclerView>(R.id.rv_list_second)

        val tempList = ArrayList<Int>()
        tempList.add(R.drawable.ic_card_1)
        tempList.add(R.drawable.ic_card_2)
        tempList.add(R.drawable.ic_card_3)
        tempList.add(R.drawable.ic_card_4)
        tempList.add(R.drawable.ic_card_5)
        tempList.add(R.drawable.ic_card_6)

        val horizontalConfig = StackCardLayoutManager.StackConfig()
        horizontalConfig.stackScale = 0.9f
        horizontalConfig.stackCount = 3
        horizontalConfig.stackPosition = 0
        horizontalConfig.space = dip2px(this, 24f)
        horizontalConfig.parallex = 1.5f
        horizontalConfig.isCycle = false
        horizontalConfig.direction = StackCardLayoutManager.StackDirection.BOTTOM
        firstListRv.layoutManager = StackCardLayoutManager(horizontalConfig)
        firstListRv.adapter = TestAdapter(this, tempList)

        val verticalConfig = StackCardLayoutManager.StackConfig()
        verticalConfig.stackScale = 0.9f
        verticalConfig.stackCount = 3
        verticalConfig.stackPosition = 0
        verticalConfig.space = dip2px(this, 24f)
        verticalConfig.parallex = 1.5f
        verticalConfig.isCycle = true
        verticalConfig.isAutoCycle = true
        verticalConfig.autoCycleTime = 3500
        verticalConfig.direction = StackCardLayoutManager.StackDirection.RIGHT
        secondListRv.layoutManager = StackCardLayoutManager(verticalConfig)
        secondListRv.adapter = TestAdapter(this, tempList)

    }

    /**
     * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
     */
    private fun dip2px(context: Context, dpValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    private class TestAdapter(val context: Context, val data: List<Int>) : RecyclerView.Adapter<TestAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.item_test, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int {
            return data.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.imageIv.setImageResource(data[position])
        }

        private class ViewHolder : RecyclerView.ViewHolder {

            var imageIv: ImageView

            constructor(itemView: View) : super(itemView) {
                imageIv = itemView.findViewById(R.id.iv_image)
            }
        }
    }


}
